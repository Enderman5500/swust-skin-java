package cn.edu.swust.mc.skin.plugins.mcwiki;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 版本导入流水线：下载客户端/服务端 JAR，解析中英文名称、配方、实体与方块状态。
 * 渲染图由共享渲染资产库提供，导入不再下载每版本贴图。
 */
final class WikiImporter {
    private static final int PHASES = 6;

    private final WikiStore store;
    private final MojangClient mojang;
    private final WikiCatalog catalog;
    private final ClientJarExtractor extractor;
    private final AssetIndexImporter assets;

    WikiImporter(WikiStore store, MojangClient mojang, WikiCatalog catalog) {
        this.store = store;
        this.mojang = mojang;
        this.catalog = catalog;
        this.extractor = new ClientJarExtractor(WikiStore.MAPPER);
        this.assets = new AssetIndexImporter(WikiStore.MAPPER, mojang);
    }

    void run(String version, JobService.Control control) {
        Map<String, Object> info = null;
        for (Map<String, Object> row : store.versions()) {
            if (version.equals(String.valueOf(row.get("id")))) {
                info = row;
                break;
            }
        }
        if (info == null) throw new IllegalStateException("Minecraft 版本不存在，请先刷新版本清单：" + version);

        control.progress("VERSION_DETAILS", 0, PHASES, "正在读取版本详情");
        String detailsUrl = String.valueOf(info.get("url"));
        JsonNode details = mojang.versionDetails(detailsUrl);
        String clientUrl = details.path("downloads").path("client").path("url").asText(null);
        if (clientUrl == null || clientUrl.isBlank()) throw new IllegalStateException("版本详情没有客户端 JAR 下载地址");
        String serverUrl = details.path("downloads").path("server").path("url").asText(null);

        Path clientJar = null;
        Path serverJar = null;
        try {
            control.progress("CLIENT_JAR", 1, PHASES, "正在下载客户端 JAR");
            clientJar = Files.createTempFile(store.tmpDir(), "mc-wiki-", ".jar");
            byte[] client = mojang.download(clientUrl);
            Files.write(clientJar, client);
            control.log("INFO", "客户端 JAR " + (client.length / 1024 / 1024) + " MB");
            control.checkpoint();

            control.progress("SERVER_JAR", 2, PHASES, "正在下载服务端 JAR（配方与标签数据）");
            if (serverUrl == null || serverUrl.isBlank()) {
                control.log("WARN", "版本详情没有服务端 JAR 下载地址，将只解析客户端 JAR（适用于 1.12.2 及更早版本）");
            } else {
                serverJar = Files.createTempFile(store.tmpDir(), "mc-wiki-server-", ".jar");
                byte[] server = mojang.download(serverUrl);
                Files.write(serverJar, server);
                control.log("INFO", "服务端 JAR " + (server.length / 1024 / 1024) + " MB");
            }
            control.checkpoint();

            control.progress("ASSET_INDEX", 3, PHASES, "正在下载中文语言文件");
            String assetIndexUrl = details.path("assetIndex").path("url").asText(null);
            byte[] zhLang = assetIndexUrl == null || assetIndexUrl.isBlank()
                    ? null : assets.importZhLang(assetIndexUrl, control);
            control.checkpoint();

            control.progress("PARSE_JAR", 4, PHASES, "正在解析语言、配方、标签和实体");
            ClientJarExtractor.Extraction data = extractor.extract(clientJar, serverJar, zhLang);
            control.checkpoint();

            control.progress("SAVE_RECORDS", 5, PHASES, "正在写入物品、生物与配方清单");
            Map<String, String> kinds = new LinkedHashMap<>();
            data.names().forEach((key, name) -> {
                String id = LangMerger.namespaced(key);
                if (id == null) return;
                String kind = key.startsWith("block.minecraft.") ? "block"
                        : key.startsWith("entity.minecraft.") ? "entity" : "item";
                kinds.put(id, kind);
            });
            for (String entity : data.entityIds()) kinds.putIfAbsent("minecraft:" + entity, "entity");
            for (String path : data.blockstates().keySet()) kinds.putIfAbsent("minecraft:" + path, "block");
            // 1.12.2 的语言键（sword_diamond）与配方注册名（diamond_sword）不一致，
            // 配方里出现但语言表没有的注册名也要建条目，否则配方无法关联到物品。
            for (ClientJarExtractor.RecipeRecord recipe : data.recipes()) {
                addRegistryId(kinds, data.blockstates(), recipe.resultId());
                for (String ingredient : recipe.ingredients()) {
                    if (ingredient != null && !ingredient.startsWith("#") && !ingredient.startsWith("ore:")) {
                        addRegistryId(kinds, data.blockstates(), ingredient);
                    }
                }
            }

            List<Map<String, Object>> items = new ArrayList<>();
            Map<String, LangMerger.Name> sortedNames = sortedNames(data.names());
            for (Map.Entry<String, String> entry : kinds.entrySet()) {
                String id = entry.getKey();
                String kind = entry.getValue();
                LangMerger.Name name = data.names().get(LangMerger.langKey(id, kind));
                if (name == null) {
                    // 旧版本语言键与注册名顺序不同（sword_diamond / diamond_sword），按词根重排兜底匹配
                    String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                    name = sortedNames.get(sortPath(path));
                }
                // 没有英文名的条目通常是从方块状态/配方补出的占位 id，避免混入物品目录
                if (name == null || name.en() == null || name.en().isBlank()) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("namespacedId", id);
                item.put("kind", kind);
                item.put("nameEn", name == null || name.en() == null ? "" : name.en());
                item.put("nameZh", name == null || name.zh() == null ? "" : name.zh());
                items.add(item);
            }

            List<Map<String, Object>> recipes = new ArrayList<>();
            for (ClientJarExtractor.RecipeRecord recipe : data.recipes()) {
                if (recipe.resultId() == null) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("recipeId", recipe.id());
                row.put("type", recipe.type());
                row.put("resultId", recipe.resultId());
                row.put("resultCount", recipe.resultCount());
                row.put("ingredients", recipe.ingredients());
                row.put("grid", recipe.grid());
                row.put("gridTags", recipe.gridTags());
                row.put("rawJson", recipe.rawJson());
                recipes.add(row);
            }

            store.writeItems(version, items);
            store.writeRecipes(version, recipes);
            store.writeBlockstates(version, data.blockstates());
            store.writeTags(version, data.tagMembers());
            store.writeImport(version, items.size(), recipes.size());
            catalog.invalidate(version);

            if (recipes.isEmpty()) {
                control.log("WARN", "没有解析到任何配方：1.13 起配方只存在于服务端 JAR，请确认该版本提供 server 下载");
            }
            for (String diagnostic : data.diagnostics()) control.log("WARN", diagnostic);
            control.progress("DONE", PHASES, PHASES,
                    "已导入 " + items.size() + " 个条目、" + recipes.size() + " 个配方");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("导入临时文件处理失败", ex);
        } finally {
            deleteQuietly(clientJar);
            deleteQuietly(serverJar);
        }
    }

    private static Map<String, LangMerger.Name> sortedNames(Map<String, LangMerger.Name> names) {
        Map<String, LangMerger.Name> result = new java.util.HashMap<>();
        for (Map.Entry<String, LangMerger.Name> entry : names.entrySet()) {
            String id = LangMerger.namespaced(entry.getKey());
            if (id == null) continue;
            String path = id.substring(id.indexOf(':') + 1);
            result.putIfAbsent(sortPath(path), entry.getValue());
        }
        return result;
    }

    private static String sortPath(String path) {
        String[] parts = path.split("_");
        java.util.Arrays.sort(parts);
        return String.join("_", parts);
    }

    private static void addRegistryId(Map<String, String> kinds, Map<String, String> blockstates, String id) {
        if (id == null || !id.contains(":") || kinds.containsKey(id)) return;
        String path = id.substring(id.indexOf(':') + 1);
        kinds.put(id, blockstates.containsKey(path) ? "block" : "item");
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}

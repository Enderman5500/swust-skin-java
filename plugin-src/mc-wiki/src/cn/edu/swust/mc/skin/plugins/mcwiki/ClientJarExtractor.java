package cn.edu.swust.mc.skin.plugins.mcwiki;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 解析 Minecraft 客户端/服务端 JAR，兼容三代目录结构：
 * 1.12.2 及更早的 assets/minecraft/recipes 与 .lang 文本；
 * 1.13~1.20.5 的 data/minecraft/recipes 与 JSON 语言文件；
 * 1.21 起的单数目录 data/minecraft/recipe、tags/item、loot_table。
 * 1.13 起配方/标签只在服务端 JAR，所以支持双 JAR 提取。
 */
final class ClientJarExtractor {
    record Extraction(Map<String, LangMerger.Name> names, List<RecipeRecord> recipes, Set<String> tags,
                      Set<String> entityIds, Map<String, String> blockstates, List<String> diagnostics,
                      Map<String, List<String>> tagMembers) {
    }

    record RecipeRecord(String id, String type, String resultId, int resultCount,
                        List<String> ingredients, List<String> grid, List<String> gridTags, String rawJson) {
    }

    private record CellValue(String id, String tag) {
    }

    private record GridResult(List<String> ids, List<String> tags) {
    }

    private static final String ITEM_TAG_PREFIX = "data/minecraft/tags/items/";
    private static final String ITEM_TAG_PREFIX_121 = "data/minecraft/tags/item/";
    private static final String BLOCK_TAG_PREFIX = "data/minecraft/tags/blocks/";
    private static final String BLOCK_TAG_PREFIX_121 = "data/minecraft/tags/block/";

    private final ObjectMapper mapper;
    private final LangMerger langs;

    ClientJarExtractor(ObjectMapper mapper) {
        this.mapper = mapper;
        this.langs = new LangMerger(mapper);
    }

    Extraction extract(Path clientJar, Path serverJar, byte[] externalZh) {
        Extraction client = scan(clientJar, externalZh, "客户端 JAR");
        if (serverJar == null) return client;
        Extraction server = scan(serverJar, null, "服务端 JAR");
        Map<String, RecipeRecord> recipes = new LinkedHashMap<>();
        for (RecipeRecord recipe : client.recipes()) recipes.put(recipe.id(), recipe);
        for (RecipeRecord recipe : server.recipes()) recipes.put(recipe.id(), recipe);
        Set<String> tags = new LinkedHashSet<>(client.tags());
        tags.addAll(server.tags());
        Set<String> entities = new LinkedHashSet<>(client.entityIds());
        entities.addAll(server.entityIds());
        List<String> diagnostics = new ArrayList<>(client.diagnostics());
        diagnostics.addAll(server.diagnostics());
        Map<String, List<String>> tagMembers = new LinkedHashMap<>(client.tagMembers());
        tagMembers.putAll(server.tagMembers());
        return new Extraction(client.names(), List.copyOf(recipes.values()), Set.copyOf(tags),
                Set.copyOf(entities), client.blockstates(), List.copyOf(diagnostics), Map.copyOf(tagMembers));
    }

    private Extraction scan(Path jar, byte[] externalZh, String label) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            byte[] en = first(zip, "assets/minecraft/lang/en_us.json", "assets/minecraft/lang/en_us.lang");
            byte[] zh = externalZh == null
                    ? first(zip, "assets/minecraft/lang/zh_cn.json", "assets/minecraft/lang/zh_cn.lang")
                    : externalZh;
            Map<String, LangMerger.Name> names = langs.merge(en, zh);
            List<RecipeRecord> recipes = new ArrayList<>();
            Set<String> tags = new LinkedHashSet<>();
            Set<String> entities = new LinkedHashSet<>();
            List<String> diagnostics = new ArrayList<>();
            Map<String, String> blockstates = new LinkedHashMap<>();

            Map<String, List<String>> tagValues = new LinkedHashMap<>();
            zip.stream().filter(e -> !e.isDirectory()).forEach(entry -> {
                String path = entry.getName();
                if (!path.endsWith(".json")) return;
                String prefix = path.startsWith(ITEM_TAG_PREFIX) ? ITEM_TAG_PREFIX
                        : path.startsWith(ITEM_TAG_PREFIX_121) ? ITEM_TAG_PREFIX_121
                        : path.startsWith(BLOCK_TAG_PREFIX) ? BLOCK_TAG_PREFIX
                        : path.startsWith(BLOCK_TAG_PREFIX_121) ? BLOCK_TAG_PREFIX_121 : null;
                if (prefix == null) return;
                try {
                    tagValues.put("minecraft:" + path.substring(prefix.length(), path.length() - 5),
                            tagValues(read(zip, path)));
                } catch (Exception ex) {
                    diagnostics.add(path + ": " + ex.getMessage());
                }
            });
            Map<String, List<String>> tagMembers = new LinkedHashMap<>();
            for (String tag : tagValues.keySet()) {
                tagMembers.put(tag, resolveTag(tag, tagValues, new LinkedHashSet<>()));
            }
            Map<String, List<String>> resolvedTags = Map.copyOf(tagMembers);

            zip.stream().filter(e -> !e.isDirectory()).forEach(entry -> {
                String path = entry.getName();
                try {
                    if (path.endsWith(".json") && (path.startsWith("data/minecraft/recipes/")
                            || path.startsWith("data/minecraft/recipe/")
                            || path.startsWith("assets/minecraft/recipes/"))) {
                        recipes.add(recipe(path, read(zip, path), resolvedTags));
                    } else if (path.startsWith(ITEM_TAG_PREFIX) || path.startsWith(ITEM_TAG_PREFIX_121)
                            || path.startsWith(BLOCK_TAG_PREFIX) || path.startsWith(BLOCK_TAG_PREFIX_121)) {
                        tags.add(canonicalTagId(path));
                    } else if (path.endsWith(".json") && (path.startsWith("data/minecraft/loot_tables/entities/")
                            || path.startsWith("data/minecraft/loot_table/entities/")
                            || path.startsWith("assets/minecraft/loot_tables/entities/"))) {
                        entities.add(path.substring(path.lastIndexOf('/') + 1, path.length() - 5));
                    } else if (path.endsWith(".json") && path.startsWith("assets/minecraft/blockstates/")) {
                        blockstates.put(path.substring("assets/minecraft/blockstates/".length(), path.length() - 5),
                                new String(read(zip, path), StandardCharsets.UTF_8));
                    }
                } catch (Exception ex) {
                    diagnostics.add(path + ": " + ex.getMessage());
                }
            });
            return new Extraction(names, List.copyOf(recipes), Set.copyOf(tags), Set.copyOf(entities),
                    Map.copyOf(blockstates), List.copyOf(diagnostics), Map.copyOf(resolvedTags));
        } catch (IOException ex) {
            throw new IllegalArgumentException(label + " 读取失败", ex);
        }
    }

    private RecipeRecord recipe(String path, byte[] bytes, Map<String, List<String>> tagMembers) throws IOException {
        JsonNode node = mapper.readTree(bytes);
        String result;
        int count = 1;
        JsonNode out = node.path("result");
        if (out.isTextual()) {
            result = out.asText();
        } else {
            result = out.path("id").isTextual() ? out.path("id").asText() : out.path("item").asText(null);
            count = Math.max(1, out.path("count").asInt(1));
        }
        List<String> ingredients = new ArrayList<>();
        node.path("ingredients").forEach(v -> ingredients.add(ingredient(v)));
        if (node.has("ingredient")) ingredients.add(ingredient(node.get("ingredient")));
        for (String field : List.of("input", "material", "template", "base", "addition")) {
            if (node.has(field)) ingredients.add(ingredient(node.get(field)));
        }
        node.path("key").fields().forEachRemaining(e -> ingredients.add(ingredient(e.getValue())));
        GridResult grid = grid(node, tagMembers);
        return new RecipeRecord(path.substring(path.lastIndexOf('/') + 1, path.length() - 5), type(node),
                normalize(result), count, ingredients.stream().filter(java.util.Objects::nonNull).distinct().toList(),
                grid.ids(), grid.tags(), new String(bytes, StandardCharsets.UTF_8));
    }

    /** 3x3 九宫格（行优先、靠左上、null 为空格），并记录每格来自哪个标签。 */
    private GridResult grid(JsonNode node, Map<String, List<String>> tagMembers) {
        CellValue[] cells = new CellValue[9];
        JsonNode pattern = node.path("pattern");
        if (pattern.isArray() && !pattern.isEmpty()) {
            JsonNode key = node.path("key");
            int row = 0;
            for (JsonNode rowNode : pattern) {
                if (row >= 3) break;
                String text = rowNode.asText("");
                for (int col = 0; col < Math.min(text.length(), 3); col++) {
                    char symbol = text.charAt(col);
                    if (symbol == ' ') continue;
                    cells[row * 3 + col] = gridIngredient(key.get(String.valueOf(symbol)), tagMembers);
                }
                row++;
            }
        } else {
            List<JsonNode> flat = new ArrayList<>();
            List<String> specialFields = List.of("template", "base", "addition", "input", "material");
            boolean special = specialFields.stream().anyMatch(node::has);
            if (special) {
                for (String field : specialFields) {
                    if (node.has(field)) flat.add(node.get(field));
                }
            } else {
                node.path("ingredients").forEach(flat::add);
                if (node.has("ingredient")) flat.add(node.get("ingredient"));
            }
            int slot = 0;
            for (JsonNode value : flat) {
                if (slot >= 9) break;
                cells[slot++] = gridIngredient(value, tagMembers);
            }
        }
        List<String> ids = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        for (CellValue cell : cells) {
            ids.add(cell == null ? null : cell.id());
            tags.add(cell == null ? null : cell.tag());
        }
        return new GridResult(ids, tags);
    }

    /** 配方落格原料解析为具体物品 id；标签同时保留标签 id，便于前端展示全部成员。 */
    private CellValue gridIngredient(JsonNode node, Map<String, List<String>> tagMembers) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) {
            String value = node.asText();
            if (value.startsWith("#")) {
                String tag = normalize(value.substring(1));
                List<String> members = tagMembers.get(tag);
                return new CellValue(members == null || members.isEmpty() ? null : members.getFirst(), tag);
            }
            return new CellValue(normalize(value), null);
        }
        if (node.has("item")) return new CellValue(normalize(node.path("item").asText()), null);
        if (node.has("tag")) {
            String tag = normalize(node.path("tag").asText());
            List<String> members = tagMembers.get(tag);
            return new CellValue(members == null || members.isEmpty() ? null : members.getFirst(), tag);
        }
        if (node.isArray()) {
            for (JsonNode option : node) {
                CellValue resolved = gridIngredient(option, tagMembers);
                if (resolved != null) return resolved;
            }
        }
        return null;
    }

    private List<String> tagValues(byte[] bytes) throws IOException {
        List<String> values = new ArrayList<>();
        for (JsonNode value : mapper.readTree(bytes).path("values")) {
            if (value.isTextual()) values.add(value.asText());
        }
        return values;
    }

    private String canonicalTagId(String path) {
        String relative = path.substring(path.indexOf("tags/") + 5, path.length() - 5);
        if (relative.startsWith("item/")) return "items/" + relative.substring("item/".length());
        if (relative.startsWith("block/")) return "blocks/" + relative.substring("block/".length());
        return relative;
    }

    private List<String> resolveTag(String tag, Map<String, List<String>> raw, Set<String> visiting) {
        if (!visiting.add(tag)) return List.of();
        List<String> members = new ArrayList<>();
        for (String value : raw.getOrDefault(tag, List.of())) {
            if (value.startsWith("#")) members.addAll(resolveTag(normalize(value.substring(1)), raw, visiting));
            else members.add(normalize(value));
        }
        visiting.remove(tag);
        return members;
    }

    private String type(JsonNode node) {
        String type = node.path("type").asText(null);
        if (type == null || type.isBlank()) type = node.has("pattern") ? "crafting_shaped" : "crafting_shapeless";
        return type.contains(":") ? type : "minecraft:" + type;
    }

    private String ingredient(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return normalize(node.asText());
        if (node.has("item")) return normalize(node.path("item").asText());
        if (node.has("tag")) return "#" + normalize(node.path("tag").asText());
        if (node.has("ore")) return "ore:" + node.path("ore").asText();
        if (node.isArray() && !node.isEmpty()) return ingredient(node.get(0));
        return null;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.contains(":") ? value : "minecraft:" + value;
    }

    private byte[] first(ZipFile zip, String... names) throws IOException {
        for (String name : names) {
            byte[] data = read(zip, name);
            if (data != null) return data;
        }
        return null;
    }

    private byte[] read(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }
}

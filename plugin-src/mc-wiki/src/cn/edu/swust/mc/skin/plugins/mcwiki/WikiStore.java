package cn.edu.swust.mc.skin.plugins.mcwiki;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 插件自己的文件存储。宿主只提供键值 plugin_data，百科数据量按版本计算较大，
 * 因此按版本分文件存放 JSON，渲染图写入插件目录以便宿主静态托管。
 */
final class WikiStore {
    static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path dataDir;
    private final Path assetsDir;
    private final Path tmpDir;

    WikiStore(Path dataDir, Path assetsDir) {
        this.dataDir = dataDir;
        this.assetsDir = assetsDir;
        try {
            Files.createDirectories(dataDir.resolve("items"));
            Files.createDirectories(dataDir.resolve("recipes"));
            Files.createDirectories(dataDir.resolve("blockstates"));
            Files.createDirectories(dataDir.resolve("tags"));
            Files.createDirectories(dataDir.resolve("events"));
            this.tmpDir = dataDir.resolve("tmp");
            Files.createDirectories(tmpDir);
            if (assetsDir != null) Files.createDirectories(assetsDir.resolve("renders"));
        } catch (IOException e) {
            throw new IllegalStateException("无法创建 MC 百科数据目录", e);
        }
    }

    Path dataDir() {
        return dataDir;
    }

    Path assetsDir() {
        return assetsDir;
    }

    Path tmpDir() {
        return tmpDir;
    }

    Path rendersDir() {
        return assetsDir == null ? null : assetsDir.resolve("renders");
    }

    synchronized List<Map<String, Object>> versions() {
        return readList(dataDir.resolve("versions.json"));
    }

    synchronized void writeVersions(List<Map<String, Object>> versions) {
        writeJson(dataDir.resolve("versions.json"), versions);
    }

    synchronized Map<String, Map<String, Object>> imports() {
        return read(dataDir.resolve("imports.json"), new TypeReference<Map<String, Map<String, Object>>>() {
        }, new LinkedHashMap<>());
    }

    synchronized Map<String, Object> importRecord(String version) {
        return imports().get(version);
    }

    synchronized void writeImport(String version, int items, int recipes) {
        Map<String, Map<String, Object>> all = imports();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("version", version);
        row.put("items", items);
        row.put("recipes", recipes);
        row.put("importedAt", System.currentTimeMillis());
        all.put(version, row);
        writeJson(dataDir.resolve("imports.json"), all);
    }

    synchronized void deleteImport(String version) {
        Map<String, Map<String, Object>> all = imports();
        if (all.remove(version) != null) writeJson(dataDir.resolve("imports.json"), all);
    }

    synchronized List<Map<String, Object>> items(String version) {
        return readList(dataDir.resolve("items").resolve(name(version) + ".json"));
    }

    synchronized void writeItems(String version, List<Map<String, Object>> items) {
        writeJson(dataDir.resolve("items").resolve(name(version) + ".json"), items);
    }

    synchronized List<Map<String, Object>> recipes(String version) {
        return readList(dataDir.resolve("recipes").resolve(name(version) + ".json"));
    }

    synchronized void writeRecipes(String version, List<Map<String, Object>> recipes) {
        writeJson(dataDir.resolve("recipes").resolve(name(version) + ".json"), recipes);
    }

    synchronized Map<String, List<String>> tags(String version) {
        return read(dataDir.resolve("tags").resolve(name(version) + ".json"),
                new TypeReference<Map<String, List<String>>>() {
                }, new LinkedHashMap<>());
    }

    synchronized void writeTags(String version, Map<String, List<String>> tags) {
        writeJson(dataDir.resolve("tags").resolve(name(version) + ".json"), tags);
    }

    synchronized Map<String, String> blockstates(String version) {
        return read(dataDir.resolve("blockstates").resolve(name(version) + ".json"),
                new TypeReference<Map<String, String>>() {
                }, new LinkedHashMap<>());
    }

    synchronized void writeBlockstates(String version, Map<String, String> blockstates) {
        writeJson(dataDir.resolve("blockstates").resolve(name(version) + ".json"), blockstates);
    }

    synchronized void deleteVersionData(String version) {
        deleteIfExists(dataDir.resolve("items").resolve(name(version) + ".json"));
        deleteIfExists(dataDir.resolve("recipes").resolve(name(version) + ".json"));
        deleteIfExists(dataDir.resolve("blockstates").resolve(name(version) + ".json"));
        deleteIfExists(dataDir.resolve("tags").resolve(name(version) + ".json"));
        deleteImport(version);
    }

    /** 清理历史导入中无英文名的占位物品，并同步导入计数；返回移除数量。 */
    synchronized int cleanupUntranslatedItems() {
        Map<String, Map<String, Object>> imports = imports();
        int removedTotal = 0;
        for (String version : new ArrayList<>(imports.keySet())) {
            List<Map<String, Object>> rows = items(version);
            int before = rows.size();
            rows.removeIf(row -> String.valueOf(row.getOrDefault("nameEn", "")).isBlank());
            int removed = before - rows.size();
            if (removed == 0) continue;
            writeItems(version, rows);
            Map<String, Object> record = imports.get(version);
            record.put("items", rows.size());
            removedTotal += removed;
        }
        if (removedTotal > 0) writeJson(dataDir.resolve("imports.json"), imports);
        return removedTotal;
    }

    /** 对外隐藏的物品/方块/生物 id（渲染资产缺图时由管理员维护）。 */
    synchronized Set<String> hiddenItems() {
        List<String> ids = read(dataDir.resolve("hidden-items.json"), new TypeReference<List<String>>() {
        }, new ArrayList<>());
        return new LinkedHashSet<>(ids);
    }

    synchronized void writeHiddenItems(Set<String> ids) {
        writeJson(dataDir.resolve("hidden-items.json"), new ArrayList<>(ids));
    }

    synchronized Map<String, Object> publication() {
        return read(dataDir.resolve("publication.json"), new TypeReference<Map<String, Object>>() {
        }, new LinkedHashMap<>());
    }

    synchronized void writePublication(Map<String, Object> publication) {
        writeJson(dataDir.resolve("publication.json"), publication);
    }

    synchronized List<Map<String, Object>> jobs() {
        return readList(dataDir.resolve("jobs.json"));
    }

    synchronized void writeJobs(List<Map<String, Object>> jobs) {
        writeJson(dataDir.resolve("jobs.json"), jobs);
    }

    synchronized void appendEvent(String jobId, Map<String, Object> event) {
        Path file = dataDir.resolve("events").resolve(name(jobId) + ".jsonl");
        try {
            Files.writeString(file, MAPPER.writeValueAsString(event) + "\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 事件日志只是辅助信息，写失败不应该打断任务
        }
    }

    synchronized List<Map<String, Object>> events(String jobId, long afterSeq) {
        Path file = dataDir.resolve("events").resolve(name(jobId) + ".jsonl");
        List<Map<String, Object>> result = new ArrayList<>();
        if (!Files.isRegularFile(file)) return result;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                try {
                    Map<String, Object> event = MAPPER.readValue(line, new TypeReference<Map<String, Object>>() {
                    });
                    Object seq = event.get("seq");
                    if (seq instanceof Number number && number.longValue() > afterSeq) result.add(event);
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return result;
    }

    synchronized void deleteEvents(String jobId) {
        deleteIfExists(dataDir.resolve("events").resolve(name(jobId) + ".jsonl"));
    }

    synchronized Map<String, Object> rendersMeta() {
        return read(dataDir.resolve("renders-meta.json"), new TypeReference<Map<String, Object>>() {
        }, new LinkedHashMap<>());
    }

    synchronized void writeRendersMeta(Map<String, Object> meta) {
        writeJson(dataDir.resolve("renders-meta.json"), meta);
    }

    void writeAsset(String relative, byte[] bytes) throws IOException {
        Path root = assetsDir;
        if (root == null) throw new IOException("插件目录不可写");
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root.normalize())) throw new IOException("资源路径越界");
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }

    void deleteAsset(String relative) {
        Path root = assetsDir;
        if (root == null) return;
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root.normalize())) return;
        deleteIfExists(target);
    }

    private List<Map<String, Object>> readList(Path file) {
        return read(file, new TypeReference<List<Map<String, Object>>>() {
        }, new ArrayList<>());
    }

    private <T> T read(Path file, TypeReference<T> type, T fallback) {
        if (!Files.isRegularFile(file)) return fallback;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.isBlank()) return fallback;
            return MAPPER.readValue(text, type);
        } catch (IOException e) {
            return fallback;
        }
    }

    private void writeJson(Path target, Object value) {
        try {
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temp, MAPPER.writeValueAsString(value), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("MC 百科数据写入失败：" + target.getFileName(), e);
        }
    }

    private static void deleteIfExists(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    /** 版本号 / 任务 id 只含字母数字和 ._-，其余替换，避免路径问题。 */
    static String name(String value) {
        return String.valueOf(value).replaceAll("[^A-Za-z0-9._-]", "_");
    }
}

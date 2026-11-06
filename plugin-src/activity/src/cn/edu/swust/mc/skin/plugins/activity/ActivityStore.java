package cn.edu.swust.mc.skin.plugins.activity;

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
import java.util.List;
import java.util.Map;

/** 活动插件的数据文件存储：活动、报名、身份、设置、导出记录与证明文件。 */
final class ActivityStore {
    static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path dataDir;
    private final Path exportsDir;
    private final Path stampedDir;
    private final Path templateFile;

    ActivityStore(Path dataDir) {
        this.dataDir = dataDir;
        this.exportsDir = dataDir.resolve("exports");
        this.stampedDir = dataDir.resolve("stamped");
        this.templateFile = dataDir.resolve("template.docx");
        try {
            Files.createDirectories(exportsDir);
            Files.createDirectories(stampedDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建活动插件数据目录", e);
        }
    }

    Path dataDir() {
        return dataDir;
    }

    synchronized List<Map<String, Object>> activities() {
        return readList("activities.json");
    }

    synchronized void writeActivities(List<Map<String, Object>> rows) {
        write("activities.json", rows);
    }

    synchronized List<Map<String, Object>> participations() {
        return readList("participations.json");
    }

    synchronized void writeParticipations(List<Map<String, Object>> rows) {
        write("participations.json", rows);
    }

    synchronized Map<String, Map<String, Object>> identities() {
        return read(dataDir.resolve("identities.json"), new TypeReference<Map<String, Map<String, Object>>>() {
        }, new LinkedHashMap<>());
    }

    synchronized void writeIdentities(Map<String, Map<String, Object>> rows) {
        write("identities.json", rows);
    }

    synchronized Map<String, Object> settings() {
        return read(dataDir.resolve("settings.json"), new TypeReference<Map<String, Object>>() {
        }, new LinkedHashMap<>());
    }

    synchronized void writeSettings(Map<String, Object> settings) {
        write("settings.json", settings);
    }

    synchronized List<Map<String, Object>> exports() {
        return readList("exports.json");
    }

    synchronized void writeExports(List<Map<String, Object>> rows) {
        write("exports.json", rows);
    }

    synchronized boolean hasTemplate() {
        return Files.isRegularFile(templateFile);
    }

    synchronized byte[] template() throws IOException {
        return Files.isRegularFile(templateFile) ? Files.readAllBytes(templateFile) : null;
    }

    synchronized void writeTemplate(byte[] bytes) throws IOException {
        Path tmp = templateFile.resolveSibling("template.docx.tmp");
        Files.write(tmp, bytes);
        move(tmp, templateFile);
    }

    synchronized void saveExport(String id, byte[] zip) throws IOException {
        Files.write(exportsDir.resolve(id + ".zip"), zip);
    }

    synchronized byte[] exportZip(String id) throws IOException {
        Path file = exportsDir.resolve(id + ".zip");
        return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
    }

    synchronized void deleteExport(String id) {
        try {
            Files.deleteIfExists(exportsDir.resolve(id + ".zip"));
        } catch (IOException ignored) {
        }
    }

    synchronized List<Map<String, Object>> stamped() {
        return readList("stamped.json");
    }

    synchronized void writeStamped(List<Map<String, Object>> rows) {
        write("stamped.json", rows);
    }

    synchronized void saveStampedPdf(String id, byte[] bytes) throws IOException {
        Files.write(stampedDir.resolve(id + ".pdf"), bytes);
    }

    synchronized byte[] stampedPdf(String id) throws IOException {
        Path file = stampedDir.resolve(id + ".pdf");
        return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
    }

    synchronized void deleteStampedPdf(String id) {
        try {
            Files.deleteIfExists(stampedDir.resolve(id + ".pdf"));
        } catch (IOException ignored) {
        }
    }

    private List<Map<String, Object>> readList(String name) {
        return read(dataDir.resolve(name), new TypeReference<List<Map<String, Object>>>() {
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

    private void write(String name, Object value) {
        try {
            Path target = dataDir.resolve(name);
            Path tmp = target.resolveSibling(name + ".tmp");
            Files.writeString(tmp, MAPPER.writeValueAsString(value), StandardCharsets.UTF_8);
            move(tmp, target);
        } catch (IOException e) {
            throw new IllegalStateException("活动数据写入失败：" + name, e);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

package cn.edu.swust.mc.skin.plugins.feedback;

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

/** 反馈插件的数据文件存储：反馈文档与图片附件。 */
final class FeedbackStore {
    static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path dataDir;
    private final Path attachmentsDir;

    FeedbackStore(Path dataDir) {
        this.dataDir = dataDir;
        this.attachmentsDir = dataDir.resolve("attachments");
        try {
            Files.createDirectories(attachmentsDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建反馈插件数据目录", e);
        }
    }

    Path dataDir() {
        return dataDir;
    }

    synchronized List<Map<String, Object>> feedbacks() {
        return read(dataDir.resolve("feedbacks.json"),
                new TypeReference<List<Map<String, Object>>>() {
                }, new ArrayList<>());
    }

    synchronized void writeFeedbacks(List<Map<String, Object>> rows) {
        write("feedbacks.json", rows);
    }

    synchronized List<Map<String, Object>> categories() {
        return read(dataDir.resolve("categories.json"),
                new TypeReference<List<Map<String, Object>>>() {
                }, new ArrayList<>());
    }

    synchronized void writeCategories(List<Map<String, Object>> rows) {
        write("categories.json", rows);
    }

    synchronized void saveAttachment(String name, byte[] data) throws IOException {
        Path target = attachmentsDir.resolve(name).normalize();
        if (!target.startsWith(attachmentsDir)) throw new IllegalArgumentException("附件名不合法");
        Files.write(target, data);
    }

    synchronized byte[] attachment(String name) throws IOException {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return null;
        }
        Path target = attachmentsDir.resolve(name).normalize();
        if (!target.startsWith(attachmentsDir) || !Files.isRegularFile(target)) return null;
        return Files.readAllBytes(target);
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
            throw new IllegalStateException("反馈数据写入失败：" + name, e);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static Map<String, Object> row() {
        return new LinkedHashMap<>();
    }
}

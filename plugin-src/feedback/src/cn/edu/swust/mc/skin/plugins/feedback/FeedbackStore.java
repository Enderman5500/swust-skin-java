package cn.edu.swust.mc.skin.plugins.feedback;

import cn.edu.swust.mc.skin.Web;
import cn.edu.swust.mc.skin.plugin.PluginContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 反馈插件的数据存储：反馈文档与图片附件。 */
final class FeedbackStore {
    static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ATTACHMENT_KEY = "attachment:";

    private final PluginContext context;
    private final Path dataDir;
    private final Path attachmentsDir;

    FeedbackStore(PluginContext context) {
        this.context = context;
        this.dataDir = context.dataDir();
        this.attachmentsDir = dataDir.resolve("attachments");
        try {
            Files.createDirectories(attachmentsDir);
        } catch (IOException e) {
            // 附件以数据库为主、文件为缓存；目录不可写时仍可继续运行。
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

    /** 附件以数据库为主数据，文件目录只做读取缓存，避免部署环境目录不可写导致上传失败。 */
    synchronized void saveAttachment(String name, byte[] data) {
        if (!validAttachmentName(name)) throw Web.badRequest("附件名不合法");
        if (data == null || data.length == 0) throw Web.badRequest("附件内容为空");
        try {
            context.setData(ATTACHMENT_KEY + name, Base64.getEncoder().encodeToString(data));
        } catch (RuntimeException e) {
            throw new Web.HttpError(500, "attachment_store_failed",
                    "附件保存失败：" + shortMessage(e));
        }
        try {
            Path target = attachmentsDir.resolve(name).normalize();
            if (target.startsWith(attachmentsDir)) Files.write(target, data);
        } catch (IOException ignored) {
            // 缓存写失败不影响上传结果，读取时会回退到数据库。
        }
    }

    synchronized byte[] attachment(String name) {
        if (!validAttachmentName(name)) return null;
        Path target = attachmentsDir.resolve(name).normalize();
        if (target.startsWith(attachmentsDir) && Files.isRegularFile(target)) {
            try {
                return Files.readAllBytes(target);
            } catch (IOException ignored) {
                // 读取缓存失败时回退数据库。
            }
        }
        Object stored;
        try {
            stored = context.getData(ATTACHMENT_KEY + name);
        } catch (RuntimeException e) {
            return null;
        }
        if (stored instanceof String text && !text.isBlank()) {
            try {
                byte[] data = Base64.getDecoder().decode(text);
                try {
                    Files.write(target, data);
                } catch (IOException ignored) {
                    // 仅缓存，失败无妨。
                }
                return data;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 附件缓存文件路径；调用前先用 attachment(name) 确保数据库内容已落盘。 */
    synchronized Path attachmentPath(String name) {
        if (!validAttachmentName(name)) return null;
        Path target = attachmentsDir.resolve(name).normalize();
        if (!target.startsWith(attachmentsDir) || !Files.isRegularFile(target)) return null;
        return target;
    }

    synchronized void deleteAttachment(String url) {        String name = attachmentName(url);
        if (name == null) return;
        try {
            context.deleteData(ATTACHMENT_KEY + name);
        } catch (RuntimeException ignored) {
        }
        try {
            Files.deleteIfExists(attachmentsDir.resolve(name).normalize());
        } catch (IOException ignored) {
        }
    }

    private static String attachmentName(String url) {
        if (url == null) return null;
        int index = url.indexOf("file=");
        if (index < 0) return null;
        String name = url.substring(index + 5);
        int amp = name.indexOf('&');
        if (amp >= 0) name = name.substring(0, amp);
        return validAttachmentName(name) ? name : null;
    }

    private static boolean validAttachmentName(String name) {
        return name != null && name.matches("^[0-9a-f]{16}\\.(png|jpe?g|gif|webp|avif)$");
    }

    private static String shortMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return error.getClass().getSimpleName();
        return message.length() > 120 ? message.substring(0, 120) : message;
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

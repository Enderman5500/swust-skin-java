package cn.edu.swust.mc.skin;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Branding {
    public static final long MAX_BYTES = 8L * 1024 * 1024;

    private Branding() {
    }

    public record Image(String extension, String mime) {
    }

    public static synchronized Image save(Config config, Database db, byte[] data) throws IOException {
        if (data == null || data.length == 0) throw Web.badRequest("请选择要上传的图片");
        if (data.length > MAX_BYTES) throw new Web.HttpError(413, "payload_too_large", "图片不能超过 8MB");
        Image image = inspect(data);
        String hash = Security.sha256(data);
        String filename = hash + image.extension();
        Path dir = config.dataDir.resolve("branding");
        Files.createDirectories(dir);
        Path target = dir.resolve(filename).normalize();
        if (!target.startsWith(dir.normalize())) throw Web.badRequest("图片文件名不合法");
        List<String> banners = list(db);
        if (!banners.contains(filename)) {
            if (banners.size() >= 8) throw Web.conflict("首页主图最多上传 8 张");
            banners.add(filename);
        }
        if (!Files.exists(target)) Files.write(target, data);
        writeList(db, banners);
        return new Image(image.extension(), image.mime());
    }

    public static synchronized void remove(Config config, Database db) {
        List<String> banners = list(db);
        writeList(db, List.of());
        for (String banner : banners) {
            deleteFile(config.dataDir.resolve("branding"), banner);
        }
    }

    public static synchronized void remove(Config config, Database db, String filename) {
        if (publicUrl(filename) == null) throw Web.notFound("图片不存在");
        List<String> banners = list(db);
        if (!banners.remove(filename)) throw Web.notFound("图片不存在");
        writeList(db, banners);
        deleteFile(config.dataDir.resolve("branding"), filename);
    }

    public static synchronized List<String> list(Database db) {
        List<String> result = new ArrayList<>();
        String raw = Settings.get(db, "home_banners");
        if (raw != null && !raw.isBlank()) {
            try {
                List<?> parsed = Json.MAPPER.readValue(raw, new TypeReference<List<Object>>() {
                });
                for (Object item : parsed) {
                    String filename = item == null ? "" : String.valueOf(item);
                    if (publicUrl(filename) != null && !result.contains(filename)) result.add(filename);
                }
            } catch (Exception ignored) {
            }
        }
        if (result.isEmpty()) {
            String single = Settings.get(db, "home_banner");
            if (publicUrl(single) != null) result.add(single);
        }
        return result;
    }

    private static void writeList(Database db, List<String> banners) {
        Settings.set(db, "home_banners", Json.stringify(banners));
        Settings.set(db, "home_banner", banners.isEmpty() ? "" : banners.get(0));
    }

    public static Image inspect(byte[] data) {
        if (isPng(data)) return new Image(".png", "image/png");
        if (isJpeg(data)) return new Image(".jpg", "image/jpeg");
        if (isWebp(data)) return new Image(".webp", "image/webp");
        throw Web.badRequest("仅支持 PNG、JPG 或 WebP 图片");
    }

    public static String publicUrl(String filename) {
        if (filename == null || filename.isBlank()) return null;
        if (!filename.matches("^[0-9a-f]{64}\\.(png|jpg|webp)$")) return null;
        return "/branding/" + filename;
    }

    public static Path file(Config config, String filename) {
        if (filename == null || !filename.matches("^[0-9a-f]{64}\\.(png|jpg|webp)$")) return null;
        Path dir = config.dataDir.resolve("branding").normalize();
        Path target = dir.resolve(filename).normalize();
        return target.startsWith(dir) ? target : null;
    }

    private static void deleteFile(Path dir, String filename) {
        Path path = fileFrom(dir, filename);
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static Path fileFrom(Path dir, String filename) {
        if (!filename.matches("^[0-9a-f]{64}\\.(png|jpg|webp)$")) return null;
        Path base = dir.normalize();
        Path target = base.resolve(filename).normalize();
        return target.startsWith(base) ? target : null;
    }

    private static boolean isPng(byte[] data) {
        return data.length > 8
                && (data[0] & 0xff) == 0x89 && data[1] == 0x50 && data[2] == 0x4e && data[3] == 0x47
                && data[4] == 0x0d && data[5] == 0x0a && data[6] == 0x1a && data[7] == 0x0a;
    }

    private static boolean isJpeg(byte[] data) {
        return data.length > 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8
                && (data[2] & 0xff) == 0xff;
    }

    private static boolean isWebp(byte[] data) {
        return data.length > 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }
}

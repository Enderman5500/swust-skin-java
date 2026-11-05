package cn.edu.swust.mc.skin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Avatars {
    public static final long MAX_BYTES = 2L * 1024 * 1024;

    private Avatars() {
    }

    public static synchronized String save(Config config, byte[] data) throws IOException {
        if (data == null || data.length == 0) throw Web.badRequest("请选择头像图片");
        if (data.length > MAX_BYTES) throw new Web.HttpError(413, "payload_too_large", "头像不能超过 2MB");
        Branding.Image image = Branding.inspect(data);
        String filename = Security.sha256(data) + image.extension();
        Path dir = config.dataDir.resolve("avatars").normalize();
        Files.createDirectories(dir);
        Path target = dir.resolve(filename).normalize();
        if (!target.startsWith(dir)) throw Web.badRequest("头像文件名不合法");
        if (!Files.exists(target)) Files.write(target, data);
        return filename;
    }

    public static String url(String filename) {
        return valid(filename) ? "/avatars/" + filename : null;
    }

    public static Path file(Config config, String filename) {
        if (!valid(filename)) return null;
        Path dir = config.dataDir.resolve("avatars").normalize();
        Path target = dir.resolve(filename).normalize();
        return target.startsWith(dir) ? target : null;
    }

    private static boolean valid(String filename) {
        return filename != null && filename.matches("^[0-9a-f]{64}\\.(png|jpg|webp)$");
    }
}

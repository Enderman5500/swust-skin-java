package cn.edu.swust.mc.skin.plugin;

import cn.edu.swust.mc.skin.Web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 插件二进制响应：用于下载插件私有目录中的文件（支持 ETag / Range）。
 *
 * 普通插件路由返回对象时宿主会自动包成 JSON；需要原样输出文件时返回本类型。
 */
public final class PluginResponse {
    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    private final Path file;
    private final String contentType;
    private final String etag;
    private final String cacheControl;

    private PluginResponse(Path file, String contentType, String etag, String cacheControl) {
        this.file = file;
        this.contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
        this.etag = etag == null ? "" : etag;
        this.cacheControl = cacheControl == null ? "no-store" : cacheControl;
    }

    public static PluginResponse file(Path file, String contentType, String etag, String cacheControl) {
        return new PluginResponse(file, contentType, etag, cacheControl);
    }

    public static PluginResponse file(Path file, String contentType, String etag) {
        return file(file, contentType, etag, "public, max-age=31536000, immutable");
    }

    public void write(Web.Ctx ctx) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            throw new Web.HttpError(404, "not_found", "文件不存在");
        }
        var exchange = ctx.exchange;
        long size = Files.size(file);
        String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
        exchange.getResponseHeaders().set("ETag", etag);
        exchange.getResponseHeaders().set("Cache-Control", cacheControl);
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (!etag.isEmpty() && etag.equals(ifNoneMatch)) {
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }

        long start = 0;
        long end = size > 0 ? size - 1 : 0;
        boolean partial = false;
        String range = exchange.getRequestHeaders().getFirst("Range");
        if (range != null && !range.isBlank()) {
            long[] parsed = parseRange(range, size);
            if (parsed == null) {
                exchange.getResponseHeaders().set("Content-Range", "bytes */" + size);
                exchange.sendResponseHeaders(416, -1);
                exchange.close();
                return;
            }
            start = parsed[0];
            end = parsed[1];
            partial = true;
            exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + size);
        }

        long length = size == 0 ? 0 : end - start + 1;
        boolean head = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(length));
        exchange.sendResponseHeaders(partial ? 206 : 200, head ? -1 : length);
        if (head) {
            exchange.close();
            return;
        }
        try (InputStream input = Files.newInputStream(file);
             OutputStream output = exchange.getResponseBody()) {
            skipFully(input, start);
            byte[] buffer = new byte[64 * 1024];
            long remaining = length;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) break;
                output.write(buffer, 0, read);
                remaining -= read;
            }
        } finally {
            exchange.close();
        }
    }

    private static void skipFully(InputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }
            if (input.read() < 0) return;
            remaining--;
        }
    }

    /** 只支持单段 Range；返回 [start, end]，非法返回 null。 */
    private static long[] parseRange(String header, long size) {
        String value = header.trim();
        if (!value.startsWith("bytes=")) return null;
        String spec = value.substring(6).trim();
        if (spec.contains(",")) return null;
        int dash = spec.indexOf('-');
        if (dash < 0) return null;
        String left = spec.substring(0, dash).trim();
        String right = spec.substring(dash + 1).trim();
        try {
            long start;
            long end;
            if (left.isEmpty()) {
                long suffix = Long.parseLong(right);
                if (suffix <= 0 || size == 0) return null;
                start = Math.max(0, size - suffix);
                end = size - 1;
            } else {
                start = Long.parseLong(left);
                end = right.isEmpty() ? size - 1 : Long.parseLong(right);
            }
            if (start < 0 || start >= size || end < start) return null;
            return new long[]{start, Math.min(end, size - 1)};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

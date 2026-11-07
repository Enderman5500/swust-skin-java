package cn.edu.swust.mc.skin;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class LogRoutes {
    private static final long TAIL_BYTES = 512L * 1024;
    private static final long HEARTBEAT_MILLIS = 15_000;

    private LogRoutes() {
    }

    public static void register(App app) {
        app.router.get("/api/admin/logs", ctx -> {
            Auth.requirePermission(ctx, "system:setting:view");
            ctx.ok(listFiles());
        });
        app.router.get("/api/admin/logs/download", LogRoutes::download);
        app.router.get("/api/admin/logs/stream", LogRoutes::stream);
    }

    private static Map<String, Object> listFiles() {
        Map<String, Object> data = new LinkedHashMap<>();
        Path directory = Log.directory();
        Path current = Log.currentFile();
        Map<String, Object> currentInfo = null;
        if (current != null && Files.isRegularFile(current)) {
            currentInfo = describe(current, true);
        }
        List<Map<String, Object>> archives = new ArrayList<>();
        if (directory != null && Files.isDirectory(directory)) {
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> Log.ARCHIVE_FILE_PATTERN.matcher(path.getFileName().toString()).matches())
                        .forEach(path -> archives.add(describe(path, false)));
            } catch (IOException ignored) {
            }
        }
        archives.sort(Comparator.comparingLong(
                (Map<String, Object> item) -> ((Number) item.get("modifiedMillis")).longValue()).reversed());
        data.put("current", currentInfo);
        data.put("archives", archives);
        return data;
    }

    private static Map<String, Object> describe(Path file, boolean current) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", file.getFileName().toString());
        item.put("current", current);
        try {
            item.put("size", Files.size(file));
            long modified = Files.getLastModifiedTime(file).toMillis();
            item.put("modifiedMillis", modified);
            item.put("modifiedAt", Instant.ofEpochMilli(modified).toString());
        } catch (IOException e) {
            item.put("size", 0);
            item.put("modifiedMillis", 0);
            item.put("modifiedAt", null);
        }
        return item;
    }

    private static void download(Web.Ctx ctx) throws IOException {
        Auth.requirePermission(ctx, "system:setting:view");
        String name = ctx.query("file", "").trim();
        boolean current = Log.CURRENT_FILE_NAME.equals(name);
        if (!current && !Log.ARCHIVE_FILE_PATTERN.matcher(name).matches()) {
            throw Web.badRequest("日志文件名不合法");
        }
        Path directory = Log.directory();
        if (directory == null) throw Web.notFound("日志文件不存在");
        Path file = directory.resolve(name).normalize();
        if (!file.startsWith(directory.normalize()) || !Files.isRegularFile(file)) {
            throw Web.notFound("日志文件不存在");
        }
        long size = Files.size(file);
        var headers = ctx.exchange.getResponseHeaders();
        headers.set("Content-Type", "text/plain; charset=utf-8");
        headers.set("Content-Disposition", "attachment; filename=\"" + name + "\"");
        headers.set("Cache-Control", "no-store");
        boolean head = "HEAD".equals(ctx.method);
        ctx.exchange.sendResponseHeaders(200, head ? -1 : size);
        if (head) {
            ctx.exchange.close();
            return;
        }
        try (OutputStream output = ctx.exchange.getResponseBody()) {
            Files.copy(file, output);
        }
    }

    private static void stream(Web.Ctx ctx) throws Exception {
        Auth.requirePermission(ctx, "system:setting:view");
        HttpExchange exchange = ctx.exchange;
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/event-stream; charset=utf-8");
        headers.set("Cache-Control", "no-cache, no-transform");
        headers.set("Connection", "keep-alive");
        headers.set("X-Accel-Buffering", "no");
        if ("HEAD".equals(ctx.method)) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }

        exchange.sendResponseHeaders(200, 0);
        int tail = clamp(parseInt(ctx.query("tail"), 600), 0, 2000);
        OutputStream output = exchange.getResponseBody();
        long sequence = 1;
        RandomAccessFile reader = null;
        Object fileKey = null;
        long position = 0;
        ByteArrayOutputStream pending = new ByteArrayOutputStream();

        try {
            write(output, "retry: 3000\n\n");
            Path current = Log.currentFile();
            if (current != null && Files.isRegularFile(current)) {
                for (String line : tail(current, tail)) {
                    sequence = send(output, sequence, line);
                }
                reader = new RandomAccessFile(current.toFile(), "r");
                position = reader.length();
                fileKey = fileKey(current);
            }

            long lastHeartbeat = System.currentTimeMillis();
            while (!Thread.currentThread().isInterrupted()) {
                boolean readSomething = false;
                Path file = Log.currentFile();
                if (file != null && Files.isRegularFile(file)) {
                    Object key = fileKey(file);
                    if (reader == null || !Objects.equals(key, fileKey)) {
                        close(reader);
                        reader = new RandomAccessFile(file.toFile(), "r");
                        fileKey = key;
                        position = 0;
                        pending.reset();
                    }
                    long length = reader.length();
                    if (length < position) {
                        close(reader);
                        reader = new RandomAccessFile(file.toFile(), "r");
                        position = 0;
                        pending.reset();
                    }
                    if (length > position) {
                        reader.seek(position);
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = reader.read(buffer)) > 0) {
                            List<String> lines = split(buffer, count, pending);
                            for (String line : lines) sequence = send(output, sequence, line);
                            position += count;
                            readSomething = true;
                        }
                    }
                } else {
                    close(reader);
                    reader = null;
                    fileKey = null;
                }

                long now = System.currentTimeMillis();
                if (now - lastHeartbeat >= HEARTBEAT_MILLIS) {
                    write(output, ": ping\n\n");
                    lastHeartbeat = now;
                }
                if (!readSomething) Thread.sleep(400);
            }
        } catch (IOException ignored) {
            // Client disconnected or log file was replaced.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            close(reader);
            try {
                exchange.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static List<String> tail(Path file, int limit) throws IOException {
        if (limit <= 0) return List.of();
        try (RandomAccessFile reader = new RandomAccessFile(file.toFile(), "r")) {
            long length = reader.length();
            long start = Math.max(0, length - TAIL_BYTES);
            reader.seek(start);
            byte[] data = new byte[(int) (length - start)];
            reader.readFully(data);
            int offset = 0;
            if (start > 0) {
                int newline = indexOf(data, 0, data.length, (byte) '\n');
                offset = newline < 0 ? data.length : newline + 1;
            }
            List<String> lines = new ArrayList<>();
            int lineStart = offset;
            for (int i = offset; i < data.length; i++) {
                if (data[i] != '\n') continue;
                lines.add(decodeLine(data, lineStart, i));
                lineStart = i + 1;
            }
            if (lineStart < data.length) lines.add(decodeLine(data, lineStart, data.length));
            if (lines.size() > limit) {
                return new ArrayList<>(lines.subList(lines.size() - limit, lines.size()));
            }
            return lines;
        }
    }

    private static List<String> split(byte[] data, int length, ByteArrayOutputStream pending) {
        pending.write(data, 0, length);
        byte[] all = pending.toByteArray();
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < all.length; i++) {
            if (all[i] != '\n') continue;
            lines.add(decodeLine(all, start, i));
            start = i + 1;
        }
        pending.reset();
        if (start < all.length) pending.write(all, start, all.length - start);
        return lines;
    }

    private static String decodeLine(byte[] data, int start, int end) {
        if (end > start && data[end - 1] == '\r') end--;
        return new String(data, start, end - start, StandardCharsets.UTF_8);
    }

    private static int indexOf(byte[] data, int start, int end, byte value) {
        for (int i = start; i < end; i++) {
            if (data[i] == value) return i;
        }
        return -1;
    }

    private static long send(OutputStream output, long sequence, String line) throws IOException {
        String safe = line.replace("\r", "");
        write(output, "id: " + sequence + "\nevent: log\ndata: " + safe + "\n\n");
        return sequence + 1;
    }

    private static void write(OutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private static Object fileKey(Path file) {
        try {
            return Files.readAttributes(file, BasicFileAttributes.class).fileKey();
        } catch (IOException e) {
            return null;
        }
    }

    private static void close(RandomAccessFile reader) {
        if (reader == null) return;
        try {
            reader.close();
        } catch (IOException ignored) {
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

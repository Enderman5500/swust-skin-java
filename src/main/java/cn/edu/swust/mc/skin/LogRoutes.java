package cn.edu.swust.mc.skin;

import com.sun.net.httpserver.HttpExchange;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class LogRoutes {
    private static final long TAIL_BYTES = 512L * 1024;
    private static final long HEARTBEAT_MILLIS = 15_000;
    private static final String POISON = new String("__journal_exit__");
    private static final DateTimeFormatter DOWNLOAD_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Path NGINX_LOG_DIR =
            Path.of(env("NGINX_LOG_DIR", "/var/log/nginx")).toAbsolutePath().normalize();
    private static final String JOURNAL_UNIT = env("SYSTEMD_UNIT", "swust-skin");
    private static final Pattern NGINX_ACCESS_ARCHIVE = Pattern.compile("^access\\.log-.*$");
    private static final Pattern NGINX_ERROR_ARCHIVE = Pattern.compile("^error\\.log-.*$");
    private static final Set<String> SOURCES = Set.of(
            "app", "nginx-access", "nginx-error", "journal-service", "journal-system");

    private static volatile Boolean journalReadable;
    private static volatile long journalCheckedAt;

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
        Map<String, Object> app = fileGroup(Log.directory(), Log.CURRENT_FILE_NAME,
                Log.ARCHIVE_FILE_PATTERN);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", app);
        data.put("current", app.get("current"));
        data.put("archives", app.get("archives"));
        data.put("nginxAccess", fileGroup(NGINX_LOG_DIR, "access.log", NGINX_ACCESS_ARCHIVE));
        data.put("nginxError", fileGroup(NGINX_LOG_DIR, "error.log", NGINX_ERROR_ARCHIVE));
        Map<String, Object> journal = new LinkedHashMap<>();
        journal.put("available", journalReadable());
        journal.put("service", true);
        journal.put("system", true);
        data.put("journal", journal);
        return data;
    }

    private static Map<String, Object> fileGroup(Path dir, String currentName, Pattern archivePattern) {
        Map<String, Object> group = new LinkedHashMap<>();
        Map<String, Object> currentInfo = null;
        List<Map<String, Object>> archives = new ArrayList<>();
        if (dir != null && Files.isDirectory(dir)) {
            Path current = dir.resolve(currentName);
            if (Files.isRegularFile(current)) {
                currentInfo = describe(current, true);
            }
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> archivePattern.matcher(path.getFileName().toString()).matches())
                        .forEach(path -> archives.add(describe(path, false)));
            } catch (IOException ignored) {
            }
        }
        archives.sort(Comparator.comparingLong(
                (Map<String, Object> item) -> ((Number) item.get("modifiedMillis")).longValue()).reversed());
        group.put("current", currentInfo);
        group.put("archives", archives);
        return group;
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
        String source = source(ctx.query("source", "app"));
        if (isJournal(source)) {
            downloadJournal(ctx, source);
            return;
        }
        String name = ctx.query("file", "").trim();
        Path directory;
        Pattern archivePattern;
        String currentName;
        if ("app".equals(source)) {
            directory = Log.directory();
            currentName = Log.CURRENT_FILE_NAME;
            archivePattern = Log.ARCHIVE_FILE_PATTERN;
        } else if ("nginx-access".equals(source)) {
            directory = NGINX_LOG_DIR;
            currentName = "access.log";
            archivePattern = NGINX_ACCESS_ARCHIVE;
        } else {
            directory = NGINX_LOG_DIR;
            currentName = "error.log";
            archivePattern = NGINX_ERROR_ARCHIVE;
        }
        if (directory == null || !(currentName.equals(name) || archivePattern.matcher(name).matches())) {
            throw Web.badRequest("日志文件名不合法");
        }
        Path file = directory.resolve(name).normalize();
        if (!file.startsWith(directory.normalize()) || !Files.isRegularFile(file)) {
            throw Web.notFound("日志文件不存在");
        }
        long size = Files.size(file);
        var headers = ctx.exchange.getResponseHeaders();
        headers.set("Content-Type", name.endsWith(".gz")
                ? "application/gzip" : "text/plain; charset=utf-8");
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

    private static void downloadJournal(Web.Ctx ctx, String source) throws IOException {
        boolean head = "HEAD".equals(ctx.method);
        int hours = clamp(parseInt(ctx.query("hours"), 24), 1, 720);
        String filename = source + "-" + LocalDateTime.now().format(DOWNLOAD_STAMP) + ".log";
        if (!head) {
            Process process;
            try {
                List<String> command = journalCommand(source);
                command.add("--since");
                command.add("@" + Instant.now().minus(hours, ChronoUnit.HOURS).getEpochSecond());
                process = new ProcessBuilder(command).redirectErrorStream(true).start();
            } catch (IOException e) {
                throw new Web.HttpError(503, "journal_unavailable", "无法读取系统日志：" + e.getMessage());
            }
            var headers = ctx.exchange.getResponseHeaders();
            headers.set("Content-Type", "text/plain; charset=utf-8");
            headers.set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            headers.set("Cache-Control", "no-store");
            ctx.exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = ctx.exchange.getResponseBody()) {
                process.getInputStream().transferTo(output);
            } finally {
                process.destroy();
            }
            return;
        }
        var headers = ctx.exchange.getResponseHeaders();
        headers.set("Content-Type", "text/plain; charset=utf-8");
        headers.set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        headers.set("Cache-Control", "no-store");
        ctx.exchange.sendResponseHeaders(200, -1);
        ctx.exchange.close();
    }

    private static void stream(Web.Ctx ctx) throws Exception {
        Auth.requirePermission(ctx, "system:setting:view");
        String source = source(ctx.query("source", "app"));
        int tail = clamp(parseInt(ctx.query("tail"), 600), 0, 2000);
        if (isJournal(source)) {
            streamJournal(ctx, source, tail);
        } else {
            streamFile(ctx, fileSupplier(source), tail);
        }
    }

    private static Supplier<Path> fileSupplier(String source) {
        return switch (source) {
            case "app" -> Log::currentFile;
            case "nginx-access" -> () -> NGINX_LOG_DIR.resolve("access.log");
            case "nginx-error" -> () -> NGINX_LOG_DIR.resolve("error.log");
            default -> throw Web.badRequest("日志来源不合法");
        };
    }

    private static void streamFile(Web.Ctx ctx, Supplier<Path> fileSupplier, int tail) throws Exception {
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
        OutputStream output = exchange.getResponseBody();
        long sequence = 1;
        RandomAccessFile reader = null;
        Object fileKey = null;
        long position = 0;
        ByteArrayOutputStream pending = new ByteArrayOutputStream();

        try {
            write(output, "retry: 3000\n\n");
            Path current = fileSupplier.get();
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
                Path file = fileSupplier.get();
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

    private static void streamJournal(Web.Ctx ctx, String source, int tail) throws Exception {
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

        List<String> command = journalCommand(source);
        command.add("-n");
        command.add(String.valueOf(Math.max(0, tail)));
        command.add("-f");
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new Web.HttpError(503, "journal_unavailable", "无法读取系统日志：" + e.getMessage());
        }

        exchange.sendResponseHeaders(200, 0);
        OutputStream output = exchange.getResponseBody();
        BlockingQueue<String> queue = new LinkedBlockingQueue<>(4000);
        Thread.ofVirtual().name("journal-reader").start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    while (!queue.offer(line)) queue.poll();
                }
            } catch (IOException ignored) {
            } finally {
                queue.offer(POISON);
            }
        });

        long sequence = 1;
        try {
            write(output, "retry: 3000\n\n");
            while (!Thread.currentThread().isInterrupted()) {
                String line = queue.poll(HEARTBEAT_MILLIS, TimeUnit.MILLISECONDS);
                if (line == null) {
                    write(output, ": ping\n\n");
                    continue;
                }
                if (POISON.equals(line)) {
                    if (!process.isAlive() && process.exitValue() != 0) {
                        sequence = send(output, sequence, "[system] journalctl 已退出，请检查服务账号是否有日志读取权限");
                    }
                    break;
                }
                sequence = send(output, sequence, line);
            }
        } catch (IOException ignored) {
            // Client disconnected.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            process.destroy();
            try {
                exchange.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static List<String> journalCommand(String source) {
        List<String> command = new ArrayList<>();
        command.add(env("JOURNALCTL", "journalctl"));
        command.add("--no-pager");
        command.add("-o");
        command.add("short-iso");
        if ("journal-service".equals(source)) {
            command.add("-u");
            command.add(JOURNAL_UNIT);
        }
        return command;
    }

    private static boolean journalReadable() {
        long now = System.currentTimeMillis();
        if (journalReadable != null && now - journalCheckedAt < 30_000) return journalReadable;
        boolean value = false;
        try {
            List<String> command = journalCommand("journal-service");
            command.add("-n");
            command.add("1");
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            value = process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0;
            process.destroy();
        } catch (Exception ignored) {
        }
        journalReadable = value;
        journalCheckedAt = now;
        return value;
    }

    private static String source(String raw) {
        String value = raw == null || raw.isBlank() ? "app" : raw.trim();
        if (!SOURCES.contains(value)) throw Web.badRequest("日志来源不合法");
        return value;
    }

    private static boolean isJournal(String source) {
        return "journal-service".equals(source) || "journal-system".equals(source);
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

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}

package cn.edu.swust.mc.skin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class Log {
    public static final String CURRENT_FILE_NAME = "swust-skin.log";
    public static final Pattern ARCHIVE_FILE_PATTERN =
            Pattern.compile("^swust-skin-\\d{8}-\\d{6}(?:-\\d+)?\\.log$");

    private static final Object LOCK = new Object();
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int RETENTION_DAYS = 30;
    private static final int MAX_ARCHIVES = 100;
    private static final DateTimeFormatter ARCHIVE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static Path directory;
    private static Path currentFile;
    private static String currentDate;
    private static BufferedWriter writer;
    private static boolean writeErrorLogged;

    private Log() {
    }

    public static void init(Path dir) {
        synchronized (LOCK) {
            directory = dir.toAbsolutePath().normalize();
            try {
                Files.createDirectories(directory);
                restrict(directory, "rwx------");
                currentFile = directory.resolve(CURRENT_FILE_NAME);
                if (Files.isRegularFile(currentFile) && Files.size(currentFile) > 0) {
                    archiveCurrentLocked();
                }
                currentDate = LocalDate.now().toString();
                openLocked();
                cleanupLocked();
            } catch (IOException e) {
                closeWriterLocked();
                System.err.println("无法初始化日志文件：" + e.getMessage());
            }
        }
    }

    public static Path directory() {
        synchronized (LOCK) {
            return directory;
        }
    }

    public static Path currentFile() {
        synchronized (LOCK) {
            return currentFile;
        }
    }

    public static void info(String message) {
        emit("INFO ", message, false);
    }

    public static void warn(String message) {
        emit("WARN ", message, false);
    }

    public static void error(String message) {
        emit("ERROR", message, true);
    }

    public static void error(Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        emit("ERROR", trace.toString().stripTrailing(), true);
    }

    private static void emit(String level, String message, boolean stderr) {
        String line = "[" + OffsetDateTime.now() + "] " + level + " " + (message == null ? "" : message);
        if (stderr) System.err.println(line);
        else System.out.println(line);
        appendToFile(line);
    }

    private static void appendToFile(String line) {
        synchronized (LOCK) {
            if (writer == null || currentFile == null) return;
            try {
                if (!LocalDate.now().toString().equals(currentDate) || Files.size(currentFile) >= MAX_BYTES) {
                    rotateLocked();
                }
                writer.write(line);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                closeWriterLocked();
                if (!writeErrorLogged) {
                    writeErrorLogged = true;
                    System.err.println("写入日志文件失败，后续日志仅输出到终端：" + e.getMessage());
                }
            }
        }
    }

    private static void rotateLocked() {
        closeWriterLocked();
        try {
            if (currentFile != null && Files.isRegularFile(currentFile) && Files.size(currentFile) > 0) {
                archiveCurrentLocked();
            }
            currentDate = LocalDate.now().toString();
            openLocked();
            cleanupLocked();
        } catch (IOException e) {
            closeWriterLocked();
            System.err.println("轮转日志文件失败：" + e.getMessage());
        }
    }

    private static void archiveCurrentLocked() throws IOException {
        Path archive = nextArchivePath();
        try {
            Files.move(currentFile, archive, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(currentFile, archive);
        }
    }

    private static Path nextArchivePath() {
        String stamp = LocalDateTime.now().format(ARCHIVE_STAMP);
        Path candidate = directory.resolve("swust-skin-" + stamp + ".log");
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve("swust-skin-" + stamp + "-" + suffix++ + ".log");
        }
        return candidate;
    }

    private static void openLocked() throws IOException {
        if (currentFile == null) return;
        writer = Files.newBufferedWriter(currentFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        writeErrorLogged = false;
        restrict(currentFile, "rw-------");
    }

    private static void closeWriterLocked() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException ignored) {
        }
        writer = null;
    }

    private static void cleanupLocked() {
        if (directory == null || !Files.isDirectory(directory)) return;
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        List<Path> archives = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> ARCHIVE_FILE_PATTERN.matcher(path.getFileName().toString()).matches())
                    .forEach(archives::add);
        } catch (IOException e) {
            return;
        }
        for (Path archive : archives) {
            try {
                if (Files.getLastModifiedTime(archive).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(archive);
                }
            } catch (IOException ignored) {
            }
        }
        archives.removeIf(path -> !Files.exists(path));
        archives.sort(Comparator.comparingLong(Log::modifiedMillis));
        while (archives.size() > MAX_ARCHIVES) {
            Path oldest = archives.remove(0);
            try {
                Files.deleteIfExists(oldest);
            } catch (IOException ignored) {
            }
        }
    }

    private static long modifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static void restrict(Path path, String permissions) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        } catch (Exception ignored) {
            // Windows and non-POSIX filesystems do not support POSIX permissions.
        }
    }
}

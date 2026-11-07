package cn.edu.swust.mc.report;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ReportConfig {
    public static final String DEFAULT_BASE_URL = "https://www.swustmc.com";

    public final String baseUrl;
    public final String serverId;
    public final String apiKey;
    public final int reportIntervalSeconds;
    public final int afkTimeoutMinutes;
    public final int httpTimeoutSeconds;
    public final boolean debug;

    public ReportConfig(String baseUrl, String serverId, String apiKey,
                        int reportIntervalSeconds, int afkTimeoutMinutes,
                        int httpTimeoutSeconds, boolean debug) {
        String cleanBase = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        this.baseUrl = cleanBase.replaceAll("/+$", "");
        this.serverId = serverId == null ? "" : serverId.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.reportIntervalSeconds = Math.max(10, reportIntervalSeconds);
        this.afkTimeoutMinutes = Math.max(1, afkTimeoutMinutes);
        this.httpTimeoutSeconds = Math.max(3, httpTimeoutSeconds);
        this.debug = debug;
    }

    public boolean valid() {
        return !serverId.isEmpty() && !apiKey.isEmpty() && !baseUrl.isEmpty();
    }

    public long afkTimeoutMillis() {
        return afkTimeoutMinutes * 60_000L;
    }

    public static ReportConfig load(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }
        }
        ReportConfig config = new ReportConfig(
                properties.getProperty("base-url", DEFAULT_BASE_URL),
                properties.getProperty("server-id", ""),
                properties.getProperty("api-key", ""),
                parseInt(properties.getProperty("report-interval-seconds"), 30),
                parseInt(properties.getProperty("afk-timeout-minutes"), 5),
                parseInt(properties.getProperty("http-timeout-seconds"), 10),
                Boolean.parseBoolean(properties.getProperty("debug", "false"))
        );
        config.save(file);
        return config;
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Properties properties = new Properties();
        properties.setProperty("base-url", baseUrl);
        properties.setProperty("server-id", serverId);
        properties.setProperty("api-key", apiKey);
        properties.setProperty("report-interval-seconds", String.valueOf(reportIntervalSeconds));
        properties.setProperty("afk-timeout-minutes", String.valueOf(afkTimeoutMinutes));
        properties.setProperty("http-timeout-seconds", String.valueOf(httpTimeoutSeconds));
        properties.setProperty("debug", String.valueOf(debug));
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "Minecraft server report agent");
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

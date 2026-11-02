package cn.edu.swust.mc.skin;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Config {
    public final Path root;
    public final String host;
    public final int port;
    public final String baseUrl;
    public final boolean https;
    public final Path dataDir;
    public final Path publicDir;
    public final Path pluginsDir;
    public final String sessionCookie = "swust_session";
    public final int sessionDays = 14;
    public final boolean trustProxy;
    public final boolean seedDemo;
    public final long jsonLimit = 1024 * 1024;
    public final long textureLimit = 4 * 1024 * 1024;
    public final long pluginLimit = 32 * 1024 * 1024;
    public final String adminUsername;
    public final String adminEmail;
    public final String adminPassword;
    public final long snowflakeDataCenter;
    public final long snowflakeMachine;

    public Config() {
        this.root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        loadDotEnv(root.resolve(".env"));
        this.host = env("HOST", "0.0.0.0");
        this.port = parseInt(env("PORT", "3000"), 3000);
        String rawBase = env("BASE_URL", "http://localhost:" + port).replaceAll("/+$", "");
        this.baseUrl = rawBase.matches("(?i)^https?://.*") ? rawBase : "http://" + rawBase;
        this.https = baseUrl.toLowerCase().startsWith("https://");
        this.dataDir = root.resolve(env("DATA_DIR", "data")).normalize();
        this.publicDir = root.resolve("public").normalize();
        this.pluginsDir = root.resolve(env("PLUGINS_DIR", "plugins")).normalize();
        this.trustProxy = "1".equals(env("TRUST_PROXY", "0"));
        this.seedDemo = !"0".equals(env("SEED_DEMO", "1"));
        this.adminUsername = env("ADMIN_USERNAME", "admin").trim();
        this.adminEmail = env("ADMIN_EMAIL", "admin@example.com").trim().toLowerCase();
        this.adminPassword = env("ADMIN_PASSWORD", "swustmc2026");
        this.snowflakeDataCenter = Math.max(0, Math.min(31, parseInt(env("SNOWFLAKE_DATA_CENTER_ID", "1"), 1)));
        this.snowflakeMachine = Math.max(0, Math.min(31, parseInt(env("SNOWFLAKE_MACHINE_ID", "1"), 1)));
        try {
            Files.createDirectories(dataDir.resolve("textures"));
            Files.createDirectories(dataDir.resolve("keys"));
            Files.createDirectories(dataDir.resolve("tmp"));
            Files.createDirectories(dataDir.resolve("branding"));
            Files.createDirectories(pluginsDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建数据目录", e);
        }
    }

    private static final Map<String, String> DOT_ENV = new HashMap<>();

    private static void loadDotEnv(Path file) {
        if (!Files.isRegularFile(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String text = line.strip();
                if (text.isEmpty() || text.startsWith("#")) continue;
                int index = text.indexOf('=');
                if (index <= 0) continue;
                String key = text.substring(0, index).strip();
                String value = text.substring(index + 1).strip();
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                DOT_ENV.putIfAbsent(key, value);
            }
        } catch (IOException ignored) {
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        if (value != null) return value;
        return DOT_ENV.getOrDefault(key, fallback);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String url(String pathname) {
        return baseUrl + (pathname.startsWith("/") ? pathname : "/" + pathname);
    }

    public URL resource(String name) {
        return Config.class.getClassLoader().getResource(name);
    }
}

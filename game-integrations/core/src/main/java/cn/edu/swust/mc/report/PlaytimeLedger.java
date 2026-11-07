package cn.edu.swust.mc.report;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class PlaytimeLedger {
    private static final String PREFIX = "p.";

    private final Path file;
    private final long afkTimeoutMillis;
    private final Map<String, State> states = new LinkedHashMap<>();

    public PlaytimeLedger(Path file, long afkTimeoutMillis) throws IOException {
        this.file = file;
        this.afkTimeoutMillis = Math.max(60_000L, afkTimeoutMillis);
        load();
    }

    public synchronized void join(String uuid, String name, long now) {
        String key = key(uuid, name);
        State state = states.get(key);
        if (state == null) {
            state = new State();
            state.key = key;
            states.put(key, state);
        }
        state.name = safeName(name);
        state.online = true;
        state.sessionStartedAt = now;
        state.lastSampleAt = now;
        state.lastActivityAt = now;
        state.lastSeenAt = now;
        saveQuietly();
    }

    public synchronized void leave(String uuid, String name, long now) {
        State state = states.get(key(uuid, name));
        if (state == null) return;
        sampleOne(state, now);
        state.online = false;
        state.lastSeenAt = now;
        saveQuietly();
    }

    public synchronized void markActivity(String uuid, String name, long now) {
        State state = states.get(key(uuid, name));
        if (state != null) state.lastActivityAt = now;
    }

    public synchronized void sample(List<OnlinePlayer> online, long now) {
        Map<String, OnlinePlayer> current = new LinkedHashMap<>();
        for (OnlinePlayer player : online) {
            if (player == null || player.name() == null) continue;
            current.put(key(player.uuid(), player.name()), player);
        }
        for (Map.Entry<String, OnlinePlayer> entry : current.entrySet()) {
            String key = entry.getKey();
            OnlinePlayer player = entry.getValue();
            State state = states.get(key);
            if (state == null) {
                state = new State();
                state.key = key;
                state.name = safeName(player.name());
                state.sessionStartedAt = now;
                state.lastSampleAt = now;
                state.lastActivityAt = now;
                states.put(key, state);
            }
            state.name = safeName(player.name());
            state.online = true;
            sampleOne(state, now);
        }
        for (State state : states.values()) {
            if (state.online && !current.containsKey(state.key)) {
                sampleOne(state, now);
                state.online = false;
                state.lastSeenAt = now;
            }
        }
        saveQuietly();
    }

    public synchronized List<PlayerTime> snapshot() {
        List<PlayerTime> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (State state : states.values()) {
            long total = Math.max(0, state.totalMillis);
            long afk = Math.min(total, Math.max(0, state.afkMillis));
            boolean currentlyAfk = state.online && now - state.lastActivityAt >= afkTimeoutMillis;
            result.add(new PlayerTime(
                    state.key,
                    state.name,
                    total,
                    afk,
                    state.online,
                    currentlyAfk,
                    iso(state.sessionStartedAt),
                    iso(state.lastSeenAt)
            ));
        }
        return result;
    }

    public synchronized void save() throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Properties properties = new Properties();
        for (State state : states.values()) {
            String base = PREFIX + state.key;
            properties.setProperty(base + ".name", state.name);
            properties.setProperty(base + ".total", String.valueOf(Math.max(0, state.totalMillis)));
            properties.setProperty(base + ".afk", String.valueOf(Math.max(0, state.afkMillis)));
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) {
            properties.store(output, "Minecraft playtime ledger");
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void sampleOne(State state, long now) {
        if (!state.online) return;
        long delta = Math.max(0, now - state.lastSampleAt);
        if (delta > 0) {
            state.totalMillis += delta;
            if (now - state.lastActivityAt >= afkTimeoutMillis) {
                state.afkMillis += delta;
            }
            state.lastSampleAt = now;
            state.lastSeenAt = now;
        }
    }

    private void load() throws IOException {
        if (!Files.isRegularFile(file)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(PREFIX) || !key.endsWith(".name")) continue;
            String playerKey = key.substring(PREFIX.length(), key.length() - ".name".length());
            State state = new State();
            state.key = playerKey;
            state.name = safeName(properties.getProperty(key));
            state.totalMillis = parseLong(properties.getProperty(PREFIX + playerKey + ".total"));
            state.afkMillis = parseLong(properties.getProperty(PREFIX + playerKey + ".afk"));
            state.online = false;
            state.lastSampleAt = 0;
            state.lastActivityAt = 0;
            state.lastSeenAt = 0;
            state.sessionStartedAt = 0;
            states.put(playerKey, state);
        }
    }

    private void saveQuietly() {
        try {
            save();
        } catch (IOException ignored) {
            // 下一个采样周期会再次尝试。
        }
    }

    private static String key(String uuid, String name) {
        String value = uuid == null ? "" : uuid.replace("-", "").trim().toLowerCase(Locale.ROOT);
        if (!value.isEmpty()) return value;
        return "name:" + safeName(name).toLowerCase(Locale.ROOT);
    }

    private static String safeName(String name) {
        String value = name == null ? "Player" : name.trim();
        if (value.isEmpty()) value = "Player";
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private static String iso(long millis) {
        return millis <= 0 ? null : Instant.ofEpochMilli(millis).toString();
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private static final class State {
        String key;
        String name;
        long totalMillis;
        long afkMillis;
        long sessionStartedAt;
        long lastSampleAt;
        long lastActivityAt;
        long lastSeenAt;
        boolean online;
    }
}

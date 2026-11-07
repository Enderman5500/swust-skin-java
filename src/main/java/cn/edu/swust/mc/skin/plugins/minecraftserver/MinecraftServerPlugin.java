package cn.edu.swust.mc.skin.plugins.minecraftserver;

import cn.edu.swust.mc.skin.AuthUser;
import cn.edu.swust.mc.skin.Authz;
import cn.edu.swust.mc.skin.Security;
import cn.edu.swust.mc.skin.Web;
import cn.edu.swust.mc.skin.plugin.PluginContext;
import cn.edu.swust.mc.skin.plugin.PluginRequest;
import cn.edu.swust.mc.skin.plugin.SkinPlugin;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MinecraftServerPlugin implements SkinPlugin {
    private static final int MAX_ONLINE = 200;
    private static final int MAX_PLAYERS = 1000;
    private static final long ONLINE_WINDOW_MS = 90_000L;

    private PluginContext context;
    private Connection db;

    @Override
    public void activate(PluginContext context) throws Exception {
        this.context = context;
        Path file = context.dataDir().resolve("minecraft-server.db");
        Class.forName("org.sqlite.JDBC");
        db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        initSchema();

        context.registerRoute("GET", "/status", this::status);
        context.registerRoute("GET", "/me", this::me);
        context.registerRoute("POST", "/report", this::report);
        context.registerAdminRoute("GET", "/admin/overview", this::adminOverview);
        context.registerAdminRoute("POST", "/admin/server", this::adminSaveServer);
        context.registerAdminRoute("POST", "/admin/server/delete", this::adminDeleteServer);
        context.info("Minecraft 服务器统计已启动");
    }

    @Override
    public void deactivate() throws Exception {
        if (db != null) db.close();
        db = null;
    }

    private void initSchema() throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS servers (
                      id TEXT PRIMARY KEY,
                      name TEXT NOT NULL,
                      host TEXT NOT NULL,
                      port INTEGER NOT NULL,
                      description TEXT NOT NULL DEFAULT '',
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS server_status (
                      server_id TEXT PRIMARY KEY,
                      online_count INTEGER NOT NULL DEFAULT 0,
                      max_players INTEGER NOT NULL DEFAULT 0,
                      version TEXT NOT NULL DEFAULT '',
                      loader TEXT NOT NULL DEFAULT '',
                      observed_at TEXT NOT NULL,
                      received_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS online_players (
                      server_id TEXT NOT NULL,
                      uuid TEXT NOT NULL,
                      name TEXT NOT NULL,
                      afk INTEGER NOT NULL DEFAULT 0,
                      session_started_at TEXT NOT NULL,
                      last_seen_at TEXT NOT NULL,
                      PRIMARY KEY (server_id, uuid)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                      server_id TEXT NOT NULL,
                      uuid TEXT NOT NULL,
                      name TEXT NOT NULL,
                      site_user_id TEXT,
                      site_player_id TEXT,
                      site_player_name TEXT,
                      total_millis INTEGER NOT NULL DEFAULT 0,
                      afk_millis INTEGER NOT NULL DEFAULT 0,
                      last_seen_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL,
                      PRIMARY KEY (server_id, uuid)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS report_log (
                      server_id TEXT NOT NULL,
                      sequence INTEGER NOT NULL,
                      observed_at TEXT NOT NULL,
                      received_at TEXT NOT NULL,
                      PRIMARY KEY (server_id, sequence)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_mc_stats_user ON player_stats(site_user_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_mc_stats_total ON player_stats(total_millis DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_mc_online_server ON online_players(server_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_mc_reports_time ON report_log(received_at)");
        }
    }

    private synchronized Object status(PluginRequest request) throws Exception {
        List<Map<String, Object>> servers = new ArrayList<>();
        try (PreparedStatement statement = db.prepareStatement("""
                SELECT s.*, st.online_count, st.max_players, st.version, st.loader,
                       st.observed_at, st.received_at
                FROM servers s LEFT JOIN server_status st ON st.server_id = s.id
                ORDER BY s.created_at ASC
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) servers.add(publicServer(result));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("servers", servers);
        data.put("checkedAt", Security.isoNow());
        return data;
    }

    private synchronized Object me(PluginRequest request) throws Exception {
        AuthUser user = request.user();
        if (user == null) throw Web.unauthorized("请先登录");
        List<Map<String, Object>> servers = new ArrayList<>();
        long total = 0;
        long afk = 0;
        try (PreparedStatement statement = db.prepareStatement("""
                SELECT s.id, s.name,
                       COALESCE(SUM(p.total_millis), 0) AS total_millis,
                       COALESCE(SUM(p.afk_millis), 0) AS afk_millis,
                       MAX(p.last_seen_at) AS last_seen_at
                FROM player_stats p JOIN servers s ON s.id = p.server_id
                WHERE p.site_user_id = ?
                GROUP BY s.id, s.name
                ORDER BY s.name ASC
                """)) {
            statement.setString(1, user.id);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long serverTotal = result.getLong("total_millis");
                    long serverAfk = Math.min(result.getLong("afk_millis"), serverTotal);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", result.getString("id"));
                    item.put("name", result.getString("name"));
                    item.put("totalMillis", serverTotal);
                    item.put("afkMillis", serverAfk);
                    item.put("activeMillis", serverTotal - serverAfk);
                    item.put("lastSeenAt", result.getString("last_seen_at"));
                    item.put("online", isUserOnline(user.id, result.getString("id")));
                    servers.add(item);
                    total += serverTotal;
                    afk += serverAfk;
                }
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("servers", servers);
        data.put("totalMillis", total);
        data.put("afkMillis", afk);
        data.put("activeMillis", total - afk);
        return data;
    }

    private synchronized Object report(PluginRequest request) throws Exception {
        if (!request.apiKey()) throw Web.unauthorized("请使用 API 密钥上报");
        AuthUser user = request.user();
        if (user == null) throw Web.unauthorized("API 密钥无效");
        Map<String, Object> body = request.json();
        String serverId = Authz.str(body.get("serverId")).trim();
        if (serverId.isEmpty()) throw Web.badRequest("缺少服务器 ID");
        long sequence = parseLong(body.get("sequence"), 0);
        if (sequence <= 0) throw Web.badRequest("sequence 必须为正整数");
        Map<String, Object> server = serverRow(serverId);
        if (server == null) throw Web.notFound("服务器不存在");

        List<Map<String, Object>> online = mapList(body.get("online"), MAX_ONLINE);
        List<Map<String, Object>> players = mapList(body.get("players"), MAX_PLAYERS);
        Map<String, Object> meta = map(body.get("meta"));
        String observedAt = isoTime(body.get("observedAt"));
        String now = Security.isoNow();

        db.setAutoCommit(false);
        try {
            if (!recordReport(serverId, sequence, observedAt, now)) {
                db.rollback();
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("accepted", 0);
                data.put("duplicate", true);
                data.put("serverTime", now);
                return data;
            }

            try (PreparedStatement clear = db.prepareStatement("DELETE FROM online_players WHERE server_id = ?")) {
                clear.setString(1, serverId);
                clear.executeUpdate();
            }
            int accepted = 0;
            try (PreparedStatement insert = db.prepareStatement("""
                    INSERT INTO online_players
                    (server_id, uuid, name, afk, session_started_at, last_seen_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                for (Map<String, Object> player : online) {
                    String name = Security.sanitize(Authz.str(player.get("name")), 64);
                    if (name.isEmpty()) continue;
                    String uuid = playerKey(player.get("uuid"), name);
                    insert.setString(1, serverId);
                    insert.setString(2, uuid);
                    insert.setString(3, name);
                    insert.setInt(4, Authz.bool(player.get("afk")) ? 1 : 0);
                    insert.setString(5, isoTime(player.get("sessionStartedAt"), observedAt));
                    insert.setString(6, observedAt);
                    insert.executeUpdate();
                    accepted++;
                }
            }

            try (PreparedStatement upsert = db.prepareStatement("""
                    INSERT INTO player_stats
                    (server_id, uuid, name, site_user_id, site_player_id, site_player_name,
                     total_millis, afk_millis, last_seen_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(server_id, uuid) DO UPDATE SET
                      name = excluded.name,
                      site_user_id = COALESCE(excluded.site_user_id, player_stats.site_user_id),
                      site_player_id = COALESCE(excluded.site_player_id, player_stats.site_player_id),
                      site_player_name = COALESCE(excluded.site_player_name, player_stats.site_player_name),
                      total_millis = MAX(player_stats.total_millis, excluded.total_millis),
                      afk_millis = MIN(
                        MAX(player_stats.afk_millis, excluded.afk_millis),
                        MAX(player_stats.total_millis, excluded.total_millis)
                      ),
                      last_seen_at = excluded.last_seen_at,
                      updated_at = excluded.updated_at
                    """)) {
                for (Map<String, Object> player : players) {
                    String name = Security.sanitize(Authz.str(player.get("name")), 64);
                    if (name.isEmpty()) continue;
                    String uuid = playerKey(player.get("uuid"), name);
                    long total = Math.max(0, parseLong(player.get("totalMillis"), 0));
                    long afk = Math.min(total, Math.max(0, parseLong(player.get("afkMillis"), 0)));
                    Map<String, Object> sitePlayer = context.findPlayer(uuid, name);
                    String siteUserId = null;
                    String sitePlayerId = null;
                    String sitePlayerName = null;
                    if (sitePlayer != null) {
                        sitePlayerId = Authz.str(sitePlayer.get("id"));
                        sitePlayerName = Authz.str(sitePlayer.get("name"));
                        Map<String, Object> owner = map(sitePlayer.get("owner"));
                        if (owner != null) siteUserId = Authz.str(owner.get("id"));
                    }
                    upsert.setString(1, serverId);
                    upsert.setString(2, uuid);
                    upsert.setString(3, name);
                    upsert.setString(4, siteUserId);
                    upsert.setString(5, sitePlayerId);
                    upsert.setString(6, sitePlayerName);
                    upsert.setLong(7, total);
                    upsert.setLong(8, afk);
                    upsert.setString(9, isoTime(player.get("lastSeenAt"), observedAt));
                    upsert.setString(10, now);
                    upsert.executeUpdate();
                }
            }

            int maxPlayers = Math.max(0, (int) parseLong(meta.get("maxPlayers"), 0));
            try (PreparedStatement upsert = db.prepareStatement("""
                    INSERT INTO server_status
                    (server_id, online_count, max_players, version, loader, observed_at, received_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(server_id) DO UPDATE SET
                      online_count = excluded.online_count,
                      max_players = excluded.max_players,
                      version = excluded.version,
                      loader = excluded.loader,
                      observed_at = excluded.observed_at,
                      received_at = excluded.received_at
                    """)) {
                upsert.setString(1, serverId);
                upsert.setInt(2, accepted);
                upsert.setInt(3, maxPlayers);
                upsert.setString(4, Security.sanitize(Authz.str(meta.get("version")), 40));
                upsert.setString(5, Security.sanitize(Authz.str(meta.get("loader")), 40));
                upsert.setString(6, observedAt);
                upsert.setString(7, now);
                upsert.executeUpdate();
            }
            db.commit();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("accepted", accepted);
            data.put("duplicate", false);
            data.put("online", accepted);
            data.put("serverTime", now);
            return data;
        } catch (Exception e) {
            db.rollback();
            throw e;
        } finally {
            db.setAutoCommit(true);
        }
    }

    private synchronized Object adminOverview(PluginRequest request) throws Exception {
        List<Map<String, Object>> servers = new ArrayList<>();
        try (PreparedStatement statement = db.prepareStatement("""
                SELECT s.*, st.online_count, st.max_players, st.version, st.loader,
                       st.observed_at, st.received_at
                FROM servers s LEFT JOIN server_status st ON st.server_id = s.id
                ORDER BY s.created_at ASC
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Map<String, Object> server = publicServer(result);
                server.put("players", onlinePlayers(result.getString("id")));
                servers.add(server);
            }
        }

        List<Map<String, Object>> players = new ArrayList<>();
        long totalMillis = 0;
        long afkMillis = 0;
        try (PreparedStatement statement = db.prepareStatement("""
                SELECT p.*, s.name AS server_name
                FROM player_stats p JOIN servers s ON s.id = p.server_id
                ORDER BY p.total_millis DESC, p.last_seen_at DESC
                LIMIT 1000
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                long total = result.getLong("total_millis");
                long afk = Math.min(result.getLong("afk_millis"), total);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("serverId", result.getString("server_id"));
                item.put("serverName", result.getString("server_name"));
                item.put("uuid", result.getString("uuid"));
                item.put("name", result.getString("name"));
                item.put("siteUserId", result.getString("site_user_id"));
                item.put("sitePlayerId", result.getString("site_player_id"));
                item.put("sitePlayerName", result.getString("site_player_name"));
                item.put("totalMillis", total);
                item.put("afkMillis", afk);
                item.put("activeMillis", total - afk);
                item.put("lastSeenAt", result.getString("last_seen_at"));
                players.add(item);
                totalMillis += total;
                afkMillis += afk;
            }
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("servers", servers.size());
        totals.put("onlineServers", servers.stream().filter(s -> Boolean.TRUE.equals(s.get("online"))).count());
        totals.put("onlinePlayers", servers.stream().mapToInt(s -> Authz.num(s.get("onlineCount"))).sum());
        totals.put("totalMillis", totalMillis);
        totals.put("afkMillis", afkMillis);
        totals.put("activeMillis", totalMillis - afkMillis);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("servers", servers);
        data.put("players", players);
        data.put("totals", totals);
        data.put("checkedAt", Security.isoNow());
        return data;
    }

    private synchronized Object adminSaveServer(PluginRequest request) throws Exception {
        Map<String, Object> body = request.json();
        String id = Authz.str(body.get("id")).trim();
        String name = Security.sanitize(Authz.str(body.get("name")), 60);
        String host = Security.sanitize(Authz.str(body.get("host")), 255);
        String description = Security.sanitize(Authz.str(body.get("description")), 300);
        int port = (int) parseLong(body.get("port"), 25565);
        if (name.isEmpty()) throw Web.badRequest("请填写服务器名称");
        if (host.isEmpty() || host.contains(" ")) throw Web.badRequest("请填写有效的服务器地址");
        if (port < 1 || port > 65535) throw Web.badRequest("端口需为 1–65535");
        String now = Security.isoNow();
        if (id.isEmpty()) {
            do {
                id = "srv_" + Security.randomHex(4);
            } while (serverRow(id) != null);
            try (PreparedStatement insert = db.prepareStatement("""
                    INSERT INTO servers (id, name, host, port, description, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """)) {
                insert.setString(1, id);
                insert.setString(2, name);
                insert.setString(3, host);
                insert.setInt(4, port);
                insert.setString(5, description);
                insert.setString(6, now);
                insert.setString(7, now);
                insert.executeUpdate();
            }
        } else {
            if (serverRow(id) == null) throw Web.notFound("服务器不存在");
            try (PreparedStatement update = db.prepareStatement("""
                    UPDATE servers SET name = ?, host = ?, port = ?, description = ?, updated_at = ?
                    WHERE id = ?
                    """)) {
                update.setString(1, name);
                update.setString(2, host);
                update.setInt(3, port);
                update.setString(4, description);
                update.setString(5, now);
                update.setString(6, id);
                update.executeUpdate();
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("server", serverRow(id));
        return data;
    }

    private synchronized Object adminDeleteServer(PluginRequest request) throws Exception {
        String id = Authz.str(request.json().get("id")).trim();
        if (id.isEmpty()) throw Web.badRequest("缺少服务器 ID");
        if (serverRow(id) == null) throw Web.notFound("服务器不存在");
        db.setAutoCommit(false);
        try {
            for (String table : List.of("report_log", "online_players", "player_stats", "server_status", "servers")) {
                try (PreparedStatement delete = db.prepareStatement(
                        "DELETE FROM " + table + " WHERE " + ("servers".equals(table) ? "id" : "server_id") + " = ?")) {
                    delete.setString(1, id);
                    delete.executeUpdate();
                }
            }
            db.commit();
        } catch (Exception e) {
            db.rollback();
            throw e;
        } finally {
            db.setAutoCommit(true);
        }
        return Map.of("deleted", true);
    }

    private boolean recordReport(String serverId, long sequence, String observedAt, String receivedAt)
            throws SQLException {
        try (PreparedStatement insert = db.prepareStatement("""
                INSERT OR IGNORE INTO report_log (server_id, sequence, observed_at, received_at)
                VALUES (?, ?, ?, ?)
                """)) {
            insert.setString(1, serverId);
            insert.setLong(2, sequence);
            insert.setString(3, observedAt);
            insert.setString(4, receivedAt);
            return insert.executeUpdate() > 0;
        }
    }

    private boolean isUserOnline(String userId, String serverId) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement("""
                SELECT 1 FROM online_players o
                JOIN player_stats p ON p.server_id = o.server_id AND p.uuid = o.uuid
                WHERE p.site_user_id = ? AND o.server_id = ?
                LIMIT 1
                """)) {
            statement.setString(1, userId);
            statement.setString(2, serverId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private List<Map<String, Object>> onlinePlayers(String serverId) throws SQLException {
        List<Map<String, Object>> players = new ArrayList<>();
        try (PreparedStatement statement = db.prepareStatement("""
                SELECT * FROM online_players WHERE server_id = ? ORDER BY name ASC
                """)) {
            statement.setString(1, serverId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Object> player = new LinkedHashMap<>();
                    player.put("uuid", result.getString("uuid"));
                    player.put("name", result.getString("name"));
                    player.put("afk", result.getInt("afk") != 0);
                    player.put("sessionStartedAt", result.getString("session_started_at"));
                    player.put("lastSeenAt", result.getString("last_seen_at"));
                    players.add(player);
                }
            }
        }
        return players;
    }

    private Map<String, Object> publicServer(ResultSet result) throws SQLException {
        Map<String, Object> server = serverMapFrom(result);
        String receivedAt = result.getString("received_at");
        boolean online = isRecent(receivedAt);
        server.put("online", online);
        server.put("onlineCount", online ? result.getInt("online_count") : 0);
        server.put("maxPlayers", result.getInt("max_players"));
        server.put("version", result.getString("version"));
        server.put("loader", result.getString("loader"));
        server.put("lastSeenAt", receivedAt);
        return server;
    }

    private Map<String, Object> serverMapFrom(ResultSet result) throws SQLException {
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("id", result.getString("id"));
        server.put("name", result.getString("name"));
        server.put("host", result.getString("host"));
        server.put("port", result.getInt("port"));
        server.put("description", result.getString("description"));
        server.put("createdAt", result.getString("created_at"));
        server.put("updatedAt", result.getString("updated_at"));
        server.put("address", result.getString("host") + ":" + result.getInt("port"));
        return server;
    }

    private Map<String, Object> serverRow(String id) {
        try (PreparedStatement statement = db.prepareStatement("SELECT * FROM servers WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? serverMapFrom(result) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("读取服务器失败", e);
        }
    }

    private static boolean isRecent(String iso) {
        if (iso == null || iso.isBlank()) return false;
        try {
            return Duration.between(Instant.parse(iso), Instant.now()).toMillis() <= ONLINE_WINDOW_MS;
        } catch (Exception e) {
            return false;
        }
    }

    private static String isoTime(Object value) {
        return isoTime(value, Security.isoNow());
    }

    private static String isoTime(Object value, String fallback) {
        String text = Authz.str(value).trim();
        try {
            return Instant.parse(text).toString();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String playerKey(Object uuid, String name) {
        String normalized = Security.stripUuid(Authz.str(uuid));
        if (normalized.matches("^[0-9a-f]{32}$")) return normalized;
        return "name:" + name.toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static List<Map<String, Object>> mapList(Object value, int limit) {
        if (!(value instanceof List<?> raw)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            Map<String, Object> map = map(item);
            if (map != null) result.add(map);
            if (result.size() >= limit) break;
        }
        return result;
    }

    private static long parseLong(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(Authz.str(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

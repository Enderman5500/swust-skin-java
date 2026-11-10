package cn.edu.swust.mc.skin.plugins.launcherdomain;

import cn.edu.swust.mc.skin.Authz;
import cn.edu.swust.mc.skin.Json;
import cn.edu.swust.mc.skin.Security;
import cn.edu.swust.mc.skin.Web;
import cn.edu.swust.mc.skin.plugin.PluginContext;
import cn.edu.swust.mc.skin.plugin.PluginRequest;
import cn.edu.swust.mc.skin.plugin.SkinPlugin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 面向 SWUSTMCL 的“站点型域”：域资料、公告、官方服务器与资源清单。
 *
 * 一个皮肤站只维护一个域（固定 id=default）。服务器可以关联
 * minecraft-server 插件中的服务器，用于在网页清单中显示心跳在线人数；
 * 启动器自身仍通过 SLP 实时探测。
 */
public final class LauncherDomainPlugin implements SkinPlugin {
    private static final String DOMAIN_ID = "default";
    private static final Set<String> RESOURCE_TYPES = Set.of("modpack", "resourcepack", "shaderpack", "other");
    private static final long ONLINE_WINDOW_SECONDS = 90;
    private static final String CURSEFORGE_API = "https://api.curseforge.com/v1";
    private static final Pattern CURSEFORGE_PATH = Pattern.compile(
            "^/mods/(search|\\d+|\\d+/files|\\d+/files/\\d+)$");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private PluginContext context;
    private Connection db;

    @Override
    public void activate(PluginContext context) throws Exception {
        this.context = context;
        Files.createDirectories(context.dataDir());
        Class.forName("org.sqlite.JDBC");
        db = DriverManager.getConnection("jdbc:sqlite:" + context.dataDir().resolve("launcher-domain.db").toAbsolutePath());
        initSchema();
        ensureDomain();
        importMinecraftServers();
        context.registerRoute("GET", "/manifest", this::manifest);
        context.registerRoute("GET", "/curseforge", this::curseforgeRelay);
        context.registerAdminRoute("GET", "/admin/overview", this::adminOverview);
        context.registerAdminRoute("POST", "/admin/domain", this::adminSaveDomain);
        context.registerAdminRoute("POST", "/admin/server", this::adminSaveServer);
        context.registerAdminRoute("POST", "/admin/server/delete", this::adminDeleteServer);
        context.registerAdminRoute("POST", "/admin/resource", this::adminSaveResource);
        context.registerAdminRoute("POST", "/admin/resource/delete", this::adminDeleteResource);
        context.info("启动器域插件已就绪");
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
                    CREATE TABLE IF NOT EXISTS domain (
                      id TEXT PRIMARY KEY,
                      name TEXT NOT NULL,
                      description TEXT NOT NULL DEFAULT '',
                      icon TEXT NOT NULL DEFAULT '',
                      site_url TEXT NOT NULL DEFAULT '',
                      yggdrasil_url TEXT NOT NULL DEFAULT '',
                      curseforge_api_key TEXT NOT NULL DEFAULT '',
                      announcement TEXT NOT NULL DEFAULT '',
                      announcement_updated_at TEXT,
                      updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS servers (
                      id TEXT PRIMARY KEY,
                      name TEXT NOT NULL,
                      host TEXT NOT NULL,
                      port INTEGER NOT NULL,
                      note TEXT NOT NULL DEFAULT '',
                      icon TEXT NOT NULL DEFAULT '',
                      sort_order INTEGER NOT NULL DEFAULT 0,
                      visible INTEGER NOT NULL DEFAULT 1,
                      minecraft_server_id TEXT,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS resources (
                      id TEXT PRIMARY KEY,
                      name TEXT NOT NULL,
                      description TEXT NOT NULL DEFAULT '',
                      type TEXT NOT NULL DEFAULT 'other',
                      url TEXT NOT NULL,
                      sha256 TEXT NOT NULL DEFAULT '',
                      size INTEGER NOT NULL DEFAULT 0,
                      version TEXT NOT NULL DEFAULT '',
                      icon TEXT NOT NULL DEFAULT '',
                      sort_order INTEGER NOT NULL DEFAULT 0,
                      visible INTEGER NOT NULL DEFAULT 1,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL
                    )
                    """);
        }
        ensureColumn("domain", "curseforge_api_key", "TEXT NOT NULL DEFAULT ''");
    }

    private void ensureColumn(String table, String column, String definition) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException e) {
            if (!String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT).contains("duplicate column")) {
                throw e;
            }
        }
    }

    private void ensureDomain() throws SQLException {
        Map<String, Object> row = one("SELECT * FROM domain WHERE id = ?", DOMAIN_ID);
        if (row != null) return;
        Map<String, Object> settings = context.settings();
        String name = text(settings.get("site_name"), 60);
        if (name.isEmpty()) name = "社团域";
        String description = text(settings.get("tagline"), 300);
        String icon = "/assets/brand/badge.png";
        String now = Security.isoNow();
        update("""
                INSERT INTO domain
                (id, name, description, icon, site_url, yggdrasil_url, announcement,
                 announcement_updated_at, updated_at)
                VALUES (?, ?, ?, ?, '', '/api/yggdrasil', '', NULL, ?)
                """, DOMAIN_ID, name, description, icon, now);
    }

    /**
     * 首次启用时把 minecraft-server 插件里已有的服务器导入域清单，
     * 保留关联 ID，网页清单可直接显示这些服务器的心跳在线人数。
     */
    private void importMinecraftServers() {
        try {
            Map<String, Object> count = one("SELECT COUNT(*) AS count FROM servers");
            if (count != null && Authz.num(count.get("count")) > 0) return;
        } catch (SQLException e) {
            return;
        }
        Path source = context.dataDir().getParent().resolve("minecraft-server").resolve("minecraft-server.db");
        if (!Files.isRegularFile(source)) return;
        String url = "jdbc:sqlite:file:" + source.toAbsolutePath().toString().replace('\\', '/') + "?mode=ro";
        String now = Security.isoNow();
        try (Connection sourceDb = DriverManager.getConnection(url);
             PreparedStatement statement = sourceDb.prepareStatement(
                     "SELECT id, name, host, port, description, visible, sort_order FROM servers ORDER BY sort_order, name");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String id = "srv_" + Security.randomHex(6);
                update("""
                        INSERT OR IGNORE INTO servers
                        (id, name, host, port, note, icon, sort_order, visible, minecraft_server_id,
                         created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, '', ?, ?, ?, ?, ?)
                        """, id, rs.getString("name"), rs.getString("host"), rs.getInt("port"),
                        text(rs.getString("description"), 200), rs.getInt("sort_order"),
                        rs.getInt("visible") != 0 ? 1 : 0, rs.getString("id"), now, now);
            }
        } catch (Exception e) {
            context.warn("导入 minecraft-server 服务器失败：" + e.getMessage());
        }
    }

    private Map<String, Object> manifest(PluginRequest request) throws Exception {
        Map<String, Object> domain = one("SELECT * FROM domain WHERE id = ?", DOMAIN_ID);
        if (domain == null) throw Web.notFound("域尚未初始化");
        List<Map<String, Object>> servers = all("""
                SELECT * FROM servers WHERE visible = 1 ORDER BY sort_order, name
                """);
        for (Map<String, Object> server : servers) {
            attachStatus(server);
        }
        List<Map<String, Object>> resources = all("""
                SELECT * FROM resources WHERE visible = 1 ORDER BY sort_order, name
                """);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", 1);
        out.put("id", "swustmc");
        out.put("name", text(domain.get("name"), 60));
        out.put("description", text(domain.get("description"), 300));
        out.put("icon", emptyToNull(text(domain.get("icon"), 500)));
        out.put("siteUrl", emptyToNull(text(domain.get("site_url"), 500)));
        String yggdrasil = text(domain.get("yggdrasil_url"), 500);
        out.put("yggdrasilUrl", emptyToNull(yggdrasil));
        out.put("announcement", announcement(domain));
        out.put("servers", servers.stream().map(this::publicServer).toList());
        out.put("resources", resources.stream().map(this::publicResource).toList());
        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("yggdrasilUrl", emptyToNull(yggdrasil));
        auth.put("loginUrl", "/api/auth/login");
        auth.put("playersUrl", "/api/players");
        out.put("auth", auth);
        Map<String, Object> curseforge = new LinkedHashMap<>();
        curseforge.put("relayUrl", "/api/plugins/launcher-domain/curseforge");
        curseforge.put("configured", !curseforgeKey().isEmpty());
        out.put("curseforge", curseforge);
        out.put("updatedAt", Authz.str(domain.get("updated_at")));
        return out;
    }

    private Map<String, Object> adminOverview(PluginRequest request) throws Exception {
        Map<String, Object> domain = one("SELECT * FROM domain WHERE id = ?", DOMAIN_ID);
        if (domain == null) throw Web.notFound("域尚未初始化");
        String source = curseforgeSource(domain);
        domain.remove("curseforge_api_key");
        domain.put("curseforge_configured", !source.isEmpty());
        domain.put("curseforge_source", source);
        List<Map<String, Object>> servers = all("SELECT * FROM servers ORDER BY sort_order, name");
        for (Map<String, Object> server : servers) attachStatus(server);
        List<Map<String, Object>> resources = all("SELECT * FROM resources ORDER BY sort_order, name");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("domain", domain);
        out.put("servers", servers);
        out.put("resources", resources);
        return out;
    }

    private Map<String, Object> adminSaveDomain(PluginRequest request) throws Exception {
        Map<String, Object> body = request.json();
        String name = text(body.get("name"), 60);
        if (name.isEmpty()) throw Web.badRequest("域名称不能为空");
        String description = text(body.get("description"), 300);
        String icon = urlOrPath(body.get("icon"), "图标地址");
        String siteUrl = urlOrPath(body.get("siteUrl"), "站点地址");
        String yggdrasilUrl = urlOrPath(body.get("yggdrasilUrl"), "认证地址");
        String announcement = text(body.get("announcement"), 2000);
        String curseforgeApiKey = text(body.get("curseforgeApiKey"), 200);
        boolean clearCurseforgeApiKey = Authz.bool(body.get("clearCurseforgeApiKey"));
        String now = Security.isoNow();
        String previousAnnouncement = "";
        String previousAnnouncementAt = null;
        Map<String, Object> current = one("SELECT announcement, announcement_updated_at FROM domain WHERE id = ?", DOMAIN_ID);
        if (current != null) {
            previousAnnouncement = Authz.str(current.get("announcement"));
            previousAnnouncementAt = Authz.str(current.get("announcement_updated_at"));
        }
        boolean announcementChanged = !announcement.equals(previousAnnouncement);
        update("""
                UPDATE domain SET name = ?, description = ?, icon = ?, site_url = ?, yggdrasil_url = ?,
                  announcement = ?, announcement_updated_at = ?, updated_at = ? WHERE id = ?
                """, name, description, icon, siteUrl, yggdrasilUrl, announcement,
                announcementChanged ? now : emptyToNull(previousAnnouncementAt), now, DOMAIN_ID);
        if (!curseforgeApiKey.isEmpty()) {
            update("UPDATE domain SET curseforge_api_key = ? WHERE id = ?", curseforgeApiKey, DOMAIN_ID);
        } else if (clearCurseforgeApiKey) {
            update("UPDATE domain SET curseforge_api_key = '' WHERE id = ?", DOMAIN_ID);
        }
        return adminOverview(request);
    }

    private Map<String, Object> adminSaveServer(PluginRequest request) throws Exception {
        Map<String, Object> body = request.json();
        String name = text(body.get("name"), 60);
        String host = text(body.get("host"), 255);
        if (name.isEmpty()) throw Web.badRequest("服务器名称不能为空");
        if (!host.matches("^[A-Za-z0-9._:\\-\\[\\]]{1,255}$")) throw Web.badRequest("服务器地址格式不正确");
        int port = Math.max(1, Math.min(65535, Authz.num(body.get("port")) == 0 ? 25565 : Authz.num(body.get("port"))));
        String note = text(body.get("note"), 200);
        String icon = urlOrPath(body.get("icon"), "图标地址");
        int sortOrder = Math.max(0, Math.min(9999, Authz.num(body.get("sortOrder"))));
        int visible = Authz.bool(body.get("visible")) ? 1 : 0;
        String minecraftServerId = text(body.get("minecraftServerId"), 80);
        String id = text(body.get("id"), 80);
        String now = Security.isoNow();
        if (id.isEmpty()) {
            id = "srv_" + Security.randomHex(6);
            update("""
                    INSERT INTO servers
                    (id, name, host, port, note, icon, sort_order, visible, minecraft_server_id,
                     created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULLIF(?, ''), ?, ?)
                    """, id, name, host, port, note, icon, sortOrder, visible, minecraftServerId, now, now);
        } else {
            int changed = updateCount("""
                    UPDATE servers SET name = ?, host = ?, port = ?, note = ?, icon = ?, sort_order = ?,
                      visible = ?, minecraft_server_id = NULLIF(?, ''), updated_at = ? WHERE id = ?
                    """, name, host, port, note, icon, sortOrder, visible, minecraftServerId, now, id);
            if (changed == 0) throw Web.notFound("服务器不存在");
        }
        return adminOverview(request);
    }

    private Map<String, Object> adminDeleteServer(PluginRequest request) throws Exception {
        String id = text(request.json().get("id"), 80);
        if (id.isEmpty()) throw Web.badRequest("缺少服务器 ID");
        update("DELETE FROM servers WHERE id = ?", id);
        return adminOverview(request);
    }

    private Map<String, Object> adminSaveResource(PluginRequest request) throws Exception {
        Map<String, Object> body = request.json();
        String name = text(body.get("name"), 80);
        if (name.isEmpty()) throw Web.badRequest("资源名称不能为空");
        String type = text(body.get("type"), 20);
        if (!RESOURCE_TYPES.contains(type)) type = "other";
        String url = text(body.get("url"), 1000);
        if (!(url.startsWith("https://") || url.startsWith("http://") || url.startsWith("/"))) {
            throw Web.badRequest("资源地址必须是 http(s) URL 或站内路径");
        }
        String description = text(body.get("description"), 500);
        String sha256 = text(body.get("sha256"), 64).toLowerCase();
        if (!sha256.isEmpty() && !sha256.matches("^[0-9a-f]{64}$")) throw Web.badRequest("SHA-256 格式不正确");
        long size = Math.max(0, Authz.num(body.get("size")));
        String version = text(body.get("version"), 40);
        String icon = urlOrPath(body.get("icon"), "图标地址");
        int sortOrder = Math.max(0, Math.min(9999, Authz.num(body.get("sortOrder"))));
        int visible = Authz.bool(body.get("visible")) ? 1 : 0;
        String id = text(body.get("id"), 80);
        String now = Security.isoNow();
        if (id.isEmpty()) {
            id = "res_" + Security.randomHex(6);
            update("""
                    INSERT INTO resources
                    (id, name, description, type, url, sha256, size, version, icon, sort_order,
                     visible, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, name, description, type, url, sha256, size, version, icon, sortOrder,
                    visible, now, now);
        } else {
            int changed = updateCount("""
                    UPDATE resources SET name = ?, description = ?, type = ?, url = ?, sha256 = ?,
                      size = ?, version = ?, icon = ?, sort_order = ?, visible = ?, updated_at = ?
                    WHERE id = ?
                    """, name, description, type, url, sha256, size, version, icon, sortOrder,
                    visible, now, id);
            if (changed == 0) throw Web.notFound("资源不存在");
        }
        return adminOverview(request);
    }

    private Map<String, Object> adminDeleteResource(PluginRequest request) throws Exception {
        String id = text(request.json().get("id"), 80);
        if (id.isEmpty()) throw Web.badRequest("缺少资源 ID");
        update("DELETE FROM resources WHERE id = ?", id);
        return adminOverview(request);
    }

    /** 站点持有的 CurseForge Key：域管理配置优先，其次读取环境变量。 */
    private String curseforgeKey() throws SQLException {
        Map<String, Object> row = one("SELECT curseforge_api_key FROM domain WHERE id = ?", DOMAIN_ID);
        String key = row == null ? "" : text(row.get("curseforge_api_key"), 200);
        if (key.isEmpty()) key = text(System.getenv("CURSEFORGE_API_KEY"), 200);
        if (key.isEmpty()) key = text(System.getenv("CF_API_KEY"), 200);
        return key;
    }

    private String curseforgeSource(Map<String, Object> domain) {
        String stored = domain == null ? "" : text(domain.get("curseforge_api_key"), 200);
        if (!stored.isEmpty()) return "database";
        if (!text(System.getenv("CURSEFORGE_API_KEY"), 200).isEmpty()) return "environment";
        if (!text(System.getenv("CF_API_KEY"), 200).isEmpty()) return "environment";
        return "";
    }

    /**
     * 启动器域内 CurseForge 中转：只放行读取类接口，Key 始终留在站点，
     * 不返回给启动器，也不允许请求任意上游地址。
     */
    private Object curseforgeRelay(PluginRequest request) throws Exception {
        String path = text(request.query("path"), 200);
        if (!CURSEFORGE_PATH.matcher(path).matches()) throw Web.badRequest("不支持的 CurseForge 接口");
        String key = curseforgeKey();
        if (key.isEmpty()) {
            throw new Web.HttpError(503, "curseforge_not_configured", "站点尚未配置 CurseForge API Key");
        }
        String query = request.query().entrySet().stream()
                .filter(entry -> !"path".equals(entry.getKey()))
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        String url = CURSEFORGE_API + path + (query.isEmpty() ? "" : "?" + query);
        HttpRequest upstream = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("x-api-key", key)
                .header("Accept", "application/json")
                .header("User-Agent", "SWUSTMCL-Domain/1.0")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = HTTP.send(upstream, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new Web.HttpError(504, "curseforge_timeout", "CurseForge 请求超时，请稍后再试");
        }
        int statusCode = response.statusCode();
        if (statusCode != 200) {
            int status = switch (statusCode) {
                case 400 -> 400;
                case 404 -> 404;
                case 429 -> 429;
                default -> 502;
            };
            String message = statusCode == 429
                    ? "CurseForge 请求过于频繁，请稍后再试"
                    : (statusCode == 401 || statusCode == 403)
                    ? "站点的 CurseForge API Key 无效或没有访问权限"
                    : "CurseForge 上游返回 HTTP " + statusCode;
            throw new Web.HttpError(status, "curseforge_error", message);
        }
        try {
            return Json.MAPPER.readValue(response.body(), Object.class);
        } catch (Exception e) {
            throw new Web.HttpError(502, "curseforge_bad_response", "CurseForge 返回了无法解析的数据");
        }
    }

    private Map<String, Object> announcement(Map<String, Object> domain) {
        String text = Authz.str(domain.get("announcement"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("text", text);
        item.put("updatedAt", emptyToNull(Authz.str(domain.get("announcement_updated_at"))));
        return item;
    }

    private Map<String, Object> publicServer(Map<String, Object> server) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Authz.str(server.get("id")));
        item.put("name", Authz.str(server.get("name")));
        item.put("host", Authz.str(server.get("host")));
        item.put("port", Authz.num(server.get("port")));
        item.put("note", Authz.str(server.get("note")));
        item.put("icon", emptyToNull(Authz.str(server.get("icon"))));
        item.put("sortOrder", Authz.num(server.get("sort_order")));
        if (server.get("status") instanceof Map<?, ?> status) {
            item.putAll((Map<String, Object>) status);
        }
        return item;
    }

    private Map<String, Object> publicResource(Map<String, Object> resource) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Authz.str(resource.get("id")));
        item.put("name", Authz.str(resource.get("name")));
        item.put("description", Authz.str(resource.get("description")));
        item.put("type", Authz.str(resource.get("type")));
        item.put("url", Authz.str(resource.get("url")));
        item.put("sha256", emptyToNull(Authz.str(resource.get("sha256"))));
        item.put("size", Authz.num(resource.get("size")));
        item.put("version", Authz.str(resource.get("version")));
        item.put("icon", emptyToNull(Authz.str(resource.get("icon"))));
        item.put("sortOrder", Authz.num(resource.get("sort_order")));
        return item;
    }

    private void attachStatus(Map<String, Object> server) {
        String linkedId = Authz.str(server.get("minecraft_server_id"));
        if (linkedId.isEmpty()) return;
        Map<String, Object> status = linkedStatus(linkedId);
        if (status != null) server.put("status", status);
    }

    private Map<String, Object> linkedStatus(String serverId) {
        Path source = context.dataDir().getParent().resolve("minecraft-server").resolve("minecraft-server.db");
        if (!Files.isRegularFile(source)) return null;
        String url = "jdbc:sqlite:file:" + source.toAbsolutePath().toString().replace('\\', '/') + "?mode=ro";
        try (Connection sourceDb = DriverManager.getConnection(url);
             PreparedStatement statement = sourceDb.prepareStatement(
                     "SELECT online_count, max_players, version, observed_at FROM server_status WHERE server_id = ?")) {
            statement.setString(1, serverId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                String observedAt = rs.getString("observed_at");
                boolean online = false;
                try {
                    online = observedAt != null
                            && Duration.between(Instant.parse(observedAt), Instant.now()).getSeconds() <= ONLINE_WINDOW_SECONDS;
                } catch (Exception ignored) {
                }
                Map<String, Object> status = new LinkedHashMap<>();
                status.put("online", online);
                status.put("onlineCount", online ? rs.getInt("online_count") : 0);
                status.put("maxPlayers", rs.getInt("max_players"));
                status.put("version", rs.getString("version"));
                status.put("lastSeenAt", observedAt);
                return status;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> one(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> rows = all(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> all(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
            try (ResultSet rs = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                var meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private void update(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
            statement.executeUpdate();
        }
    }

    private int updateCount(String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
            return statement.executeUpdate();
        }
    }

    private static String text(Object value, int max) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String urlOrPath(Object value, String label) {
        String text = text(value, 1000);
        if (text.isEmpty()) return "";
        if (text.startsWith("/") || text.startsWith("https://") || text.startsWith("http://")) return text;
        throw Web.badRequest(label + "必须是 http(s) URL 或站内路径");
    }
}

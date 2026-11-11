package cn.edu.swust.mc.skin.plugins.launcherdomain;

import cn.edu.swust.mc.skin.Authz;
import cn.edu.swust.mc.skin.Json;
import cn.edu.swust.mc.skin.Security;
import cn.edu.swust.mc.skin.Web;
import cn.edu.swust.mc.skin.plugin.PluginContext;
import cn.edu.swust.mc.skin.plugin.PluginRequest;
import cn.edu.swust.mc.skin.plugin.PluginResponse;
import cn.edu.swust.mc.skin.plugin.SkinPlugin;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final int MAX_PACK_FILES = 50_000;
    private static final long MAX_PACK_BYTES = 20L * 1024 * 1024 * 1024;
    private static final long MAX_BLOB_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int UPLOAD_CHUNK = 8 * 1024 * 1024;
    private static final Set<String> POLICIES = Set.of("required", "optional");
    private static final Set<Integer> MIN_JAVA_VALUES = Set.of(0, 8, 11, 16, 17, 21, 25, 26);
    private static final ConcurrentHashMap<String, Object> BLOB_LOCKS = new ConcurrentHashMap<>();
    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
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
        Files.createDirectories(context.dataDir().resolve("packs").resolve("blobs"));
        Files.createDirectories(context.dataDir().resolve("packs").resolve("tmp"));
        Class.forName("org.sqlite.JDBC");
        db = DriverManager.getConnection("jdbc:sqlite:" + context.dataDir().resolve("launcher-domain.db").toAbsolutePath());
        initSchema();
        ensureDomain();
        importMinecraftServers();
        context.registerRoute("GET", "/manifest", this::manifest);
        context.registerRoute("GET", "/curseforge", this::curseforgeRelay);
        context.registerRoute("GET", "/pack", this::publicPack);
        context.registerRoute("GET", "/blob", this::publicBlob);
        context.registerRoute("POST", "/admin/pack/prepare", this::adminPreparePack);
        context.registerRoute("PUT", "/admin/pack/blob", this::adminUploadBlob);
        context.registerRoute("POST", "/admin/pack/commit", this::adminCommitPack);
        context.registerAdminRoute("GET", "/admin/packs", this::adminPacks);
        context.registerAdminRoute("GET", "/admin/pack/versions", this::adminPackVersions);
        context.registerAdminRoute("POST", "/admin/pack/update", this::adminUpdatePack);
        context.registerAdminRoute("GET", "/admin/pack/tokens", this::adminTokens);
        context.registerAdminRoute("POST", "/admin/pack/token", this::adminCreateToken);
        context.registerAdminRoute("POST", "/admin/pack/token/delete", this::adminDeleteToken);
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
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS packs (
                      id TEXT PRIMARY KEY,
                      name TEXT NOT NULL,
                      description TEXT NOT NULL DEFAULT '',
                      icon TEXT NOT NULL DEFAULT '',
                      server_id TEXT NOT NULL DEFAULT '',
                      policy TEXT NOT NULL DEFAULT 'optional',
                      min_java INTEGER NOT NULL DEFAULT 0,
                      visible INTEGER NOT NULL DEFAULT 1,
                      latest_version INTEGER NOT NULL DEFAULT 0,
                      created_at TEXT NOT NULL,
                      updated_at TEXT NOT NULL,
                      created_by TEXT NOT NULL DEFAULT ''
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS pack_versions (
                      id TEXT PRIMARY KEY,
                      pack_id TEXT NOT NULL,
                      version_no INTEGER NOT NULL,
                      manifest_json TEXT NOT NULL,
                      instance_json TEXT NOT NULL DEFAULT '{}',
                      file_count INTEGER NOT NULL DEFAULT 0,
                      total_size INTEGER NOT NULL DEFAULT 0,
                      notes TEXT NOT NULL DEFAULT '',
                      created_at TEXT NOT NULL,
                      created_by TEXT NOT NULL DEFAULT '',
                      UNIQUE(pack_id, version_no)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_pack_versions_pack ON pack_versions(pack_id, version_no DESC)");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS publish_tokens (
                      id TEXT PRIMARY KEY,
                      name TEXT NOT NULL,
                      token_hash TEXT NOT NULL,
                      prefix TEXT NOT NULL,
                      expires_at TEXT,
                      last_used_at TEXT,
                      created_at TEXT NOT NULL,
                      revoked INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_publish_tokens_hash ON publish_tokens(token_hash)");
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
        List<Map<String, Object>> packs = publicPacks();
        Map<String, Map<String, Object>> packByServer = new HashMap<>();
        for (Map<String, Object> pack : packs) {
            String serverId = Authz.str(pack.get("serverId"));
            if (!serverId.isEmpty()) packByServer.put(serverId, pack);
        }
        for (Map<String, Object> server : servers) {
            Map<String, Object> pack = packByServer.get(Authz.str(server.get("id")));
            if (pack != null) server.put("pack", pack);
        }
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
        out.put("packs", packs);
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
        out.put("packs", adminPacks(request).get("packs"));
        out.put("tokens", adminTokens(request).get("tokens"));
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

    // ------------------------------------------------------------ 实例整合包

    private Path packsRoot() {
        return context.dataDir().resolve("packs");
    }

    private Path blobPath(String hash) {
        return packsRoot().resolve("blobs").resolve(hash.substring(0, 2)).resolve(hash);
    }

    private Path tempBlobPath(String hash) {
        return packsRoot().resolve("tmp").resolve(hash + ".part");
    }

    private static boolean validHash(String hash) {
        return hash != null && hash.matches("^[0-9a-f]{64}$");
    }

    private static String fileSha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("计算文件哈希失败", e);
        }
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value == null ? "" : value).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static String normalizePackPath(Object value) {
        String path = text(value, 500).replace('\\', '/');
        while (path.startsWith("/")) path = path.substring(1);
        if (path.isEmpty()) throw Web.badRequest("文件路径不能为空");
        if (path.contains("\0") || path.matches(".*[:*?\"<>|].*")) {
            throw Web.badRequest("文件路径包含非法字符：" + path);
        }
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.endsWith(".") || segment.endsWith(" ")) {
                throw Web.badRequest("文件路径不合法：" + path);
            }
        }
        if (".swustmcl-pack.json".equals(path) || "swustmcl-instance.json".equals(path)) {
            throw Web.badRequest("整合包不能包含启动器私有文件：" + path);
        }
        return path;
    }

    private List<Map<String, Object>> normalizeFiles(Object raw) {
        if (!(raw instanceof List<?> list)) throw Web.badRequest("缺少文件清单");
        if (list.size() > MAX_PACK_FILES) throw Web.badRequest("文件数量超过上限");
        List<Map<String, Object>> files = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        long total = 0;
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> item)) throw Web.badRequest("文件清单格式不正确");
            String path = normalizePackPath(item.get("path"));
            String hash = text(item.get("hash"), 64).toLowerCase(Locale.ROOT);
            if (!validHash(hash)) throw Web.badRequest("文件 hash 不合法：" + path);
            long size = longValue(item.get("size"));
            if (size < 0 || size > MAX_BLOB_BYTES) throw Web.badRequest("文件大小不合法：" + path);
            if (!paths.add(path)) throw Web.badRequest("文件路径重复：" + path);
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("path", path);
            file.put("hash", hash);
            file.put("size", size);
            files.add(file);
            total += size;
        }
        if (total > MAX_PACK_BYTES) throw Web.badRequest("整合包体积超过上限");
        files.sort((a, b) -> Authz.str(a.get("path")).compareTo(Authz.str(b.get("path"))));
        return files;
    }

    private List<Map<String, Object>> parseManifest(String json) {
        try {
            Object value = Json.MAPPER.readValue(json == null || json.isBlank() ? "[]" : json, Object.class);
            if (!(value instanceof List<?> list)) return List.of();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                Map<String, Object> file = new LinkedHashMap<>();
                file.put("path", Authz.str(map.get("path")));
                file.put("hash", Authz.str(map.get("hash")));
                file.put("size", Authz.num(map.get("size")));
                out.add(file);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> parseObject(String json) {
        try {
            Object value = Json.MAPPER.readValue(json == null || json.isBlank() ? "{}" : json, Object.class);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((key, item) -> out.put(String.valueOf(key), item));
                return out;
            }
        } catch (Exception ignored) {
        }
        return Map.of();
    }

    private Map<String, Object> diffManifests(List<Map<String, Object>> oldFiles,
                                              List<Map<String, Object>> newFiles) {
        Map<String, Map<String, Object>> oldByPath = new HashMap<>();
        Map<String, Map<String, Object>> newByPath = new HashMap<>();
        for (Map<String, Object> file : oldFiles) oldByPath.put(Authz.str(file.get("path")), file);
        for (Map<String, Object> file : newFiles) newByPath.put(Authz.str(file.get("path")), file);

        Map<String, List<String>> oldByHash = new HashMap<>();
        for (Map<String, Object> file : oldFiles) {
            if (!newByPath.containsKey(Authz.str(file.get("path")))) {
                oldByHash.computeIfAbsent(Authz.str(file.get("hash")), key -> new ArrayList<>())
                        .add(Authz.str(file.get("path")));
            }
        }

        int added = 0;
        int modified = 0;
        int deleted = 0;
        int moved = 0;
        Set<String> consumedOldPaths = new HashSet<>();
        for (Map<String, Object> file : newFiles) {
            String path = Authz.str(file.get("path"));
            Map<String, Object> old = oldByPath.get(path);
            if (old == null) {
                List<String> candidates = oldByHash.get(Authz.str(file.get("hash")));
                String matched = null;
                if (candidates != null) {
                    for (String candidate : candidates) {
                        if (!consumedOldPaths.contains(candidate)) {
                            matched = candidate;
                            break;
                        }
                    }
                }
                if (matched != null) {
                    consumedOldPaths.add(matched);
                    moved += 1;
                } else {
                    added += 1;
                }
            } else if (!Authz.str(old.get("hash")).equals(Authz.str(file.get("hash")))
                    || longValue(old.get("size")) != longValue(file.get("size"))) {
                modified += 1;
            }
        }
        for (String oldPath : oldByPath.keySet()) {
            if (newByPath.containsKey(oldPath) || consumedOldPaths.contains(oldPath)) continue;
            deleted += 1;
        }

        Set<String> missing = new HashSet<>();
        long uploadBytes = 0;
        for (Map<String, Object> file : newFiles) {
            String hash = Authz.str(file.get("hash"));
            if (missing.contains(hash)) continue;
            if (longValue(file.get("size")) == 0 && EMPTY_SHA256.equals(hash)) continue;
            if (!Files.isRegularFile(blobPath(hash))) {
                missing.add(hash);
                uploadBytes += longValue(file.get("size"));
            }
        }
        Map<String, Object> diff = new LinkedHashMap<>();
        diff.put("added", added);
        diff.put("modified", modified);
        diff.put("deleted", deleted);
        diff.put("moved", moved);
        diff.put("uploadBytes", uploadBytes);
        diff.put("missingCount", missing.size());
        diff.put("missing", new ArrayList<>(missing));
        return diff;
    }

    private Map<String, Object> manifestPayload(PluginRequest request) throws Exception {
        byte[] body = request.body(16 * 1024 * 1024);
        if (body.length == 0) throw Web.badRequest("请求内容为空");
        Object value = Json.MAPPER.readValue(body, Object.class);
        if (!(value instanceof Map<?, ?> map)) throw Web.badRequest("请求内容必须是 JSON 对象");
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((key, item) -> out.put(String.valueOf(key), item));
        return out;
    }

    private Map<String, Object> requirePublishToken(PluginRequest request) throws SQLException {
        String raw = request.header("X-SWUSTMCL-Publish-Token");
        if (raw == null || raw.isBlank()) throw Web.unauthorized("缺少发布令牌");
        Map<String, Object> token = one("""
                SELECT * FROM publish_tokens WHERE token_hash = ? AND revoked = 0
                """, Security.sha256(raw.trim()));
        if (token == null) throw Web.unauthorized("发布令牌无效");
        String expiresAt = Authz.str(token.get("expires_at"));
        if (!expiresAt.isEmpty() && expiresAt.compareTo(Security.isoNow()) <= 0) {
            throw Web.unauthorized("发布令牌已过期");
        }
        update("UPDATE publish_tokens SET last_used_at = ? WHERE id = ?",
                Security.isoNow(), token.get("id"));
        return token;
    }

    private void validatePackMeta(String serverId, String policy, int minJava, String excludePackId) throws SQLException {
        if (serverId.isEmpty() || one("SELECT id FROM servers WHERE id = ?", serverId) == null) {
            throw Web.badRequest("请选择要绑定的服务器");
        }
        if (!POLICIES.contains(policy)) throw Web.badRequest("绑定方式不合法");
        if (!MIN_JAVA_VALUES.contains(minJava)) throw Web.badRequest("最低 Java 版本不合法");
        Map<String, Object> occupied = one(
                "SELECT id, name FROM packs WHERE server_id = ? AND id <> ?", serverId, excludePackId);
        if (occupied != null) throw Web.conflict("该服务器已绑定整合包：" + Authz.str(occupied.get("name")));
    }

    private Map<String, Object> publicPackSummary(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Authz.str(row.get("id")));
        item.put("name", Authz.str(row.get("name")));
        item.put("description", Authz.str(row.get("description")));
        item.put("icon", emptyToNull(Authz.str(row.get("icon"))));
        item.put("serverId", Authz.str(row.get("server_id")));
        item.put("policy", Authz.str(row.get("policy")));
        item.put("minJava", Authz.num(row.get("min_java")));
        item.put("latestVersion", Authz.num(row.get("latest_version")));
        item.put("fileCount", Authz.num(row.get("latest_file_count")));
        item.put("totalSize", longValue(row.get("latest_total_size")));
        item.put("updatedAt", Authz.str(row.get("updated_at")));
        Map<String, Object> instance = parseObject(Authz.str(row.get("latest_instance_json")));
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("mcVersion", text(instance.get("mcVersion"), 40));
        brief.put("loader", text(instance.get("loader"), 40));
        brief.put("loaderVersion", text(instance.get("loaderVersion"), 80));
        brief.put("javaMajor", Authz.num(instance.get("javaMajor")));
        item.put("instance", brief);
        return item;
    }

    private List<Map<String, Object>> publicPacks() throws SQLException {
        List<Map<String, Object>> rows = all("""
                SELECT p.*, v.file_count AS latest_file_count, v.total_size AS latest_total_size,
                       v.instance_json AS latest_instance_json
                FROM packs p
                LEFT JOIN pack_versions v ON v.pack_id = p.id AND v.version_no = p.latest_version
                WHERE p.visible = 1 AND p.latest_version > 0
                ORDER BY p.updated_at DESC, p.name
                """);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) out.add(publicPackSummary(row));
        return out;
    }

    private Object publicPack(PluginRequest request) throws Exception {
        String id = text(request.query("id"), 80);
        String serverId = text(request.query("server"), 80);
        Map<String, Object> pack;
        if (!id.isEmpty()) {
            pack = one("""
                    SELECT p.*, v.file_count AS latest_file_count, v.total_size AS latest_total_size,
                           v.instance_json AS latest_instance_json
                    FROM packs p
                    LEFT JOIN pack_versions v ON v.pack_id = p.id AND v.version_no = p.latest_version
                    WHERE p.id = ?
                    """, id);
        } else if (!serverId.isEmpty()) {
            pack = one("""
                    SELECT p.*, v.file_count AS latest_file_count, v.total_size AS latest_total_size,
                           v.instance_json AS latest_instance_json
                    FROM packs p
                    LEFT JOIN pack_versions v ON v.pack_id = p.id AND v.version_no = p.latest_version
                    WHERE p.server_id = ? AND p.visible = 1
                    """, serverId);
        } else {
            throw Web.badRequest("缺少整合包 ID 或服务器 ID");
        }
        if (pack == null || Authz.num(pack.get("latest_version")) <= 0) throw Web.notFound("整合包不存在或尚未发布");
        int latest = Authz.num(pack.get("latest_version"));
        Map<String, Object> version = one("""
                SELECT * FROM pack_versions WHERE pack_id = ? AND version_no = ?
                """, pack.get("id"), latest);
        if (version == null) throw Web.notFound("整合包版本不存在");
        Map<String, Object> out = publicPackSummary(pack);
        out.put("instance", parseObject(Authz.str(version.get("instance_json"))));
        Map<String, Object> versionInfo = new LinkedHashMap<>();
        versionInfo.put("version", latest);
        versionInfo.put("notes", Authz.str(version.get("notes")));
        versionInfo.put("createdAt", Authz.str(version.get("created_at")));
        versionInfo.put("fileCount", Authz.num(version.get("file_count")));
        versionInfo.put("totalSize", longValue(version.get("total_size")));
        out.put("version", versionInfo);
        out.put("files", parseManifest(Authz.str(version.get("manifest_json"))));
        return out;
    }

    private Object publicBlob(PluginRequest request) {
        String hash = text(request.query("hash"), 64).toLowerCase(Locale.ROOT);
        if (!validHash(hash)) throw Web.badRequest("文件 hash 不合法");
        Path file = blobPath(hash);
        if (!Files.isRegularFile(file)) throw Web.notFound("文件不存在");
        return PluginResponse.file(file, "application/octet-stream", "\"" + hash + "\"");
    }

    private Object adminPreparePack(PluginRequest request) throws Exception {
        requirePublishToken(request);
        Map<String, Object> body = manifestPayload(request);
        List<Map<String, Object>> files = normalizeFiles(body.get("files"));
        String serverId = text(body.get("serverId"), 80);
        String policy = text(body.get("policy"), 20);
        int minJava = (int) longValue(body.getOrDefault("minJava", 0));
        String packId = text(body.get("packId"), 80);
        Map<String, Object> pack = null;
        if (!packId.isEmpty()) {
            pack = one("SELECT * FROM packs WHERE id = ?", packId);
            if (pack == null) throw Web.notFound("整合包不存在");
        }
        validatePackMeta(serverId, policy, minJava, pack == null ? "" : packId);
        String oldManifest = "[]";
        if (pack != null && Authz.num(pack.get("latest_version")) > 0) {
            Map<String, Object> version = one(
                    "SELECT manifest_json FROM pack_versions WHERE pack_id = ? AND version_no = ?",
                    packId, pack.get("latest_version"));
            if (version != null) oldManifest = Authz.str(version.get("manifest_json"));
        }
        Map<String, Object> diff = diffManifests(parseManifest(oldManifest), files);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("packId", packId.isEmpty() ? "pack_" + Security.randomHex(6) : packId);
        out.put("latestVersion", pack == null ? 0 : Authz.num(pack.get("latest_version")));
        out.put("diff", diff);
        return out;
    }

    private Object adminUploadBlob(PluginRequest request) throws Exception {
        requirePublishToken(request);
        String hash = text(request.query("hash"), 64).toLowerCase(Locale.ROOT);
        if (!validHash(hash)) throw Web.badRequest("文件 hash 不合法");
        long total = longValue(request.query("total"));
        long offset = longValue(request.query("offset"));
        if (total <= 0 || total > MAX_BLOB_BYTES) throw Web.badRequest("文件大小不合法");
        if (offset < 0 || offset > total) throw Web.badRequest("分片位置不合法");
        Path target = blobPath(hash);
        if (Files.isRegularFile(target)) {
            if (Files.size(target) != total) throw Web.conflict("已存在同名文件但大小不一致");
            return Map.of("done", true, "size", total);
        }
        byte[] data = request.body(UPLOAD_CHUNK + 1024);
        if (offset + data.length > total) throw Web.badRequest("分片超出文件大小");
        if (data.length == 0 && offset < total) throw Web.badRequest("分片内容为空");
        Object lock = BLOB_LOCKS.computeIfAbsent(hash, key -> new Object());
        synchronized (lock) {
            Path temp = tempBlobPath(hash);
            Files.createDirectories(temp.getParent());
            try (RandomAccessFile raf = new RandomAccessFile(temp.toFile(), "rw")) {
                long length = raf.length();
                if (offset > length) throw new Web.HttpError(409, "upload_gap", "上传分片不连续，请重新上传");
                if (offset == 0) raf.setLength(0);
                else if (length > offset) raf.setLength(offset);
                raf.seek(offset);
                raf.write(data);
            }
            long written = offset + data.length;
            if (written == total) {
                String actual = fileSha256(temp);
                if (!actual.equals(hash)) {
                    Files.deleteIfExists(temp);
                    throw Web.badRequest("文件校验失败，请重试");
                }
                Files.createDirectories(target.getParent());
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                return Map.of("done", true, "size", total);
            }
            return Map.of("done", false, "received", written);
        }
    }

    private synchronized Object adminCommitPack(PluginRequest request) throws Exception {
        Map<String, Object> token = requirePublishToken(request);
        Map<String, Object> body = manifestPayload(request);
        List<Map<String, Object>> files = normalizeFiles(body.get("files"));
        String serverId = text(body.get("serverId"), 80);
        String policy = text(body.get("policy"), 20);
        int minJava = (int) longValue(body.getOrDefault("minJava", 0));
        String packId = text(body.get("packId"), 80);
        if (packId.isEmpty()) packId = "pack_" + Security.randomHex(6);
        Map<String, Object> pack = one("SELECT * FROM packs WHERE id = ?", packId);
        String name = text(body.get("name"), 80);
        if (name.isEmpty() && pack != null) name = Authz.str(pack.get("name"));
        if (name.isEmpty()) throw Web.badRequest("请填写整合包名称");
        validatePackMeta(serverId, policy, minJava, packId);
        Map<String, Object> instance = body.get("instance") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        String instanceJson = Json.stringify(instance);
        String manifestJson = Json.stringify(files);
        int fileCount = files.size();
        long totalSize = files.stream().mapToLong(file -> longValue(file.get("size"))).sum();
        for (Map<String, Object> file : files) {
            String hash = Authz.str(file.get("hash"));
            Path blob = blobPath(hash);
            if (Files.isRegularFile(blob)) continue;
            if (longValue(file.get("size")) == 0 && EMPTY_SHA256.equals(hash)) {
                Files.createDirectories(blob.getParent());
                Files.write(blob, new byte[0]);
                continue;
            }
            throw Web.badRequest("文件尚未上传完成：" + Authz.str(file.get("path")));
        }
        String now = Security.isoNow();
        String createdBy = Authz.str(token.get("name"));
        int latest = pack == null ? 0 : Authz.num(pack.get("latest_version"));
        String oldManifest = "[]";
        String oldInstance = "{}";
        if (latest > 0) {
            Map<String, Object> previous = one(
                    "SELECT manifest_json, instance_json FROM pack_versions WHERE pack_id = ? AND version_no = ?",
                    packId, latest);
            if (previous != null) {
                oldManifest = Authz.str(previous.get("manifest_json"));
                oldInstance = Authz.str(previous.get("instance_json"));
            }
        }
        boolean changed = latest == 0 || !manifestJson.equals(oldManifest) || !instanceJson.equals(oldInstance);
        if (pack == null) {
            update("""
                    INSERT INTO packs
                    (id, name, description, icon, server_id, policy, min_java, visible, latest_version,
                     created_at, updated_at, created_by)
                    VALUES (?, ?, ?, '', ?, ?, ?, 1, 0, ?, ?, ?)
                    """, packId, name, text(body.get("description"), 300), serverId, policy, minJava,
                    now, now, createdBy);
        } else {
            update("""
                    UPDATE packs SET name = ?, description = ?, server_id = ?, policy = ?, min_java = ?,
                      updated_at = ? WHERE id = ?
                    """, name, text(body.get("description"), 300), serverId, policy, minJava, now, packId);
        }
        int nextVersion = latest;
        if (changed) {
            nextVersion = latest + 1;
            update("""
                    INSERT INTO pack_versions
                    (id, pack_id, version_no, manifest_json, instance_json, file_count, total_size,
                     notes, created_at, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, "pv_" + Security.randomHex(6), packId, nextVersion, manifestJson, instanceJson,
                    fileCount, totalSize, text(body.get("notes"), 500), now, createdBy);
            update("UPDATE packs SET latest_version = ?, updated_at = ? WHERE id = ?",
                    nextVersion, now, packId);
        }
        Map<String, Object> out = adminPackRow(packId);
        out.put("changed", changed);
        out.put("latestVersion", nextVersion);
        return out;
    }

    private Map<String, Object> adminPackRow(String packId) throws SQLException {
        Map<String, Object> row = one("""
                SELECT p.*, v.file_count AS latest_file_count, v.total_size AS latest_total_size,
                       v.instance_json AS latest_instance_json, v.notes AS latest_notes,
                       v.created_at AS latest_created_at
                FROM packs p
                LEFT JOIN pack_versions v ON v.pack_id = p.id AND v.version_no = p.latest_version
                WHERE p.id = ?
                """, packId);
        if (row == null) throw Web.notFound("整合包不存在");
        Map<String, Object> out = publicPackSummary(row);
        out.put("visible", Authz.num(row.get("visible")) != 0);
        out.put("createdBy", Authz.str(row.get("created_by")));
        out.put("createdAt", Authz.str(row.get("created_at")));
        out.put("latestNotes", Authz.str(row.get("latest_notes")));
        out.put("latestCreatedAt", Authz.str(row.get("latest_created_at")));
        return out;
    }

    private Map<String, Object> adminPacks(PluginRequest request) throws Exception {
        Map<String, String> serverNames = new HashMap<>();
        for (Map<String, Object> server : all("SELECT id, name FROM servers")) {
            serverNames.put(Authz.str(server.get("id")), Authz.str(server.get("name")));
        }
        List<Map<String, Object>> rows = all("""
                SELECT p.*, v.file_count AS latest_file_count, v.total_size AS latest_total_size,
                       v.instance_json AS latest_instance_json, v.notes AS latest_notes,
                       v.created_at AS latest_created_at
                FROM packs p
                LEFT JOIN pack_versions v ON v.pack_id = p.id AND v.version_no = p.latest_version
                ORDER BY p.updated_at DESC, p.name
                """);
        List<Map<String, Object>> packs = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = publicPackSummary(row);
            item.put("visible", Authz.num(row.get("visible")) != 0);
            item.put("serverName", serverNames.getOrDefault(Authz.str(row.get("server_id")), ""));
            item.put("createdBy", Authz.str(row.get("created_by")));
            item.put("latestNotes", Authz.str(row.get("latest_notes")));
            item.put("latestCreatedAt", Authz.str(row.get("latest_created_at")));
            packs.add(item);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("packs", packs);
        return out;
    }

    private Map<String, Object> adminPackVersions(PluginRequest request) throws SQLException {
        String id = text(request.query("id"), 80);
        if (id.isEmpty()) throw Web.badRequest("缺少整合包 ID");
        List<Map<String, Object>> rows = all("""
                SELECT version_no, file_count, total_size, notes, created_at, created_by
                FROM pack_versions WHERE pack_id = ? ORDER BY version_no DESC
                """, id);
        return Map.of("versions", rows);
    }

    private Map<String, Object> adminUpdatePack(PluginRequest request) throws Exception {
        Map<String, Object> body = request.json();
        String id = text(body.get("id"), 80);
        Map<String, Object> pack = one("SELECT * FROM packs WHERE id = ?", id);
        if (pack == null) throw Web.notFound("整合包不存在");
        String action = text(body.get("action"), 30);
        String now = Security.isoNow();
        if ("visibility".equals(action)) {
            update("UPDATE packs SET visible = ?, updated_at = ? WHERE id = ?",
                    Authz.bool(body.get("visible")) ? 1 : 0, now, id);
        } else if ("rollback".equals(action)) {
            int version = (int) longValue(body.get("version"));
            if (one("SELECT version_no FROM pack_versions WHERE pack_id = ? AND version_no = ?", id, version) == null) {
                throw Web.notFound("版本不存在");
            }
            update("UPDATE packs SET latest_version = ?, updated_at = ? WHERE id = ?", version, now, id);
        } else if ("delete".equals(action)) {
            update("DELETE FROM pack_versions WHERE pack_id = ?", id);
            update("DELETE FROM packs WHERE id = ?", id);
            return Map.of("deleted", true);
        } else {
            throw Web.badRequest("不支持的操作");
        }
        return adminPackRow(id);
    }

    private Map<String, Object> adminTokens(PluginRequest request) throws SQLException {
        List<Map<String, Object>> rows = all("""
                SELECT id, name, prefix, expires_at, last_used_at, created_at, revoked
                FROM publish_tokens ORDER BY created_at DESC
                """);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", Authz.str(row.get("id")));
            item.put("name", Authz.str(row.get("name")));
            item.put("prefix", Authz.str(row.get("prefix")));
            item.put("expiresAt", emptyToNull(Authz.str(row.get("expires_at"))));
            item.put("lastUsedAt", emptyToNull(Authz.str(row.get("last_used_at"))));
            item.put("createdAt", Authz.str(row.get("created_at")));
            item.put("revoked", Authz.num(row.get("revoked")) != 0);
            out.add(item);
        }
        return Map.of("tokens", out);
    }

    private Map<String, Object> adminCreateToken(PluginRequest request) throws Exception {
        Map<String, Object> body = request.json();
        String name = text(body.get("name"), 40);
        if (name.isEmpty()) name = "启动器发布令牌";
        int expiresInDays = (int) longValue(body.getOrDefault("expiresInDays", 0));
        if (expiresInDays < 0 || expiresInDays > 3650) throw Web.badRequest("有效期不合法");
        String token = "spt_" + Security.randomToken(32);
        String id = "tok_" + Security.randomHex(6);
        String expiresAt = expiresInDays > 0
                ? Instant.now().plusSeconds(expiresInDays * 86400L).toString() : null;
        update("""
                INSERT INTO publish_tokens (id, name, token_hash, prefix, expires_at, created_at, revoked)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """, id, name, Security.sha256(token), token.substring(0, 12), expiresAt, Security.isoNow());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", token);
        out.put("id", id);
        out.put("name", name);
        out.put("expiresAt", expiresAt);
        return out;
    }

    private Map<String, Object> adminDeleteToken(PluginRequest request) throws Exception {
        String id = text(request.json().get("id"), 80);
        if (id.isEmpty()) throw Web.badRequest("缺少令牌 ID");
        update("DELETE FROM publish_tokens WHERE id = ?", id);
        return adminTokens(request);
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
        if (server.get("pack") instanceof Map<?, ?> pack) item.put("pack", pack);
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

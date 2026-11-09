package cn.edu.swust.mc.skin;

import cn.edu.swust.mc.skin.plugin.PluginContext;
import cn.edu.swust.mc.skin.plugin.PluginRequest;
import cn.edu.swust.mc.skin.plugin.SkinPlugin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Plugins {
    private static final String ID_RE = "^[a-z][a-z0-9-]{2,31}$";
    private static final String VERSION_RE = "^\\d+\\.\\d+\\.\\d+([-+][0-9A-Za-z.-]+)?$";
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private final App app;
    private final Database db;
    private final Config config;
    private final Map<String, Record> records = new LinkedHashMap<>();
    private final List<Map<String, Object>> scanErrors = new ArrayList<>();

    private static final class PluginRoute {
        String method;
        String path;
        SkinPlugin.RouteHandler handler;
        boolean admin;
    }

    private static final class Record {
        Path dir;
        Map<String, Object> manifest;
        String id;
        boolean enabled;
        boolean loaded;
        String error;
        String installedAt;
        String updatedAt;
        final List<PluginRoute> routes = new ArrayList<>();
        final Map<String, List<SkinPlugin.EventHandler>> hooks = new LinkedHashMap<>();
        SkinPlugin instance;
        URLClassLoader loader;
        ContextImpl context;
    }

    public Plugins(App app) {
        this.app = app;
        this.db = app.db;
        this.config = app.config;
    }

    public void init() {
        try {
            Files.createDirectories(config.pluginsDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建插件目录", e);
        }
        scan();
        for (Record record : new ArrayList<>(records.values())) {
            if (record.enabled) load(record.id);
        }
    }

    public synchronized Map<String, Object> scan() {
        scanErrors.clear();
        Set<String> seen = new LinkedHashSet<>();
        List<Path> dirs = new ArrayList<>();
        try (var stream = Files.list(config.pluginsDir)) {
            stream.filter(Files::isDirectory).forEach(dirs::add);
        } catch (IOException e) {
            throw new IllegalStateException("读取插件目录失败", e);
        }
        dirs.sort(Comparator.comparing(path -> path.getFileName().toString()));

        for (Path dir : dirs) {
            Path manifestFile = dir.resolve("plugin.json");
            if (!Files.isRegularFile(manifestFile)) continue;
            Map<String, Object> manifest;
            try {
                manifest = validateManifest(readJson(manifestFile), dir.getFileName().toString());
                String client = string(manifest.get("client"));
                if (!client.isEmpty() && !Files.isRegularFile(dir.resolve(client))) {
                    throw new IllegalArgumentException("client 入口不存在：" + client);
                }
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                scanErrors.add(Map.of("directory", dir.getFileName().toString(), "error", message));
                Log.warn("插件 " + dir.getFileName() + " 无法加载：" + e.getMessage());
                continue;
            }
            String id = string(manifest.get("id"));
            seen.add(id);
            Map<String, Object> row = db.one("SELECT * FROM plugins WHERE id = ?", id);
            String now = Security.isoNow();
            if (row == null) {
                db.update("""
                        INSERT INTO plugins (id, name, version, description, author, enabled, installed_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, id, string(manifest.get("name")), string(manifest.get("version")),
                        string(manifest.get("description")), string(manifest.get("author")),
                        bool(manifest.get("enabledByDefault")) ? 1 : 0, now, now);
                row = db.one("SELECT * FROM plugins WHERE id = ?", id);
            } else if (!string(manifest.get("name")).equals(string(row.get("name")))
                    || !string(manifest.get("version")).equals(string(row.get("version")))
                    || !string(manifest.get("description")).equals(string(row.get("description")))
                    || !string(manifest.get("author")).equals(string(row.get("author")))) {
                db.update("""
                        UPDATE plugins SET name = ?, version = ?, description = ?, author = ?, updated_at = ?
                        WHERE id = ?
                        """, string(manifest.get("name")), string(manifest.get("version")),
                        string(manifest.get("description")), string(manifest.get("author")), now, id);
                row = db.one("SELECT * FROM plugins WHERE id = ?", id);
            }
            Record existing = records.get(id);
            if (existing != null && existing.loaded) continue;
            Record record = new Record();
            record.id = id;
            record.dir = dir;
            record.manifest = manifest;
            record.enabled = Authz.bool(row.get("enabled"));
            record.loaded = false;
            record.error = null;
            record.installedAt = Authz.str(row.get("installed_at"));
            record.updatedAt = Authz.str(row.get("updated_at"));
            records.put(id, record);
        }

        for (Map.Entry<String, Record> entry : new ArrayList<>(records.entrySet())) {
            String id = entry.getKey();
            Record record = entry.getValue();
            if (seen.contains(id)) continue;
            if (Files.exists(record.dir)) {
                scanErrors.add(Map.of("directory", record.dir.getFileName().toString(),
                        "error", "插件清单无效，已保留原有记录"));
                continue;
            }
            unload(id);
            records.remove(id);
            db.update("DELETE FROM plugins WHERE id = ?", id);
            db.update("DELETE FROM plugin_data WHERE plugin_id = ?", id);
            Authz.removePluginPermission(db, id);
            Log.warn("插件 " + id + " 的目录已移除，记录已清理");
        }
        syncPermissions();
        return listAdmin();
    }

    private void syncPermissions() {
        for (Record record : records.values()) {
            Authz.ensurePluginPermission(db, config, record.id, string(record.manifest.get("name")));
        }
    }

    public synchronized Record load(String id) {
        Record record = records.get(id);
        if (record == null) throw Web.notFound("插件不存在");
        if (record.loaded) return record;
        if (!record.enabled) throw Web.forbidden("插件未启用");
        record.error = null;
        String mainClass = string(record.manifest.get("mainClass"));
        if (mainClass.isEmpty()) {
            record.loaded = true;
            return record;
        }
        try {
            URLClassLoader loader = new URLClassLoader(new URL[]{record.dir.toUri().toURL()},
                    Plugins.class.getClassLoader());
            Class<?> type = Class.forName(mainClass, true, loader);
            if (!SkinPlugin.class.isAssignableFrom(type)) {
                throw new IllegalStateException("插件主类必须实现 " + SkinPlugin.class.getName());
            }
            SkinPlugin instance = (SkinPlugin) type.getDeclaredConstructor().newInstance();
            ContextImpl context = new ContextImpl(record);
            instance.activate(context);
            record.loader = loader;
            record.instance = instance;
            record.context = context;
            record.loaded = true;
            Log.info("插件已加载：" + string(record.manifest.get("name")) + " (" + id + ")");
        } catch (Throwable e) {
            record.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            record.loaded = false;
            record.routes.clear();
            record.hooks.clear();
            record.instance = null;
            record.context = null;
            if (record.loader != null) {
                try {
                    record.loader.close();
                } catch (IOException ignored) {
                }
            }
            record.loader = null;
            Log.error("插件 " + id + " 加载失败：" + record.error);
        }
        return record;
    }

    public synchronized void unload(String id) {
        Record record = records.get(id);
        if (record == null) return;
        if (record.loaded && record.instance != null) {
            try {
                record.instance.deactivate();
            } catch (Exception e) {
                Log.error("插件 " + id + " 卸载钩子执行失败：" + e.getMessage());
            }
        }
        record.loaded = false;
        record.routes.clear();
        record.hooks.clear();
        record.instance = null;
        record.context = null;
        if (record.loader != null) {
            try {
                record.loader.close();
            } catch (IOException ignored) {
            }
            record.loader = null;
        }
    }

    public synchronized void enable(String id) {
        Record record = records.get(id);
        if (record == null) throw Web.notFound("插件不存在");
        db.update("UPDATE plugins SET enabled = 1, updated_at = ? WHERE id = ?", Security.isoNow(), id);
        record.enabled = true;
        load(id);
    }

    public synchronized void disable(String id) {
        Record record = records.get(id);
        if (record == null) throw Web.notFound("插件不存在");
        unload(id);
        db.update("UPDATE plugins SET enabled = 0, updated_at = ? WHERE id = ?", Security.isoNow(), id);
        record.enabled = false;
    }

    public synchronized void reload(String id) {
        Record record = records.get(id);
        if (record == null) throw Web.notFound("插件不存在");
        unload(id);
        if (record.enabled) load(id);
    }

    public synchronized void rescan() {
        for (String id : new ArrayList<>(records.keySet())) unload(id);
        scan();
        for (Record record : new ArrayList<>(records.values())) {
            if (record.enabled) load(record.id);
        }
    }

    public synchronized Map<String, Object> install(byte[] archive) {
        return install(archive, null);
    }

    public synchronized Map<String, Object> install(byte[] archive, String expectedId) {
        Map<String, byte[]> files = extract(archive);
        files = stripCommonRoot(files);
        byte[] manifestBytes = files.get("plugin.json");
        if (manifestBytes == null) throw Web.conflict("插件包根目录缺少 plugin.json");
        Map<String, Object> manifest;
        try {
            manifest = validateManifest(Json.MAPPER.readValue(manifestBytes, new MapType()), null);
        } catch (Web.HttpError e) {
            throw e;
        } catch (Exception e) {
            String message = e.getMessage() == null ? "plugin.json 不是有效的 JSON 或字段不完整" : e.getMessage();
            throw Web.conflict(message);
        }
        String id = string(manifest.get("id"));
        String version = string(manifest.get("version"));
        if (expectedId != null && !expectedId.isBlank() && !expectedId.equals(id)) {
            throw Web.conflict("上传的插件包 ID（" + id + "）与要更新的插件（" + expectedId + "）不一致");
        }
        String client = string(manifest.get("client"));
        if (!client.isEmpty() && !files.containsKey(client)) throw Web.conflict("插件包中缺少 client 入口");

        Record existing = records.get(id);
        Path target = config.pluginsDir.resolve(id);
        Map<String, Object> previousRow = existing == null
                ? db.one("SELECT version FROM plugins WHERE id = ?", id)
                : null;
        boolean update = existing != null || Files.exists(target) || previousRow != null;
        if (expectedId != null && !expectedId.isBlank() && !update) {
            throw Web.notFound("要更新的插件不存在");
        }
        String previousVersion = existing != null
                ? string(existing.manifest.get("version"))
                : previousRow == null ? "" : Authz.str(previousRow.get("version"));

        Path temp = config.dataDir.resolve("tmp").resolve("plugin-" + id + "-" + System.currentTimeMillis());
        Path backup = update
                ? config.dataDir.resolve("tmp").resolve("plugin-backup-" + id + "-" + System.currentTimeMillis())
                : null;
        try {
            Files.createDirectories(temp);
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                Path destination = temp.resolve(entry.getKey()).normalize();
                if (!destination.startsWith(temp.normalize())) throw Web.conflict("插件包包含越界路径");
                Files.createDirectories(destination.getParent());
                Files.write(destination, entry.getValue());
            }
            if (update) {
                unload(id);
                if (Files.exists(target)) moveDirectory(target, backup);
            }
            Files.createDirectories(target.getParent());
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                copyDirectory(temp, target);
            }
            scan();
            Record record = records.get(id);
            if (record == null) {
                restoreUpdate(id, target, backup);
                throw Web.conflict("插件安装失败");
            }
            String now = Security.isoNow();
            if (update) {
                db.update("UPDATE plugins SET updated_at = ? WHERE id = ?", now, id);
                record.updatedAt = now;
                if (record.enabled) {
                    load(id);
                    if (record.error != null) {
                        String error = record.error;
                        restoreUpdate(id, target, backup);
                        throw Web.conflict("新版本加载失败，已回滚到旧版本：" + error);
                    }
                }
            } else {
                db.update("UPDATE plugins SET enabled = 1, updated_at = ? WHERE id = ?", now, id);
                record.enabled = true;
                record.updatedAt = now;
                load(id);
            }
            if (backup != null) {
                try {
                    deleteDirectory(backup);
                } catch (IOException ignored) {
                }
            }
            Map<String, Object> result = listAdmin();
            result.put("installedId", id);
            result.put("updated", update);
            result.put("previousVersion", previousVersion);
            result.put("version", version);
            result.put("enabled", record.enabled);
            return result;
        } catch (IOException e) {
            restoreUpdate(id, target, backup);
            throw new IllegalStateException("插件安装失败", e);
        } finally {
            try {
                deleteDirectory(temp);
            } catch (IOException ignored) {
            }
        }
    }

    private void restoreUpdate(String id, Path target, Path backup) {
        if (backup == null) return;
        try {
            unload(id);
            if (Files.exists(backup)) {
                if (Files.exists(target)) deleteDirectory(target);
                moveDirectory(backup, target);
            }
            scan();
            Record restored = records.get(id);
            if (restored != null && restored.enabled) load(id);
            Log.warn("插件 " + id + " 更新失败，已恢复原版本");
        } catch (Exception e) {
            Log.error("插件 " + id + " 回滚失败：" + e.getMessage());
        }
    }

    public synchronized void remove(String id) {
        Record record = records.get(id);
        if (record == null) throw Web.notFound("插件不存在");
        Path dir = record.dir;
        unload(id);
        records.remove(id);
        db.update("DELETE FROM plugins WHERE id = ?", id);
        db.update("DELETE FROM plugin_data WHERE plugin_id = ?", id);
        Authz.removePluginPermission(db, id);
        try {
            deleteDirectory(dir);
        } catch (IOException e) {
            Log.warn("插件目录删除失败：" + dir + "：" + e.getMessage());
        }
    }

    public synchronized boolean handle(Web.Ctx ctx) {
        String prefix = "/api/plugins/";
        if (!ctx.path.startsWith(prefix)) return false;
        String rest = ctx.path.substring(prefix.length());
        int slash = rest.indexOf('/');
        String id = slash < 0 ? rest : rest.substring(0, slash);
        String path = slash < 0 ? "/" : rest.substring(slash);
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        Record record = records.get(id);
        if (record == null || !record.loaded) return false;
        if (!canUse(ctx.user, id)) throw Web.forbidden("没有权限使用该插件");
        for (PluginRoute route : record.routes) {
            if (!route.method.equals(ctx.method)) continue;
            if (!route.path.equals(path)) continue;
            if (route.admin && ctx.user == null) throw Web.unauthorized("请先登录");
            if (route.admin && !ctx.user.admin) throw Web.forbidden("没有权限执行此操作");
            try {
                Object result = route.handler.handle(new PluginRequest(ctx, record.manifest));
                ctx.ok(result);
            } catch (Web.HttpError | Yggdrasil.YggdrasilError e) {
                throw e;
            } catch (Exception e) {
                Log.error("插件 " + id + " 路由 " + route.method + " " + route.path + " 执行失败：" + e.getMessage());
                throw new Web.HttpError(500, "plugin_error", "插件执行失败");
            }
            return true;
        }
        return false;
    }

    public boolean serveAsset(Web.Ctx ctx, String pluginId, String relative) throws IOException {
        Record record = records.get(pluginId);
        if (record == null) return false;
        if (!canUse(ctx.user, pluginId)) return false;
        if (!record.enabled && (ctx.user == null || !ctx.user.admin)) return false;
        String decoded = Web.decode(relative);
        Path target = Web.resolveInside(record.dir, decoded);
        if (target == null || target.equals(record.dir) || !Files.isRegularFile(target)) return false;
        return Web.serveFile(ctx, target, "no-cache");
    }

    public void emit(String event, Map<String, Object> payload) {
        List<Record> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(records.values());
        }
        for (Record record : snapshot) {
            if (!record.loaded) continue;
            List<SkinPlugin.EventHandler> handlers;
            synchronized (this) {
                handlers = record.hooks.get(event);
                if (handlers == null) continue;
                handlers = new ArrayList<>(handlers);
            }
            for (SkinPlugin.EventHandler handler : handlers) {
                try {
                    handler.handle(payload);
                } catch (Exception e) {
                    Log.error("插件 " + record.id + " 事件 " + event + " 处理失败：" + e.getMessage());
                }
            }
        }
    }

    public List<Map<String, Object>> publicPlugins(AuthUser user) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Record record : records.values()) {
            boolean hasPublicNav = record.manifest.get("nav") != null;
            boolean hasAdminNav = record.manifest.get("adminNav") instanceof List<?> list && !list.isEmpty();
            if (!record.enabled || !record.loaded || (!hasPublicNav && !hasAdminNav)) continue;
            if (!canUse(user, record.id)) continue;
            items.add(publicPlugin(record));
        }
        items.sort(Comparator
                .comparingInt((Map<String, Object> item) -> {
                    Object nav = item.get("nav");
                    if (nav instanceof Map<?, ?> map && map.get("order") instanceof Number number) {
                        return number.intValue();
                    }
                    return 50;
                })
                .thenComparing(item -> string(item.get("name"))));
        return items;
    }

    public Map<String, Object> listAdmin() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Record record : records.values()) {
            Map<String, Object> item = publicPlugin(record);
            item.put("enabled", record.enabled);
            item.put("loaded", record.loaded);
            item.put("error", record.error);
            item.put("installedAt", record.installedAt);
            item.put("updatedAt", record.updatedAt);
            item.put("hasServer", !string(record.manifest.get("mainClass")).isEmpty());
            item.put("hasClient", !string(record.manifest.get("client")).isEmpty());
            item.put("routes", record.routes.stream().map(route -> route.method + " " + route.path).toList());
            item.put("hooks", new ArrayList<>(record.hooks.keySet()));
            items.add(item);
        }
        items.sort(Comparator.comparing(item -> string(item.get("name"))));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("scanErrors", scanErrors);
        return result;
    }

    private boolean canUse(AuthUser user, String pluginId) {
        String code = Authz.pluginPermissionCode(pluginId);
        if (user == null) return Authz.guestCan(db, code);
        return user.can(code);
    }

    private Map<String, Object> publicPlugin(Record record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.id);
        map.put("name", string(record.manifest.get("name")));
        map.put("version", string(record.manifest.get("version")));
        map.put("description", string(record.manifest.get("description")));
        map.put("author", string(record.manifest.get("author")));
        map.put("permission", Authz.pluginPermissionCode(record.id));
        map.put("nav", record.manifest.get("nav"));
        map.put("adminNav", record.manifest.get("adminNav"));
        String client = string(record.manifest.get("client"));
        map.put("clientUrl", client.isEmpty() ? null : "/plugin-assets/" + record.id + "/" + client);
        return map;
    }

    private Map<String, Object> validateManifest(Map<String, Object> raw, String expectedId) {
        if (raw == null) throw new IllegalArgumentException("plugin.json 必须是 JSON 对象");
        String id = string(raw.get("id")).trim();
        if (!id.matches(ID_RE)) {
            throw new IllegalArgumentException("插件 id 需为 3–32 位小写字母、数字或连字符，且以字母开头");
        }
        if (expectedId != null && !id.equals(expectedId)) {
            throw new IllegalArgumentException("plugin.json 中的 id 与目录名不一致：" + id + " != " + expectedId);
        }
        String name = string(raw.get("name")).trim();
        if (name.isEmpty() || name.length() > 60) throw new IllegalArgumentException("插件名称不能为空且不超过 60 字");
        String version = string(raw.get("version")).trim();
        if (!version.matches(VERSION_RE)) throw new IllegalArgumentException("插件版本需为 semver，例如 1.0.0");
        String mainClass = safePath(string(raw.get("mainClass")), "mainClass");
        String client = safePath(string(raw.get("client")), "client");
        if (mainClass.isEmpty() && client.isEmpty()) {
            throw new IllegalArgumentException("插件必须至少提供 mainClass 或 client 入口");
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("id", id);
        manifest.put("name", name);
        manifest.put("version", version);
        manifest.put("description", truncate(string(raw.get("description")), 300));
        manifest.put("author", truncate(string(raw.get("author")), 60));
        manifest.put("mainClass", mainClass.isEmpty() ? null : mainClass);
        manifest.put("client", client.isEmpty() ? null : client);
        manifest.put("enabledByDefault", Boolean.TRUE.equals(raw.get("enabledByDefault")));
        Object nav = raw.get("nav");
        if (nav instanceof Map<?, ?> navMap) {
            Map<String, Object> clean = new LinkedHashMap<>();
            String title = truncate(string(navMap.get("title")), 16);
            if (title.isEmpty()) throw new IllegalArgumentException("nav.title 不能为空且不超过 16 字");
            clean.put("title", title);
            clean.put("icon", truncate(string(navMap.get("icon")), 32).isEmpty()
                    ? "sparkles" : truncate(string(navMap.get("icon")), 32));
            int order = 50;
            if (navMap.get("order") instanceof Number number) order = number.intValue();
            clean.put("order", order);
            manifest.put("nav", clean);
        } else {
            manifest.put("nav", null);
        }
        List<Map<String, Object>> adminNav = new ArrayList<>();
        Object rawAdminNav = raw.get("adminNav");
        if (rawAdminNav instanceof List<?> items) {
            for (Object entry : items.subList(0, Math.min(items.size(), 8))) {
                if (!(entry instanceof Map<?, ?> item)) {
                    throw new IllegalArgumentException("adminNav 配置不合法");
                }
                String title = truncate(string(item.get("title")), 20);
                if (title.isEmpty()) throw new IllegalArgumentException("adminNav.title 不能为空");
                String path = string(item.get("path")).trim();
                if (!path.isEmpty() && !path.startsWith("#/")) {
                    throw new IllegalArgumentException("adminNav.path 必须是 #/ 开头的站内路由");
                }
                Map<String, Object> clean = new LinkedHashMap<>();
                clean.put("title", title);
                clean.put("category", truncate(string(item.get("category")), 20).isEmpty()
                        ? "插件页面" : truncate(string(item.get("category")), 20));
                clean.put("icon", truncate(string(item.get("icon")), 32).isEmpty()
                        ? "plugin" : truncate(string(item.get("icon")), 32));
                clean.put("path", path);
                int order = 50;
                if (item.get("order") instanceof Number number) order = number.intValue();
                clean.put("order", order);
                adminNav.add(clean);
            }
        }
        manifest.put("adminNav", adminNav);
        manifest.put("config", raw.get("config") instanceof Map<?, ?> ? raw.get("config") : Map.of());
        return manifest;
    }

    private static String safePath(String value, String label) {
        if (value == null || value.isBlank()) return "";
        String text = value.trim();
        if (text.startsWith("/") || text.matches("^[A-Za-z]:.*") || text.contains("\0")) {
            throw new IllegalArgumentException(label + " 路径不合法");
        }
        String normalized = text.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) throw new IllegalArgumentException(label + " 路径不能越出插件目录");
        }
        return normalized;
    }

    private final class ContextImpl implements PluginContext {
        private final Record record;

        ContextImpl(Record record) {
            this.record = record;
        }

        @Override
        public String id() {
            return record.id;
        }

        @Override
        public Map<String, Object> manifest() {
            return record.manifest;
        }

        @Override
        public void info(String message) {
            Log.info("[plugin:" + record.id + "] " + message);
        }

        @Override
        public void warn(String message) {
            Log.warn("[plugin:" + record.id + "] " + message);
        }

        @Override
        public void error(String message, Throwable error) {
            Log.error("[plugin:" + record.id + "] " + message
                    + (error == null ? "" : "：" + error.getMessage()));
        }

        @Override
        public Map<String, Object> getConfig() {
            Map<String, Object> config = new LinkedHashMap<>();
            if (record.manifest.get("config") instanceof Map<?, ?> defaults) {
                for (Map.Entry<?, ?> entry : defaults.entrySet()) {
                    config.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            Object stored = getData("config");
            if (stored instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    config.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return config;
        }

        @Override
        public Map<String, Object> setConfig(Map<String, Object> patch) {
            Map<String, Object> next = getConfig();
            if (patch != null) next.putAll(patch);
            setData("config", next);
            return next;
        }

        @Override
        public Object getData(String key) {
            Map<String, Object> row = db.one(
                    "SELECT value FROM plugin_data WHERE plugin_id = ? AND key = ?", record.id, String.valueOf(key));
            if (row == null) return null;
            String value = Authz.str(row.get("value"));
            try {
                return Json.MAPPER.readValue(value, Object.class);
            } catch (Exception e) {
                return value;
            }
        }

        @Override
        public void setData(String key, Object value) {
            try {
                db.update("""
                        INSERT INTO plugin_data (plugin_id, key, value) VALUES (?, ?, ?)
                        ON CONFLICT(plugin_id, key) DO UPDATE SET value = excluded.value
                        """, record.id, String.valueOf(key), Json.stringify(value));
            } catch (Exception e) {
                throw new IllegalStateException("插件数据保存失败", e);
            }
        }

        @Override
        public void deleteData(String key) {
            db.update("DELETE FROM plugin_data WHERE plugin_id = ? AND key = ?", record.id, String.valueOf(key));
        }

        @Override
        public Path dataDir() {
            Path dir = app.config.dataDir.resolve("plugin-data").resolve(record.id).normalize();
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new IllegalStateException("无法创建插件数据目录", e);
            }
            return dir;
        }

        @Override
        public Map<String, Object> findPlayer(String uuid, String name) {
            String normalized = Security.stripUuid(uuid);
            Map<String, Object> row = normalized.matches("^[0-9a-f]{32}$")
                    ? Players.byUuid(db, normalized)
                    : null;
            if (row == null) row = Players.byName(db, name);
            return row == null ? null : Players.serialize(row, null);
        }

        @Override
        public void registerRoute(String method, String path, SkinPlugin.RouteHandler handler) {
            addRoute(method, path, handler, false);
        }

        @Override
        public void registerAdminRoute(String method, String path, SkinPlugin.RouteHandler handler) {
            addRoute(method, path, handler, true);
        }

        private void addRoute(String method, String path, SkinPlugin.RouteHandler handler, boolean admin) {
            String upper = method == null ? "" : method.toUpperCase();
            if (!METHODS.contains(upper)) throw new IllegalArgumentException("不支持的 HTTP 方法：" + method);
            String normalized = path == null ? "" : path;
            if (!normalized.startsWith("/") || normalized.contains("?") || normalized.contains("#")) {
                throw new IllegalArgumentException("插件路由必须以 / 开头，且不能包含查询串");
            }
            if (handler == null) throw new IllegalArgumentException("路由处理函数不合法");
            for (PluginRoute route : record.routes) {
                if (route.method.equals(upper) && route.path.equals(normalized)) {
                    throw new IllegalArgumentException("重复注册路由：" + upper + " " + normalized);
                }
            }
            PluginRoute route = new PluginRoute();
            route.method = upper;
            route.path = normalized;
            route.handler = handler;
            route.admin = admin;
            record.routes.add(route);
        }

        @Override
        public void on(String event, SkinPlugin.EventHandler handler) {
            if (handler == null) throw new IllegalArgumentException("事件处理函数不合法");
            record.hooks.computeIfAbsent(event, key -> new ArrayList<>()).add(handler);
        }

        @Override
        public Map<String, Object> settings() {
            return Settings.publicMap(db);
        }

        @Override
        public Map<String, Object> stats() {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("users", scalar("SELECT COUNT(*) AS count FROM users"));
            stats.put("players", scalar("SELECT COUNT(*) AS count FROM players"));
            stats.put("textures", scalar("SELECT COUNT(*) AS count FROM textures WHERE is_public = 1"));
            stats.put("downloads", scalar("SELECT COALESCE(SUM(downloads), 0) AS count FROM textures"));
            return stats;
        }

        @Override
        public Map<String, Object> listPublicTextures(Map<String, String> query) {
            Map<String, String> options = new LinkedHashMap<>(query);
            options.put("scope", "library");
            return Textures.list(db, null, options);
        }

        private int scalar(String sql) {
            Map<String, Object> row = db.one(sql);
            return row == null ? 0 : Authz.num(row.get("count"));
        }
    }

    private static Map<String, Object> readJson(Path file) throws IOException {
        return Json.MAPPER.readValue(Files.readAllBytes(file), new MapType());
    }

    private static final class MapType extends com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> {
    }

    private static Map<String, byte[]> extract(byte[] archive) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("\0")) throw Web.conflict("插件包包含非法路径");
                for (String segment : name.split("/")) {
                    if ("..".equals(segment)) throw Web.conflict("插件包包含越界路径");
                }
                byte[] data = zip.readAllBytes();
                if (data.length > 32 * 1024 * 1024) throw Web.conflict("插件包内容过大");
                files.put(name, data);
            }
        } catch (IOException e) {
            throw Web.conflict("无法读取插件包，请上传 ZIP 或 JAR 文件");
        }
        if (files.isEmpty()) throw Web.conflict("插件包为空");
        return files;
    }

    private static Map<String, byte[]> stripCommonRoot(Map<String, byte[]> files) {
        String common = null;
        for (String name : files.keySet()) {
            int slash = name.indexOf('/');
            if (slash < 0) return files;
            String first = name.substring(0, slash);
            if (common == null) common = first;
            else if (!common.equals(first)) return files;
        }
        if (common == null) return files;
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            result.put(entry.getKey().substring(common.length() + 1), entry.getValue());
        }
        return result;
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            copyDirectory(source, target);
            deleteDirectory(source);
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String truncate(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || "1".equals(String.valueOf(value));
    }
}

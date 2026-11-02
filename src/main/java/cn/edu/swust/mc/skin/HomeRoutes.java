package cn.edu.swust.mc.skin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HomeRoutes {
    private HomeRoutes() {
    }

    public static void register(App app) {
        Web.Router router = app.router;

        router.get("/api/home", ctx -> {
            Map<String, String> recentQuery = new LinkedHashMap<>();
            recentQuery.put("page", "1");
            recentQuery.put("perPage", "8");
            recentQuery.put("sort", "latest");
            Map<String, String> featuredQuery = new LinkedHashMap<>();
            featuredQuery.put("page", "1");
            featuredQuery.put("perPage", "3");
            featuredQuery.put("sort", "popular");
            featuredQuery.put("type", "skin");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("settings", Settings.publicMap(app.db));
            data.put("stats", stats(app.db));
            data.put("recent", Textures.list(app.db, ctx.user, recentQuery).get("items"));
            data.put("featured", Textures.list(app.db, ctx.user, featuredQuery).get("items"));
            data.put("yggdrasilUrl", app.config.baseUrl + "/api/yggdrasil");
            data.put("plugins", app.plugins.publicPlugins(ctx.user));
            ctx.ok(data);
        });

        router.get("/api/stats", ctx -> ctx.ok(stats(app.db)));

        router.get("/branding/:file", ctx -> {
            String filename = ctx.param("file");
            java.nio.file.Path file = Branding.file(app.config, filename);
            if (file == null || !java.nio.file.Files.isRegularFile(file)) {
                throw Web.notFound("图片不存在");
            }
            if (!Web.serveFile(ctx, file, "public, max-age=31536000, immutable")) {
                throw Web.notFound("图片不存在");
            }
        });

        router.get("/api/health", ctx -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "ok");
            data.put("time", Security.isoNow());
            ctx.ok(data);
        });
    }

    private static Map<String, Object> stats(Database db) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users", count(db, "SELECT COUNT(*) AS count FROM users"));
        data.put("players", count(db, "SELECT COUNT(*) AS count FROM players"));
        data.put("textures", count(db, "SELECT COUNT(*) AS count FROM textures WHERE is_public = 1"));
        data.put("downloads", count(db, "SELECT COALESCE(SUM(downloads), 0) AS count FROM textures"));
        return data;
    }

    private static int count(Database db, String sql) {
        Map<String, Object> row = db.one(sql);
        return row == null ? 0 : Authz.num(row.get("count"));
    }
}

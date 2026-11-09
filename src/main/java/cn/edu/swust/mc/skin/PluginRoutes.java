package cn.edu.swust.mc.skin;

import java.util.Map;

public final class PluginRoutes {
    private PluginRoutes() {
    }

    public static void register(App app) {
        Web.Router router = app.router;

        router.get("/api/plugins", ctx -> ctx.ok(Map.of("items", app.plugins.publicPlugins(ctx.user))));

        router.get("/api/admin/plugins", ctx -> {
            Auth.requirePermission(ctx, "skin:plugin:view");
            ctx.ok(app.plugins.listAdmin());
        });

        router.post("/api/admin/plugins/install", ctx -> {
            Auth.requirePermission(ctx, "skin:plugin:install");
            byte[] buffer = ctx.body((int) app.config.pluginLimit);
            Map<String, Object> result = app.plugins.install(buffer, ctx.query("expectId"));
            String installedId = String.valueOf(result.get("installedId"));
            @SuppressWarnings("unchecked")
            var items = (java.util.List<Map<String, Object>>) result.get("items");
            Map<String, Object> installed = items.stream()
                    .filter(item -> installedId.equals(item.get("id")))
                    .findFirst().orElse(null);
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("plugin", installed);
            data.put("updated", result.get("updated"));
            data.put("previousVersion", result.get("previousVersion"));
            data.put("version", result.get("version"));
            ctx.ok(data, Boolean.TRUE.equals(result.get("updated")) ? 200 : 201);
        });

        router.post("/api/admin/plugins/rescan", ctx -> {
            Auth.requirePermission(ctx, "skin:plugin:edit");
            app.plugins.rescan();
            ctx.ok(app.plugins.listAdmin());
        });

        router.post("/api/admin/plugins/:id/enable", ctx -> {
            Auth.requirePermission(ctx, "skin:plugin:edit");
            app.plugins.enable(ctx.param("id"));
            ctx.ok(app.plugins.listAdmin());
        });

        router.post("/api/admin/plugins/:id/disable", ctx -> {
            Auth.requirePermission(ctx, "skin:plugin:edit");
            app.plugins.disable(ctx.param("id"));
            ctx.ok(app.plugins.listAdmin());
        });

        router.post("/api/admin/plugins/:id/reload", ctx -> {
            Auth.requirePermission(ctx, "skin:plugin:edit");
            app.plugins.reload(ctx.param("id"));
            ctx.ok(app.plugins.listAdmin());
        });

        router.delete("/api/admin/plugins/:id", ctx -> {
            Auth.requirePermission(ctx, "skin:plugin:delete");
            app.plugins.remove(ctx.param("id"));
            ctx.ok(app.plugins.listAdmin());
        });
    }
}

package cn.edu.swust.mc.skin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SkinRoutes {
    private SkinRoutes() {
    }

    public static void register(App app) {
        Web.Router router = app.router;

        router.get("/api/textures", ctx -> ctx.ok(Textures.list(app.db, ctx.user, ctx.query)));

        router.get("/api/textures/:id", ctx -> {
            Map<String, Object> row = Textures.row(app.db, textureId(ctx.param("id")));
            if (row == null || !Textures.canAccess(row, ctx.user)) throw Web.notFound("材质不存在");
            boolean inCloset = ctx.user != null && app.db.one(
                    "SELECT 1 FROM closet_items WHERE user_id = ? AND texture_id = ?",
                    ctx.user.id, row.get("id")) != null;
            ctx.ok(Map.of("texture", Textures.serialize(row, ctx.user, inCloset)));
        });

        router.post("/api/textures", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            Map<String, Object> input = Textures.validateInput(
                    ctx.query("type"), ctx.query("name"), ctx.query("model", "classic"),
                    parseBoolean(ctx.query("public"), true));
            byte[] buffer = ctx.body((int) app.config.textureLimit);
            if (buffer.length == 0) throw Web.badRequest("请选择要上传的 PNG 文件");
            Textures.CreateResult result = Textures.create(app.db, app.config, user, buffer, input);
            boolean inCloset = app.db.one("SELECT 1 FROM closet_items WHERE user_id = ? AND texture_id = ?",
                    user.id, result.row().get("id")) != null;
            Map<String, Object> texture = Textures.serialize(result.row(), user, inCloset);
            if (result.created()) app.plugins.emit("texture.uploaded", Map.of("texture", texture, "user", user.toMap()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("texture", texture);
            data.put("created", result.created());
            data.put("attachedToCloset", result.attachedToCloset());
            ctx.ok(data, result.created() ? 201 : 200);
        });

        router.get("/api/textures/:id/content", ctx -> {
            Map<String, Object> row = Textures.row(app.db, textureId(ctx.param("id")));
            if (row == null || !Textures.canAccess(row, ctx.user)) throw Web.notFound("材质不存在");
            byte[] file = Textures.readFile(app.config, Authz.str(row.get("hash")));
            if (file == null) throw Web.notFound("材质文件不存在");
            ctx.sendBytes(200, file, "image/png",
                    Authz.bool(row.get("is_public")) ? "public, max-age=86400" : "private, max-age=3600", Map.of());
        });

        router.get("/api/textures/:id/download", ctx -> {
            Map<String, Object> row = Textures.row(app.db, textureId(ctx.param("id")));
            if (row == null || !Textures.canAccess(row, ctx.user)) throw Web.notFound("材质不存在");
            byte[] file = Textures.readFile(app.config, Authz.str(row.get("hash")));
            if (file == null) throw Web.notFound("材质文件不存在");
            app.db.update("UPDATE textures SET downloads = downloads + 1 WHERE id = ?", row.get("id"));
            String filename = Web.encodePath(Authz.str(row.get("name")) + ".png");
            ctx.sendBytes(200, file, "image/png", "no-store",
                    Map.of("Content-Disposition", "attachment; filename*=UTF-8''" + filename));
        });

        router.patch("/api/textures/:id", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            Map<String, Object> row = Textures.row(app.db, textureId(ctx.param("id")));
            if (row == null) throw Web.notFound("材质不存在");
            if (!Textures.canManage(row, user)) throw Web.forbidden("不能修改他人的材质");
            Map<String, Object> body = ctx.json();
            Map<String, Object> updated = row;
            if (body.containsKey("name")) updated = Textures.rename(app.db, updated, Authz.str(body.get("name")));
            if (body.containsKey("isPublic")) {
                updated = Textures.setVisibility(app.db, updated, parseBoolean(String.valueOf(body.get("isPublic")), true));
            }
            boolean inCloset = app.db.one("SELECT 1 FROM closet_items WHERE user_id = ? AND texture_id = ?",
                    user.id, updated.get("id")) != null;
            ctx.ok(Map.of("texture", Textures.serialize(updated, user, inCloset)));
        });

        router.delete("/api/textures/:id", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            Map<String, Object> row = Textures.row(app.db, textureId(ctx.param("id")));
            if (row == null) throw Web.notFound("材质不存在");
            if (!Textures.canManage(row, user)) throw Web.forbidden("不能删除他人的材质");
            Map<String, Object> payload = Textures.serialize(row, user, false);
            Textures.delete(app.db, app.config, row);
            app.plugins.emit("texture.deleted", Map.of("texture", payload, "user", user.toMap()));
            ctx.ok(Map.of("deleted", true));
        });

        router.get("/textures/:file", ctx -> {
            String file = ctx.param("file");
            if (!file.endsWith(".png")) throw Web.notFound("材质不存在");
            String hash = file.substring(0, file.length() - 4);
            if (!hash.matches("^[0-9a-f]{64}$")) throw Web.notFound("材质不存在");
            if (Textures.byHash(app.db, hash) == null) throw Web.notFound("材质不存在");
            if (!Web.serveFile(ctx, Textures.filePath(app.config, hash), "public, max-age=31536000, immutable")) {
                throw Web.notFound("材质文件不存在");
            }
        });

        router.get("/api/closet", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            ctx.ok(Textures.closet(app.db, user.id, ctx.query));
        });

        router.post("/api/closet/:textureId", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            Textures.addToCloset(app.db, user.id, textureId(ctx.param("textureId")));
            ctx.ok(Map.of("inCloset", true));
        });

        router.patch("/api/closet/:textureId", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            Map<String, Object> body = ctx.json();
            Textures.renameCloset(app.db, user.id, textureId(ctx.param("textureId")), Authz.str(body.get("name")));
            ctx.ok(Map.of("ok", true));
        });

        router.delete("/api/closet/:textureId", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            Textures.removeFromCloset(app.db, user.id, textureId(ctx.param("textureId")));
            ctx.ok(Map.of("inCloset", false));
        });

        router.get("/api/admin/textures", ctx -> {
            Auth.requirePermission(ctx, "skin:texture:view");
            Map<String, String> query = new LinkedHashMap<>(ctx.query);
            query.put("scope", "admin");
            ctx.ok(Textures.list(app.db, ctx.user, query));
        });

        router.patch("/api/admin/textures/:id", ctx -> {
            Auth.requirePermission(ctx, "skin:texture:edit");
            Map<String, Object> row = Textures.row(app.db, textureId(ctx.param("id")));
            if (row == null) throw Web.notFound("材质不存在");
            Map<String, Object> body = ctx.json();
            Map<String, Object> updated = row;
            if (body.containsKey("name")) updated = Textures.rename(app.db, updated, Authz.str(body.get("name")));
            if (body.containsKey("isPublic")) {
                updated = Textures.setVisibility(app.db, updated, parseBoolean(String.valueOf(body.get("isPublic")), true));
            }
            ctx.ok(Map.of("texture", Textures.serialize(updated, ctx.user, false)));
        });

        router.delete("/api/admin/textures/:id", ctx -> {
            Auth.requirePermission(ctx, "skin:texture:delete");
            Map<String, Object> row = Textures.row(app.db, textureId(ctx.param("id")));
            if (row == null) throw Web.notFound("材质不存在");
            Map<String, Object> payload = Textures.serialize(row, ctx.user, false);
            Textures.delete(app.db, app.config, row);
            app.plugins.emit("texture.deleted", Map.of("texture", payload, "user", ctx.user.toMap()));
            ctx.ok(Map.of("deleted", true));
        });

        registerPlayers(app);
    }

    private static void registerPlayers(App app) {
        Web.Router router = app.router;

        router.get("/api/players", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            List<Map<String, Object>> rows = Players.forUser(app.db, user.id);
            List<Map<String, Object>> items = rows.stream().map(row -> Players.serialize(row, user)).toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("items", items);
            data.put("limit", Players.MAX_PER_USER);
            data.put("canCreate", items.size() < Players.MAX_PER_USER);
            ctx.ok(data);
        });

        router.post("/api/players", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            Map<String, Object> body = ctx.json();
            Map<String, Object> row = Players.create(app.db, user, Authz.str(body.get("name")),
                    body.get("model") == null ? "classic" : Authz.str(body.get("model")));
            Map<String, Object> player = Players.serialize(row, user);
            app.plugins.emit("player.created", Map.of("player", player, "user", user.toMap()));
            ctx.ok(Map.of("player", player), 201);
        });

        router.get("/api/players/:id", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            Map<String, Object> player = player(app, ctx.param("id"));
            assertOwner(player, user);
            ctx.ok(Map.of("player", Players.serialize(player, user)));
        });

        router.patch("/api/players/:id", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            Map<String, Object> player = player(app, ctx.param("id"));
            assertOwner(player, user);
            Map<String, Object> body = ctx.json();
            if (body.containsKey("name")) player = Players.rename(app.db, player, Authz.str(body.get("name")));
            if (body.containsKey("model")) player = Players.setModel(app.db, player, Authz.str(body.get("model")));
            ctx.ok(Map.of("player", Players.serialize(player, user)));
        });

        router.delete("/api/players/:id", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            Map<String, Object> player = player(app, ctx.param("id"));
            assertOwner(player, user);
            Map<String, Object> payload = Players.serialize(player, user);
            Players.delete(app.db, player);
            app.plugins.emit("player.deleted", Map.of("player", payload, "user", user.toMap()));
            ctx.ok(Map.of("deleted", true));
        });

        router.put("/api/players/:id/textures", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            Map<String, Object> player = player(app, ctx.param("id"));
            assertOwner(player, user);
            Map<String, Object> body = ctx.json();
            Object skin = Players.UNSET;
            Object cape = Players.UNSET;
            if (body.containsKey("skinTextureId")) {
                skin = body.get("skinTextureId") == null ? Players.CLEAR : body.get("skinTextureId");
            }
            if (body.containsKey("capeTextureId")) {
                cape = body.get("capeTextureId") == null ? Players.CLEAR : body.get("capeTextureId");
            }
            Map<String, Object> updated = Players.setTextures(app.db, player, skin, cape, user);
            Map<String, Object> payload = Players.serialize(updated, user);
            app.plugins.emit("player.equipped", Map.of("player", payload, "user", user.toMap()));
            ctx.ok(Map.of("player", payload));
        });
    }

    private static Map<String, Object> player(App app, String rawId) {
        int id;
        try {
            id = Integer.parseInt(rawId);
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw Web.notFound("角色不存在");
        }
        Map<String, Object> player = Players.row(app.db, id);
        if (player == null) throw Web.notFound("角色不存在");
        return player;
    }

    private static void assertOwner(Map<String, Object> player, AuthUser user) {
        if (!user.id.equals(Authz.str(player.get("user_id"))) && !user.admin) {
            throw Web.forbidden("不能操作他人的角色");
        }
    }

    public static int textureId(String value) {
        try {
            int id = Integer.parseInt(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw Web.notFound("材质不存在");
        }
    }

    public static boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isEmpty()) return fallback;
        String text = value.toLowerCase();
        return "1".equals(text) || "true".equals(text) || "on".equals(text) || "yes".equals(text);
    }
}

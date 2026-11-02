package cn.edu.swust.mc.skin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApiKeyRoutes {
    private ApiKeyRoutes() {
    }

    public static void register(App app) {
        Web.Router router = app.router;

        router.get("/api/admin/api-keys/users", ctx -> {
            Auth.requirePermission(ctx, "system:user:view");
            ctx.ok(Map.of("items", Users.options(app.db, "ACTIVE")));
        });

        router.get("/api/admin/api-keys", ctx -> {
            Auth.requirePermission(ctx, "system:user:view");
            ctx.ok(Map.of("items", ApiKeys.listAll(app.db)));
        });

        router.post("/api/admin/api-keys", ctx -> {
            AuthUser operator = Auth.requirePermission(ctx, "system:user:edit");
            Map<String, Object> body = ctx.json();
            AuthUser target = Authz.authUser(app.db,
                    app.db.one("SELECT * FROM users WHERE id = ?", Authz.str(body.get("userId")).trim()));
            if (target == null) throw Web.notFound("用户不存在");
            if (!operator.id.equals(target.id) && operator.levelWeight < target.levelWeight) {
                throw Web.forbidden("不能为同级或更高权限的用户创建 API 密钥");
            }
            Integer days = body.get("expiresInDays") instanceof Number number ? number.intValue() : null;
            Map<String, Object> result = ApiKeys.create(app.db, app.config, target,
                    Authz.str(body.get("name")), Authz.str(body.getOrDefault("scope", "all")),
                    Users.stringList(body.get("permissions")), days);
            ctx.ok(result, 201);
        });

        router.delete("/api/admin/api-keys/:id", ctx -> {
            AuthUser operator = Auth.requirePermission(ctx, "system:user:edit");
            Map<String, Object> row = ApiKeys.row(app.db, ctx.param("id"));
            if (row == null) throw Web.notFound("API 密钥不存在");
            AuthUser owner = Authz.authUser(app.db,
                    app.db.one("SELECT * FROM users WHERE id = ?", Authz.str(row.get("user_id"))));
            if (owner != null && !operator.id.equals(owner.id) && operator.levelWeight < owner.levelWeight) {
                throw Web.forbidden("不能删除同级或更高权限用户的 API 密钥");
            }
            ApiKeys.delete(app.db, ctx.param("id"));
            ctx.ok(Map.of("deleted", true));
        });
    }
}

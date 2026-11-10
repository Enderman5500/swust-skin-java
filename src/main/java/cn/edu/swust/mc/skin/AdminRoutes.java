package cn.edu.swust.mc.skin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AdminRoutes {
    private AdminRoutes() {
    }

    public static void register(App app) {
        Web.Router router = app.router;

        router.get("/api/admin/stats", ctx -> {
            AuthUser operator = Auth.requireUser(ctx);
            if (!operator.can("system:user:view") && !operator.can("system:setting:view")) {
                throw Web.forbidden("没有权限执行此操作");
            }
            Database db = app.db;
            int users = count(db, "SELECT COUNT(*) AS count FROM users");
            int activeUsers = count(db, "SELECT COUNT(*) AS count FROM users WHERE status = 'ACTIVE'");
            int players = count(db, "SELECT COUNT(*) AS count FROM players");
            Map<String, Object> textureStats = Textures.stats(db);
            int privateTextures = count(db, "SELECT COUNT(*) AS count FROM textures WHERE is_public = 0");
            Map<String, Object> plugins = app.plugins.listAdmin();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pluginItems = (List<Map<String, Object>>) plugins.get("items");
            long enabledPlugins = pluginItems.stream().filter(item -> Boolean.TRUE.equals(item.get("enabled"))).count();

            Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("users", users);
            counts.put("activeUsers", activeUsers);
            counts.put("players", players);
            counts.put("textures", textureStats.get("textures"));
            counts.put("privateTextures", privateTextures);
            counts.put("downloads", textureStats.get("downloads"));
            counts.put("plugins", pluginItems.size());
            counts.put("pluginsEnabled", enabledPlugins);

            Map<String, Object> runtime = new LinkedHashMap<>();
            runtime.put("node", "Java " + System.getProperty("java.version"));
            runtime.put("uptime", (System.currentTimeMillis() - app.startedAt) / 1000);
            runtime.put("baseUrl", app.config.baseUrl);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("counts", counts);
            data.put("recentUsers", Users.page(db, Map.of("page", "1", "size", "6")).get("items"));
            Map<String, String> textureQuery = new LinkedHashMap<>();
            textureQuery.put("scope", "admin");
            textureQuery.put("perPage", "5");
            textureQuery.put("page", "1");
            data.put("recentTextures", Textures.list(db, ctx.user, textureQuery).get("items"));
            data.put("recentPlayers", Players.adminPage(db, Map.of("size", "5", "page", "1")).get("items"));
            data.put("runtime", runtime);
            ctx.ok(data);
        });

        registerUsers(app);
        registerRoles(app);

        router.get("/api/admin/players", ctx -> {
            Auth.requirePermission(ctx, "skin:player:view");
            ctx.ok(Players.adminPage(app.db, ctx.query));
        });

        router.patch("/api/admin/players/:id", ctx -> {
            Auth.requirePermission(ctx, "skin:player:edit");
            Map<String, Object> player = targetPlayer(app, ctx.param("id"));
            Map<String, Object> body = ctx.json();
            if (body.containsKey("name")) player = Players.rename(app.db, player, Authz.str(body.get("name")));
            if (body.containsKey("model")) player = Players.setModel(app.db, player, Authz.str(body.get("model")));
            if (body.containsKey("userId")) {
                String userId = Authz.str(body.get("userId")).trim();
                if (userId.isEmpty()) throw Web.badRequest("所属用户不合法");
                player = Players.reassign(app.db, player, userId);
            }
            ctx.ok(Map.of("player", Players.serialize(player, ctx.user)));
        });

        router.delete("/api/admin/players/:id", ctx -> {
            Auth.requirePermission(ctx, "skin:player:delete");
            Map<String, Object> player = targetPlayer(app, ctx.param("id"));
            Players.delete(app.db, player);
            ctx.ok(Map.of("deleted", true));
        });

        router.get("/api/admin/settings", ctx -> {
            Auth.requirePermission(ctx, "system:setting:view");
            ctx.ok(settingsPayload(app));
        });

        router.put("/api/admin/settings", ctx -> {
            Auth.requirePermission(ctx, "system:setting:edit");
            Settings.update(app.db, ctx.json());
            ctx.ok(settingsPayload(app));
        });

        router.post("/api/admin/settings/banner", ctx -> {
            Auth.requirePermission(ctx, "system:setting:edit");
            byte[] image = ctx.body((int) Branding.MAX_BYTES);
            Branding.save(app.config, app.db, image);
            ctx.ok(settingsPayload(app));
        });

        router.delete("/api/admin/settings/banner", ctx -> {
            Auth.requirePermission(ctx, "system:setting:edit");
            Branding.remove(app.config, app.db);
            ctx.ok(settingsPayload(app));
        });

        router.delete("/api/admin/settings/banner/:file", ctx -> {
            Auth.requirePermission(ctx, "system:setting:edit");
            Branding.remove(app.config, app.db, ctx.param("file"));
            ctx.ok(settingsPayload(app));
        });

        router.get("/api/admin/export", ctx -> {
            Auth.requirePermission(ctx, "system:setting:view");
            Map<String, Object> data = export(app.db);
            ctx.sendJson(200, data, Map.of("Content-Disposition",
                    "attachment; filename=\"swust-skin-export-" + Instant.now().toString().substring(0, 10) + ".json\""));
        });
    }

    private static void registerUsers(App app) {
        Web.Router router = app.router;

        router.get("/api/admin/users", ctx -> {
            Auth.requirePermission(ctx, "system:user:view");
            ctx.ok(Users.page(app.db, ctx.query));
        });

        router.get("/api/admin/permissions", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            if (!user.can("system:user:view") && !user.can("system:role:view")) {
                throw Web.forbidden("没有权限查看权限目录");
            }
            ctx.ok(Map.of("items", Roles.permissions(app.db)));
        });

        router.get("/api/admin/users/:id", ctx -> {
            Auth.requirePermission(ctx, "system:user:view");
            AuthUser user = targetUser(app, ctx.param("id"));
            ctx.ok(Map.of("user", user.toMap()));
        });

        router.post("/api/admin/users", ctx -> {
            Auth.requirePermission(ctx, "system:user:create");
            Map<String, Object> body = ctx.json();
            assertCanGrantRoles(app.db, ctx.user, body.get("roleIds"));
            AuthUser user = Users.create(app.db, app.config, body);
            ctx.ok(Map.of("user", user.toMap()), 201);
        });

        router.patch("/api/admin/users/:id", ctx -> {
            Auth.requirePermission(ctx, "system:user:edit");
            AuthUser target = targetUser(app, ctx.param("id"));
            assertCanManage(ctx.user, target);
            Map<String, Object> body = ctx.json();
            Map<String, Object> payload = new LinkedHashMap<>();
            for (String field : List.of("username", "nickname", "email", "phone", "qq", "emailVerified", "playerLimit")) {
                if (body.containsKey(field)) payload.put(field, body.get(field));
            }
            AuthUser updated = Users.update(app.db, target.id, payload);
            ctx.ok(Map.of("user", updated.toMap()));
        });

        router.put("/api/admin/users/:id/roles", ctx -> {
            Auth.requirePermission(ctx, "system:user:assign-role");
            AuthUser target = targetUser(app, ctx.param("id"));
            assertCanManage(ctx.user, target);
            Map<String, Object> body = ctx.json();
            List<String> roleIds = Users.stringList(body.get("roleIds"));
            assertCanGrantRoles(app.db, ctx.user, roleIds);
            AuthUser updated = Users.update(app.db, target.id, Map.of("roleIds", roleIds));
            ctx.ok(Map.of("user", updated.toMap()));
        });

        router.put("/api/admin/users/:id/depts", ctx -> {
            Auth.requirePermission(ctx, "system:user:assign-dept");
            AuthUser target = targetUser(app, ctx.param("id"));
            assertCanManage(ctx.user, target);
            Map<String, Object> body = ctx.json();
            AuthUser updated = Users.update(app.db, target.id,
                    Map.of("depts", Users.mapList(body.get("depts"))));
            ctx.ok(Map.of("user", updated.toMap()));
        });

        router.put("/api/admin/users/:id/permissions", ctx -> {
            Auth.requirePermission(ctx, "system:user:edit");
            AuthUser target = targetUser(app, ctx.param("id"));
            assertCanManage(ctx.user, target);
            List<String> codes = Users.stringList(ctx.json().get("permissions"));
            assertCanGrantPermissions(ctx.user, codes);
            Authz.setUserExtraPermissions(app.db, target.id, codes);
            AuthUser updated = targetUser(app, target.id);
            ctx.ok(Map.of("user", updated.toMap()));
        });

        router.put("/api/admin/users/:id/password", ctx -> {
            Auth.requirePermission(ctx, "system:user:edit");
            AuthUser target = targetUser(app, ctx.param("id"));
            if (target.id.equals(ctx.user.id)) throw Web.badRequest("请在个人中心修改自己的密码");
            assertCanManage(ctx.user, target);
            String password = Authz.str(ctx.json().get("password"));
            String passwordError = Security.validatePassword(password);
            if (passwordError != null) throw new Web.HttpError(400, "invalid_password", passwordError);
            String now = Security.isoNow();
            app.db.update("""
                    UPDATE users SET password = ?, password_changed_time = ?, update_time = ?,
                      version = version + 1 WHERE id = ?
                    """, Security.hashPassword(password), now, now, target.id);
            app.db.update("DELETE FROM sessions WHERE user_id = ?", target.id);
            app.db.update("DELETE FROM yggdrasil_tokens WHERE user_id = ?", target.id);
            ctx.ok(Map.of("ok", true));
        });

        router.delete("/api/admin/users/:id", ctx -> {
            Auth.requirePermission(ctx, "system:user:delete");
            AuthUser target = targetUser(app, ctx.param("id"));
            if (!target.id.equals(ctx.user.id)) assertCanManage(ctx.user, target);
            AuthUser updated = Users.setStatus(app.db, target.id, "DISABLED", ctx.user.id);
            ctx.ok(Map.of("user", updated.toMap()));
        });

        router.post("/api/admin/users/:id/enable", ctx -> {
            Auth.requirePermission(ctx, "system:user:edit");
            AuthUser target = targetUser(app, ctx.param("id"));
            if (!target.id.equals(ctx.user.id)) assertCanManage(ctx.user, target);
            AuthUser updated = Users.setStatus(app.db, target.id, "ACTIVE", ctx.user.id);
            ctx.ok(Map.of("user", updated.toMap()));
        });

        router.delete("/api/admin/users/:id/permanent", ctx -> {
            Auth.requirePermission(ctx, "system:user:purge");
            AuthUser target = targetUser(app, ctx.param("id"));
            if (!target.id.equals(ctx.user.id)) assertCanManage(ctx.user, target);
            Users.permanentDelete(app.db, target.id, ctx.user.id);
            ctx.ok(Map.of("deleted", true));
        });
    }

    private static void registerRoles(App app) {
        Web.Router router = app.router;

        router.get("/api/admin/roles", ctx -> {
            Auth.requirePermission(ctx, "system:role:view");
            Map<String, Object> data = Roles.page(app.db, ctx.query);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
            List<Map<String, Object>> enriched = new ArrayList<>();
            for (Map<String, Object> role : items) {
                Map<String, Object> item = new LinkedHashMap<>(role);
                item.put("userCount", Roles.usedCount(app.db, role.get("id")));
                enriched.add(item);
            }
            data.put("items", enriched);
            ctx.ok(data);
        });

        router.get("/api/admin/roles/options", ctx -> {
            Auth.requirePermission(ctx, "system:role:view");
            ctx.ok(Map.of("items", Users.roleOptions(app.db)));
        });

        router.get("/api/admin/roles/permissions", ctx -> {
            Auth.requirePermission(ctx, "system:role:view");
            ctx.ok(Map.of("items", Roles.permissions(app.db)));
        });

        router.get("/api/admin/departments", ctx -> {
            Auth.requirePermission(ctx, "system:dept:view");
            ctx.ok(Map.of("items", Roles.departments(app.db)));
        });

        router.post("/api/admin/roles", ctx -> {
            Auth.requirePermission(ctx, "system:role:create");
            Map<String, Object> body = ctx.json();
            assertLevelAllowed(ctx.user, Authz.str(body.getOrDefault("level", "USER")).toUpperCase());
            Map<String, Object> role = Roles.create(app.db, app.config, body);
            ctx.ok(Map.of("role", Authz.roleMap(role)), 201);
        });

        router.put("/api/admin/roles/:id", ctx -> {
            Auth.requirePermission(ctx, "system:role:edit");
            assertRoleManageable(app, ctx.user, ctx.param("id"));
            Map<String, Object> body = ctx.json();
            if (body.containsKey("level")) {
                assertLevelAllowed(ctx.user, Authz.str(body.getOrDefault("level", "USER")).toUpperCase());
            }
            Map<String, Object> role = Roles.update(app.db, ctx.param("id"), body);
            ctx.ok(Map.of("role", Authz.roleMap(role)));
        });

        router.delete("/api/admin/roles/:id", ctx -> {
            Auth.requirePermission(ctx, "system:role:delete");
            assertRoleManageable(app, ctx.user, ctx.param("id"));
            Map<String, Object> role = Roles.disable(app.db, ctx.param("id"));
            ctx.ok(Map.of("role", Authz.roleMap(role)));
        });

        router.post("/api/admin/roles/:id/enable", ctx -> {
            Auth.requirePermission(ctx, "system:role:edit");
            assertRoleManageable(app, ctx.user, ctx.param("id"));
            Map<String, Object> role = Roles.enable(app.db, ctx.param("id"));
            ctx.ok(Map.of("role", Authz.roleMap(role)));
        });

        router.get("/api/admin/roles/:id", ctx -> {
            Auth.requirePermission(ctx, "system:role:view");
            Map<String, Object> role = Roles.row(app.db, ctx.param("id"));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("role", role == null ? null : Authz.roleMap(role));
            ctx.ok(data);
        });
    }

    private static AuthUser targetUser(App app, String id) {
        AuthUser user = Authz.authUser(app.db, app.db.one("SELECT * FROM users WHERE id = ?", id));
        if (user == null) throw Web.notFound("用户不存在");
        return user;
    }

    private static Map<String, Object> targetPlayer(App app, String rawId) {
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

    private static void assertCanGrantPermissions(AuthUser operator, List<String> codes) {
        if (operator.wildcard) return;
        for (String code : codes) {
            if ("*".equals(code)) throw Web.forbidden("不能授予通配权限");
            if (!operator.can(code)) throw Web.forbidden("不能授予自己不具备的权限：" + code);
        }
    }

    private static void assertCanManage(AuthUser operator, AuthUser target) {
        if (operator.id.equals(target.id)) return;
        if (operator.levelWeight < target.levelWeight) {
            throw Web.forbidden("不能操作同级或更高权限的用户");
        }
    }

    private static void assertCanGrantRoles(Database db, AuthUser operator, Object roleIdsValue) {
        if (operator.wildcard) return;
        Set<String> ids = new LinkedHashSet<>(Users.stringList(roleIdsValue));
        if (ids.isEmpty()) return;
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        List<Map<String, Object>> roles = db.query(
                "SELECT level FROM roles WHERE id IN (" + placeholders + ")", ids.toArray());
        int maxLevel = 0;
        for (Map<String, Object> role : roles) {
            maxLevel = Math.max(maxLevel, Authz.LEVELS.getOrDefault(Authz.str(role.get("level")), 0));
        }
        if (maxLevel > operator.levelWeight) throw Web.forbidden("不能分配高于自身等级的角色");
    }

    private static void assertLevelAllowed(AuthUser operator, String level) {
        if (operator.wildcard) return;
        if (Authz.LEVELS.getOrDefault(level, 0) > operator.levelWeight) {
            throw Web.forbidden("不能创建或管理高于自身等级的角色");
        }
    }

    private static void assertRoleManageable(App app, AuthUser operator, String id) {
        Map<String, Object> role = Roles.row(app.db, id);
        if (role == null) return;
        if (!operator.wildcard && Authz.LEVELS.getOrDefault(Authz.str(role.get("level")), 0) > operator.levelWeight) {
            throw Web.forbidden("不能操作高于自身等级的角色");
        }
    }

    private static Map<String, Object> settingsPayload(App app) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("settings", Settings.all(app.db));
        data.put("public", Settings.publicMap(app.db));
        return data;
    }

    private static int count(Database db, String sql) {
        Map<String, Object> row = db.one(sql);
        return row == null ? 0 : Authz.num(row.get("count"));
    }

    private static Map<String, Object> export(Database db) {
        List<Map<String, Object>> users = db.query("SELECT * FROM users ORDER BY create_time ASC");
        Map<String, List<String>> userRoles = new LinkedHashMap<>();
        for (Map<String, Object> row : db.query("SELECT user_id, role_id FROM user_roles")) {
            userRoles.computeIfAbsent(Authz.str(row.get("user_id")), key -> new ArrayList<>())
                    .add(Authz.str(row.get("role_id")));
        }
        Map<String, List<String>> userExtraPermissions = new LinkedHashMap<>();
        for (Map<String, Object> row : db.query("SELECT user_id, permission FROM user_permissions")) {
            userExtraPermissions.computeIfAbsent(Authz.str(row.get("user_id")), key -> new ArrayList<>())
                    .add(Authz.str(row.get("permission")));
        }
        Map<String, List<Map<String, Object>>> userDepts = new LinkedHashMap<>();
        for (Map<String, Object> row : db.query("SELECT user_id, dept_id, is_default FROM user_depts")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deptId", Authz.str(row.get("dept_id")));
            item.put("defaultDept", Authz.bool(row.get("is_default")));
            userDepts.computeIfAbsent(Authz.str(row.get("user_id")), key -> new ArrayList<>()).add(item);
        }
        List<Map<String, Object>> sysUser = users.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", Authz.str(row.get("id")));
            item.put("version", Authz.num(row.get("version")));
            item.put("createTime", Authz.str(row.get("create_time")));
            item.put("updateTime", Authz.str(row.get("update_time")));
            item.put("username", Authz.str(row.get("username")));
            item.put("nickname", row.get("nickname") == null ? null : Authz.str(row.get("nickname")));
            item.put("email", row.get("email") == null ? null : Authz.str(row.get("email")));
            item.put("phone", row.get("phone") == null ? null : Authz.str(row.get("phone")));
            item.put("qq", row.get("qq") == null ? null : Authz.str(row.get("qq")));
            item.put("password", Authz.str(row.get("password")));
            String avatarFileId = row.get("avatar_file_id") == null ? null : Authz.str(row.get("avatar_file_id"));
            item.put("avatarFileId", avatarFileId);
            item.put("avatar", Avatars.url(avatarFileId));
            item.put("emailVerified", Authz.bool(row.get("email_verified")));
            item.put("status", Authz.str(row.get("status")));
            item.put("depts", userDepts.getOrDefault(Authz.str(row.get("id")), List.of()));
            item.put("roleIds", userRoles.getOrDefault(Authz.str(row.get("id")), List.of()));
            item.put("extraPermissions", userExtraPermissions.getOrDefault(Authz.str(row.get("id")), List.of()));
            item.put("tags", Authz.parseJson(row.get("tags"), List.of()));
            item.put("fields", Authz.parseJson(row.get("fields"), Map.of()));
            return item;
        }).toList();

        List<Map<String, Object>> sysRole = db.query(
                "SELECT * FROM roles ORDER BY system_role DESC, create_time ASC").stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", Authz.str(row.get("id")));
            item.put("version", Authz.num(row.get("version")));
            item.put("createTime", Authz.str(row.get("create_time")));
            item.put("updateTime", Authz.str(row.get("update_time")));
            item.put("name", Authz.str(row.get("name")));
            item.put("deptId", row.get("dept_id") == null ? null : Authz.str(row.get("dept_id")));
            item.put("code", Authz.str(row.get("code")));
            item.put("level", Authz.str(row.get("level")));
            item.put("systemRole", Authz.bool(row.get("system_role")));
            item.put("systemType", row.get("system_type") == null ? null : Authz.str(row.get("system_type")));
            item.put("icon", row.get("icon") == null ? null : Authz.str(row.get("icon")));
            item.put("color", row.get("color") == null ? null : Authz.str(row.get("color")));
            item.put("permissions", Authz.parseJson(row.get("permissions"), List.of()));
            item.put("status", Authz.str(row.get("status")));
            return item;
        }).toList();

        List<Map<String, Object>> sysDept = db.query(
                "SELECT * FROM departments ORDER BY sort_order ASC, id ASC").stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", Authz.str(row.get("id")));
            item.put("version", Authz.num(row.get("version")));
            item.put("createTime", Authz.str(row.get("create_time")));
            item.put("updateTime", Authz.str(row.get("update_time")));
            item.put("name", Authz.str(row.get("name")));
            item.put("description", row.get("description") == null ? null : Authz.str(row.get("description")));
            item.put("leaderId", row.get("leader_id") == null ? null : Authz.str(row.get("leader_id")));
            item.put("phone", row.get("phone") == null ? null : Authz.str(row.get("phone")));
            item.put("avatarFileId", row.get("avatar_file_id") == null ? null : Authz.str(row.get("avatar_file_id")));
            item.put("color", row.get("color") == null ? null : Authz.str(row.get("color")));
            item.put("parentId", row.get("parent_id") == null ? null : Authz.str(row.get("parent_id")));
            item.put("sortOrder", Authz.num(row.get("sort_order")));
            item.put("deptType", Authz.str(row.get("dept_type")));
            item.put("status", Authz.str(row.get("status")));
            return item;
        }).toList();

        List<Map<String, Object>> sysPermission = db.query(
                "SELECT * FROM permissions ORDER BY module ASC, code ASC").stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", Authz.str(row.get("id")));
            item.put("version", Authz.num(row.get("version")));
            item.put("createTime", Authz.str(row.get("create_time")));
            item.put("updateTime", Authz.str(row.get("update_time")));
            item.put("code", Authz.str(row.get("code")));
            item.put("name", Authz.str(row.get("name")));
            item.put("module", Authz.str(row.get("module")));
            item.put("description", row.get("description") == null ? null : Authz.str(row.get("description")));
            item.put("status", Authz.str(row.get("status")));
            item.put("source", Authz.str(row.get("source")));
            return item;
        }).toList();

        Map<String, Object> collections = new LinkedHashMap<>();
        collections.put("sysUser", sysUser);
        collections.put("sysRole", sysRole);
        collections.put("sysDept", sysDept);
        collections.put("sysPermission", sysPermission);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("sysUser", sysUser.size());
        counts.put("sysRole", sysRole.size());
        counts.put("sysDept", sysDept.size());
        counts.put("sysPermission", sysPermission.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", "swust-skin-admin");
        result.put("version", 1);
        result.put("exportedAt", Security.isoNow());
        result.put("idType", "int64-as-string");
        result.put("collections", collections);
        result.put("counts", counts);
        return result;
    }
}

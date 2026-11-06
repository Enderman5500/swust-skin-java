package cn.edu.swust.mc.skin;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ApiKeys {
    public record AuthResult(Map<String, Object> key, AuthUser user, String id) {
    }

    private ApiKeys() {
    }

    public static Map<String, Object> create(Database db, Config config, AuthUser target,
                                             String name, String scope, List<String> requested,
                                             Integer expiresInDays) {
        String cleanName = Security.sanitize(name, 60);
        if (cleanName.isEmpty()) throw Web.badRequest("请填写 API 密钥名称");
        List<String> permissions = resolvePermissions(db, target, scope, requested);
        String expiresAt = null;
        if (expiresInDays != null && expiresInDays > 0) {
            if (expiresInDays > 3650) throw Web.badRequest("有效期不能超过 3650 天");
            expiresAt = Instant.now().plusSeconds(expiresInDays * 86400L).toString();
        }
        String token = "sk_" + Security.randomToken(32);
        String prefix = token.substring(0, Math.min(12, token.length()));
        String id = Security.nextId(config.snowflakeDataCenter, config.snowflakeMachine);
        db.update("""
                INSERT INTO api_keys
                (id, user_id, name, key_prefix, key_hash, permissions, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, target.id, cleanName, prefix, Security.sha256(token),
                Json.stringify(permissions), Security.isoNow(), expiresAt);
        Map<String, Object> row = db.one("""
                SELECT k.*, u.username AS owner_username, u.nickname AS owner_nickname
                FROM api_keys k LEFT JOIN users u ON u.id = k.user_id WHERE k.id = ?
                """, id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("apiKey", serialize(row));
        return result;
    }

    public static List<Map<String, Object>> listForUser(Database db, String userId) {
        return db.query("""
                SELECT k.*, u.username AS owner_username, u.nickname AS owner_nickname
                FROM api_keys k LEFT JOIN users u ON u.id = k.user_id
                WHERE k.user_id = ? ORDER BY k.created_at DESC
                """, userId).stream().map(ApiKeys::serialize).toList();
    }

    public static List<Map<String, Object>> listAll(Database db) {
        return db.query("""
                SELECT k.*, u.username AS owner_username, u.nickname AS owner_nickname
                FROM api_keys k LEFT JOIN users u ON u.id = k.user_id
                ORDER BY k.created_at DESC
                """).stream().map(ApiKeys::serialize).toList();
    }

    public static Map<String, Object> row(Database db, String id) {
        return db.one("SELECT * FROM api_keys WHERE id = ?", id);
    }

    public static void delete(Database db, String id) {
        if (db.one("SELECT id FROM api_keys WHERE id = ?", id) == null) {
            throw Web.notFound("API 密钥不存在");
        }
        db.update("DELETE FROM api_keys WHERE id = ?", id);
    }

    public static AuthResult authenticate(Database db, String token) {
        if (token == null || token.isBlank() || !token.startsWith("sk_")) return null;
        Map<String, Object> row = db.one("""
                SELECT k.id AS api_key_id, k.user_id AS api_key_user_id, k.name AS api_key_name,
                       k.key_prefix, k.permissions AS api_key_permissions,
                       k.created_at AS api_key_created_at, k.expires_at AS api_key_expires_at,
                       k.last_used_at AS api_key_last_used_at, u.*
                FROM api_keys k JOIN users u ON u.id = k.user_id
                WHERE k.key_hash = ?
                """, Security.sha256(token));
        if (row == null) return null;
        if (!"ACTIVE".equals(Authz.str(row.get("status")))) return null;
        String expiresAt = Authz.str(row.get("api_key_expires_at"));
        if (!expiresAt.isEmpty() && expiresAt.compareTo(Security.isoNow()) <= 0) return null;
        db.update("UPDATE api_keys SET last_used_at = ? WHERE id = ?",
                Security.isoNow(), row.get("api_key_id"));
        AuthUser base = Authz.authUser(db, row);
        List<String> keyPermissions = parsePermissions(row.get("api_key_permissions"));
        AuthUser scoped = scoped(base, keyPermissions);
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("id", Authz.str(row.get("api_key_id")));
        key.put("name", Authz.str(row.get("api_key_name")));
        key.put("prefix", Authz.str(row.get("key_prefix")));
        key.put("permissions", keyPermissions);
        key.put("expiresAt", expiresAt.isEmpty() ? null : expiresAt);
        key.put("lastUsedAt", row.get("api_key_last_used_at") == null ? null : Authz.str(row.get("api_key_last_used_at")));
        return new AuthResult(key, scoped, Authz.str(row.get("api_key_id")));
    }

    public static boolean allowedPath(String method, String path) {
        String upper = method == null ? "GET" : method.toUpperCase();
        if ("/api/plugins".equals(path) || path.startsWith("/api/plugins/")) return true;
        if (path.startsWith("/api/admin/")) return true;
        if (!"GET".equals(upper)) return false;
        if (path.equals("/api/health") || path.equals("/api/home") || path.equals("/api/stats")) return true;
        if (path.startsWith("/api/textures")) return true;
        return path.startsWith("/textures/");
    }

    private static List<String> resolvePermissions(Database db, AuthUser target, String scope, List<String> requested) {
        String normalizedScope = scope == null || scope.isBlank() ? "all" : scope.trim().toLowerCase();
        List<String> catalog = Authz.allPermissions(db).stream()
                .map(row -> Authz.str(row.get("code"))).toList();
        Set<String> base = new LinkedHashSet<>();
        if (target.wildcard) {
            base.addAll(catalog);
        } else {
            for (String permission : target.permissions) {
                if (!"*".equals(permission)) base.add(permission);
            }
        }
        List<String> resolved = new ArrayList<>();
        switch (normalizedScope) {
            case "all" -> resolved.addAll(base);
            case "plugin" -> base.stream().filter(code -> code.startsWith("plugin:")).forEach(resolved::add);
            case "read" -> base.stream().filter(code -> code.endsWith(":view")).forEach(resolved::add);
            case "custom" -> {
                for (String code : requested == null ? List.<String>of() : requested) {
                    String value = Authz.str(code).trim();
                    if (value.isEmpty()) continue;
                    if ("*".equals(value)) throw Web.badRequest("API 密钥不能使用通配权限");
                    if (!base.contains(value)) {
                        throw Web.badRequest("该用户不具备权限：" + value);
                    }
                    if (!resolved.contains(value)) resolved.add(value);
                }
            }
            default -> throw Web.badRequest("API 密钥权限范围不合法");
        }
        return resolved.size() > 200 ? resolved.subList(0, 200) : resolved;
    }

    private static AuthUser scoped(AuthUser base, List<String> allowed) {
        AuthUser user = new AuthUser();
        user.id = base.id;
        user.username = base.username;
        user.nickname = base.nickname;
        user.email = base.email;
        user.phone = base.phone;
        user.qq = base.qq;
        user.avatarFileId = base.avatarFileId;
        user.emailVerified = base.emailVerified;
        user.status = base.status;
        user.createTime = base.createTime;
        user.updateTime = base.updateTime;
        user.lastLoginTime = base.lastLoginTime;
        user.tags = base.tags;
        user.fields = base.fields;
        user.roles = base.roles;
        user.depts = base.depts;
        user.roleIds = base.roleIds;
        user.roleNames = base.roleNames;
        user.deptIds = base.deptIds;
        user.deptNames = base.deptNames;
        user.defaultDeptId = base.defaultDeptId;
        Set<String> permissions = new LinkedHashSet<>();
        for (String code : allowed) {
            if (base.wildcard || base.permissions.contains(code)) permissions.add(code);
        }
        user.permissions = permissions;
        user.rolePermissions = new LinkedHashSet<>();
        user.extraPermissions = permissions;
        user.wildcard = false;
        user.admin = false;
        user.levelWeight = base.levelWeight;
        return user;
    }

    private static Map<String, Object> serialize(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", Authz.str(row.get("id")));
        item.put("name", Authz.str(row.get("name")));
        item.put("prefix", Authz.str(row.get("key_prefix")));
        item.put("permissions", parsePermissions(row.get("permissions")));
        item.put("expiresAt", row.get("expires_at") == null ? null : Authz.str(row.get("expires_at")));
        item.put("lastUsedAt", row.get("last_used_at") == null ? null : Authz.str(row.get("last_used_at")));
        item.put("createdAt", Authz.str(row.get("created_at")));
        String userId = Authz.str(row.get("user_id"));
        if (userId.isEmpty()) {
            item.put("owner", null);
        } else {
            Map<String, Object> owner = new LinkedHashMap<>();
            owner.put("id", userId);
            owner.put("username", Authz.str(row.get("owner_username")));
            owner.put("nickname", Authz.str(row.get("owner_nickname")));
            item.put("owner", owner);
        }
        return item;
    }

    private static List<String> parsePermissions(Object value) {
        if (value == null) return List.of();
        try {
            List<?> parsed = Json.MAPPER.readValue(Authz.str(value), new TypeReference<List<Object>>() {
            });
            List<String> result = new ArrayList<>();
            for (Object item : parsed) {
                if (item != null) result.add(String.valueOf(item));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
}

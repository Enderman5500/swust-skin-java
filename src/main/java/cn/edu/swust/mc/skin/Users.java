package cn.edu.swust.mc.skin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Users {
    private Users() {
    }

    public static AuthUser authUser(Database db, String id) {
        return Authz.authUser(db, db.one("SELECT * FROM users WHERE id = ?", String.valueOf(id)));
    }

    public static Map<String, Object> page(Database db, Map<String, String> query) {
        int page = Math.max(1, parseInt(query.get("page"), 1));
        int perPage = Math.max(1, Math.min(100, parseInt(first(query, "perPage", "size"), 30)));
        int offset = (page - 1) * perPage;
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String keyword = first(query, "keyword", "q").trim();
        if (!keyword.isEmpty()) {
            conditions.add("(u.username LIKE ? ESCAPE '\\' OR u.nickname LIKE ? ESCAPE '\\' "
                    + "OR u.email LIKE ? ESCAPE '\\' OR u.phone LIKE ? ESCAPE '\\' OR u.qq LIKE ? ESCAPE '\\')");
            String pattern = Security.likePattern(keyword);
            for (int i = 0; i < 5; i++) params.add(pattern);
        }
        String status = query.get("status");
        if ("ACTIVE".equals(status) || "DISABLED".equals(status)) {
            conditions.add("u.status = ?");
            params.add(status);
        }
        String roleId = query.get("roleId");
        if (roleId != null && !roleId.isBlank()) {
            conditions.add("EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = ?)");
            params.add(roleId);
        }
        String deptId = query.get("deptId");
        if (deptId != null && !deptId.isBlank()) {
            conditions.add("EXISTS (SELECT 1 FROM user_depts ud WHERE ud.user_id = u.id AND ud.dept_id = ?)");
            params.add(deptId);
        }
        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        Map<String, Object> count = db.one("SELECT COUNT(*) AS count FROM users u " + where, params.toArray());
        int total = count == null ? 0 : Authz.num(count.get("count"));
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(perPage);
        pageParams.add(offset);
        List<Map<String, Object>> rows = db.query("""
                SELECT u.*,
                  (SELECT COUNT(*) FROM players p WHERE p.user_id = u.id) AS player_count,
                  (SELECT COUNT(*) FROM textures t WHERE t.uploaded_by = u.id) AS texture_count
                FROM users u %s
                ORDER BY u.create_time DESC LIMIT ? OFFSET ?
                """.formatted(where), pageParams.toArray());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            AuthUser user = Authz.authUser(db, row);
            Map<String, Object> item = user.toMap();
            item.put("playerCount", Authz.num(row.get("player_count")));
            item.put("textureCount", Authz.num(row.get("texture_count")));
            items.add(item);
        }
        return Page.of(items, total, page, perPage);
    }

    private static void ensureUniqueIdentity(Database db, String username, String email, String excludeId) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (username != null && !username.isEmpty()) {
            conditions.add("username = ? COLLATE NOCASE");
            params.add(username);
        }
        if (email != null && !email.isEmpty()) {
            conditions.add("email = ? COLLATE NOCASE");
            params.add(email);
        }
        if (conditions.isEmpty()) return;
        String sql = "SELECT id, username, email FROM users WHERE (" + String.join(" OR ", conditions) + ")";
        if (excludeId != null) {
            sql += " AND id <> ?";
            params.add(excludeId);
        }
        Map<String, Object> duplicate = db.one(sql, params.toArray());
        if (duplicate == null) return;
        if (username != null && username.equalsIgnoreCase(Authz.str(duplicate.get("username")))) {
            throw Web.conflict("用户名已存在");
        }
        throw Web.conflict("邮箱已存在");
    }

    private static void validateProfile(String username, String email, String phone, String qq) {
        if (!Security.validUsername(username)) throw Web.badRequest("用户名需为 3–16 位字母、数字或下划线");
        if (!Security.validEmail(email)) throw Web.badRequest("邮箱格式不正确");
        if (phone != null && !phone.isEmpty() && !phone.matches("^1[3-9]\\d{9}$")) {
            throw Web.badRequest("手机号格式不正确");
        }
        if (qq != null && !qq.isEmpty() && !qq.matches("^\\d{5,12}$")) {
            throw Web.badRequest("QQ 号格式不正确");
        }
    }

    private static int parsePlayerLimit(Object value) {
        String text = Authz.str(value).trim();
        if (!text.matches("\\d{1,3}")) {
            throw Web.badRequest("可创建角色数需为 0–" + Players.MAX_PER_USER + " 的整数");
        }
        int limit = Integer.parseInt(text);
        if (limit > Players.MAX_PER_USER) {
            throw Web.badRequest("可创建角色数需为 0–" + Players.MAX_PER_USER + " 的整数");
        }
        return limit;
    }

    public static AuthUser create(Database db, Config config, Map<String, Object> input) {
        String username = Authz.str(input.get("username")).trim();
        String nickname = Security.sanitize(Authz.str(input.getOrDefault("nickname", username)), 64);
        if (nickname.isEmpty()) nickname = username;
        String email = Authz.str(input.get("email")).trim().toLowerCase();
        String phone = Security.sanitize(Authz.str(input.get("phone")), 20);
        String qq = Security.sanitize(Authz.str(input.get("qq")), 20);
        String password = Authz.str(input.get("password"));
        validateProfile(username, email, phone, qq);
        String passwordError = Security.validatePassword(password);
        if (passwordError != null) throw Web.badRequest(passwordError);
        ensureUniqueIdentity(db, username, email, null);
        String now = Security.isoNow();
        String id = Security.nextId(config.snowflakeDataCenter, config.snowflakeMachine);
        int playerLimit = input.containsKey("playerLimit")
                ? parsePlayerLimit(input.get("playerLimit"))
                : Players.DEFAULT_PER_USER;
        db.update("""
                INSERT INTO users
                (id, username, nickname, email, phone, qq, password, avatar_file_id, email_verified, status,
                 player_limit, tags, fields, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, 'ACTIVE', ?, '[]', '{}', 0, ?, ?)
                """, id, username, nickname, email,
                phone.isEmpty() ? null : phone, qq.isEmpty() ? null : qq,
                Security.hashPassword(password), Boolean.FALSE.equals(input.get("emailVerified")) ? 0 : 1,
                playerLimit, now, now);
        List<Map<String, Object>> depts = mapList(input.get("depts"));
        if (depts.isEmpty()) {
            depts = List.of(Map.of("deptId", Authz.ROOT_DEPT, "defaultDept", true));
        }
        Authz.setUserDepts(db, id, depts);
        List<String> roleIds = stringList(input.get("roleIds"));
        if (roleIds.isEmpty()) roleIds = List.of(Authz.USER_ID);
        Authz.assignRoles(db, id, roleIds, true);
        return Authz.authUser(db, db.one("SELECT * FROM users WHERE id = ?", id));
    }

    @SuppressWarnings("unchecked")
    public static AuthUser update(Database db, String userId, Map<String, Object> input) {
        Map<String, Object> row = db.one("SELECT * FROM users WHERE id = ?", userId);
        if (row == null) throw Web.notFound("用户不存在");
        AuthUser user = Authz.authUser(db, row);
        String username = input.containsKey("username") ? Authz.str(input.get("username")).trim() : user.username;
        String nickname = input.containsKey("nickname") ? Security.sanitize(Authz.str(input.get("nickname")), 64) : user.nickname;
        String email = input.containsKey("email") ? Authz.str(input.get("email")).trim().toLowerCase() : user.email;
        String phone = input.containsKey("phone") ? Security.sanitize(Authz.str(input.get("phone")), 20) : user.phone;
        String qq = input.containsKey("qq") ? Security.sanitize(Authz.str(input.get("qq")), 20) : user.qq;
        validateProfile(username, email, phone, qq);
        ensureUniqueIdentity(db, username, email, user.id);
        int emailVerified = input.containsKey("emailVerified")
                ? (Authz.bool(input.get("emailVerified")) ? 1 : 0)
                : (user.emailVerified ? 1 : 0);
        String now = Security.isoNow();
        int playerLimit = input.containsKey("playerLimit")
                ? parsePlayerLimit(input.get("playerLimit"))
                : row.get("player_limit") == null
                        ? Players.DEFAULT_PER_USER
                        : Authz.num(row.get("player_limit"));
        db.update("""
                UPDATE users SET username = ?, nickname = ?, email = ?, phone = ?, qq = ?,
                  email_verified = ?, player_limit = ?, update_time = ?, version = version + 1 WHERE id = ?
                """, username, nickname, email, phone.isEmpty() ? null : phone, qq.isEmpty() ? null : qq,
                emailVerified, playerLimit, now, user.id);

        if (input.containsKey("depts")) {
            List<Map<String, Object>> depts = mapList(input.get("depts"));
            Set<String> nextDeptIds = new LinkedHashSet<>();
            for (Map<String, Object> dept : depts) {
                nextDeptIds.add(Authz.str(dept.containsKey("deptId") ? dept.get("deptId") : dept.get("id")));
            }
            boolean keepsAdminDept = !user.admin;
            if (user.admin) {
                for (Map<String, Object> role : user.roles) {
                    if (Authz.ADMIN_TYPES.contains(Authz.str(role.get("systemType")))
                            && nextDeptIds.contains(Authz.str(role.get("deptId")))) {
                        keepsAdminDept = true;
                    }
                }
            }
            if (!keepsAdminDept && Authz.activeAdminCount(db, user.id) == 0) {
                throw Web.conflict("至少保留一名可用的管理员，不能移除其角色所属部门");
            }
            Authz.setUserDepts(db, user.id, depts);
        }
        if (input.containsKey("roleIds")) {
            List<String> nextRoles = stringList(input.get("roleIds"));
            boolean nextIsAdmin = false;
            if (!nextRoles.isEmpty()) {
                String placeholders = String.join(",", nextRoles.stream().map(id -> "?").toList());
                for (Map<String, Object> role : db.query("SELECT * FROM roles WHERE id IN (" + placeholders + ")",
                        nextRoles.toArray())) {
                    if ("ACTIVE".equals(Authz.str(role.get("status")))
                            && Authz.ADMIN_TYPES.contains(Authz.str(role.get("system_type")))) {
                        nextIsAdmin = true;
                    }
                }
            }
            if (user.admin && !nextIsAdmin && Authz.activeAdminCount(db, user.id) == 0) {
                throw Web.conflict("至少保留一名可用的管理员");
            }
            Authz.assignRoles(db, user.id, nextRoles, true);
        }
        return Authz.authUser(db, db.one("SELECT * FROM users WHERE id = ?", user.id));
    }

    public static AuthUser setStatus(Database db, String userId, String status, String operatorId) {
        Map<String, Object> row = db.one("SELECT * FROM users WHERE id = ?", userId);
        if (row == null) throw Web.notFound("用户不存在");
        AuthUser user = Authz.authUser(db, row);
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) throw Web.badRequest("账号状态不合法");
        if (user.id.equals(operatorId) && !"ACTIVE".equals(status)) throw Web.forbidden("不能停用自己的账号");
        if ("DISABLED".equals(status) && user.admin && Authz.activeAdminCount(db, user.id) == 0) {
            throw Web.conflict("至少保留一名可用的管理员");
        }
        db.update("UPDATE users SET status = ?, update_time = ?, version = version + 1 WHERE id = ?",
                status, Security.isoNow(), user.id);
        if ("DISABLED".equals(status)) {
            db.update("DELETE FROM sessions WHERE user_id = ?", user.id);
            db.update("DELETE FROM yggdrasil_tokens WHERE user_id = ?", user.id);
        }
        return Authz.authUser(db, db.one("SELECT * FROM users WHERE id = ?", user.id));
    }

    public static void permanentDelete(Database db, String userId, String operatorId) {
        Map<String, Object> row = db.one("SELECT * FROM users WHERE id = ?", userId);
        if (row == null) throw Web.notFound("用户不存在");
        AuthUser user = Authz.authUser(db, row);
        if (user.id.equals(operatorId)) throw Web.forbidden("不能删除自己的账号");
        if (user.admin && Authz.activeAdminCount(db, user.id) == 0) {
            throw Web.conflict("至少保留一名可用的管理员");
        }
        db.update("DELETE FROM users WHERE id = ?", user.id);
    }

    public static boolean canManage(AuthUser operator, AuthUser target) {
        if (operator == null || target == null) return false;
        if (operator.id.equals(target.id)) return false;
        return operator.admin && operator.levelWeight >= target.levelWeight;
    }

    public static List<Map<String, Object>> roleOptions(Database db) {
        return db.query("""
                SELECT r.*, d.name AS dept_name
                FROM roles r LEFT JOIN departments d ON d.id = r.dept_id
                WHERE r.status = 'ACTIVE'
                ORDER BY r.system_role DESC,
                  CASE r.level WHEN 'SUPER_ADMIN' THEN 100 WHEN 'ADMIN' THEN 80 WHEN 'USER' THEN 50 ELSE 20 END DESC,
                  r.name ASC
                """).stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", Authz.str(row.get("id")));
            item.put("name", Authz.str(row.get("name")));
            item.put("code", Authz.str(row.get("code")));
            item.put("level", Authz.str(row.get("level")));
            item.put("levelWeight", Authz.LEVELS.getOrDefault(Authz.str(row.get("level")), 0));
            item.put("deptId", row.get("dept_id") == null ? null : Authz.str(row.get("dept_id")));
            item.put("deptName", row.get("dept_name") == null ? null : Authz.str(row.get("dept_name")));
            item.put("systemRole", Authz.bool(row.get("system_role")));
            item.put("systemType", row.get("system_type") == null ? null : Authz.str(row.get("system_type")));
            item.put("color", row.get("color") == null ? null : Authz.str(row.get("color")));
            item.put("permissions", Authz.parseJson(row.get("permissions"), List.of()));
            item.put("status", Authz.str(row.get("status")));
            return item;
        }).toList();
    }

    public static List<Map<String, Object>> options(Database db, String activeOnly) {
        List<Map<String, Object>> users = db.query(
                "SELECT id, username, nickname FROM users WHERE status = 'ACTIVE' ORDER BY username ASC");
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : users) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", Authz.str(row.get("id")));
            item.put("username", Authz.str(row.get("username")));
            item.put("nickname", Authz.str(row.get("nickname")));
            items.add(item);
        }
        return items;
    }

    public static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static String first(Map<String, String> query, String... keys) {
        for (String key : keys) {
            String value = query.get(key);
            if (value != null) return value;
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> map) list.add((Map<String, Object>) map);
            }
        }
        return list;
    }

    public static List<String> stringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object item : items) list.add(Authz.str(item));
        }
        return list;
    }
}

package cn.edu.swust.mc.skin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Roles {
    private Roles() {
    }

    public static Map<String, Object> row(Database db, Object id) {
        return db.one("""
                SELECT r.*, d.name AS dept_name
                FROM roles r LEFT JOIN departments d ON d.id = r.dept_id
                WHERE r.id = ?
                """, String.valueOf(id));
    }

    public static int usedCount(Database db, Object id) {
        Map<String, Object> row = db.one("SELECT COUNT(*) AS count FROM user_roles WHERE role_id = ?", String.valueOf(id));
        return row == null ? 0 : Authz.num(row.get("count"));
    }

    public static Map<String, Object> page(Database db, Map<String, String> query) {
        int page = Math.max(1, Users.parseInt(query.get("page"), 1));
        int perPage = Math.max(1, Math.min(60, Users.parseInt(
                query.containsKey("perPage") ? query.get("perPage") : query.get("size"), 30)));
        int offset = (page - 1) * perPage;
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String keyword = query.getOrDefault("keyword", query.getOrDefault("q", "")).trim();
        if (!keyword.isEmpty()) {
            conditions.add("(r.name LIKE ? ESCAPE '\\' OR r.code LIKE ? ESCAPE '\\')");
            params.add(Security.likePattern(keyword));
            params.add(Security.likePattern(keyword));
        }
        String deptId = query.get("deptId");
        if (deptId != null && !deptId.isBlank()) {
            conditions.add("r.dept_id = ?");
            params.add(deptId);
        }
        String status = query.get("status");
        if ("ACTIVE".equals(status) || "DEPRECATED".equals(status)) {
            conditions.add("r.status = ?");
            params.add(status);
        }
        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        Map<String, Object> count = db.one("SELECT COUNT(*) AS count FROM roles r " + where, params.toArray());
        int total = count == null ? 0 : Authz.num(count.get("count"));
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(perPage);
        pageParams.add(offset);
        List<Map<String, Object>> rows = db.query("""
                SELECT r.*, d.name AS dept_name
                FROM roles r LEFT JOIN departments d ON d.id = r.dept_id
                %s
                ORDER BY r.system_role DESC,
                  CASE r.level WHEN 'SUPER_ADMIN' THEN 100 WHEN 'ADMIN' THEN 80 WHEN 'USER' THEN 50 ELSE 20 END DESC,
                  r.create_time ASC
                LIMIT ? OFFSET ?
                """.formatted(where), pageParams.toArray());
        List<Map<String, Object>> items = rows.stream().map(Authz::roleMap).toList();
        return Page.of(items, total, page, perPage);
    }

    private static List<String> normalizePermissions(Object input) {
        Set<String> result = new LinkedHashSet<>();
        if (input instanceof List<?> list) {
            for (Object item : list) {
                String code = Authz.str(item).trim();
                if (!code.isEmpty()) result.add(code);
                if (result.size() >= 500) break;
            }
        }
        return new ArrayList<>(result);
    }

    private static Map<String, Object> validate(Database db, Map<String, Object> payload, Map<String, Object> current) {
        String name = Security.sanitize(Authz.str(payload.get("name")), 64);
        if (name.isEmpty()) throw Web.badRequest("角色名称不能为空");
        String code = Authz.str(payload.get("code")).trim();
        if (!code.matches("^[A-Za-z][A-Za-z0-9_:-]{1,63}$")) {
            throw Web.badRequest("角色编码需为 2–64 位字母、数字、下划线、冒号或连字符，且以字母开头");
        }
        if (current != null && Authz.bool(current.get("system_role")) && !code.equals(Authz.str(current.get("code")))) {
            throw Web.badRequest("系统角色编码不可修改");
        }
        String deptId = Authz.str(payload.get("deptId"));
        Map<String, Object> dept = db.one("SELECT * FROM departments WHERE id = ? AND status = 'ACTIVE'", deptId);
        if (dept == null) throw Web.badRequest("所属部门不存在");
        String level = Authz.str(payload.getOrDefault("level",
                current == null ? "USER" : current.get("level"))).toUpperCase();
        if (!Authz.LEVELS.containsKey(level)) throw Web.badRequest("角色等级不合法");
        String color = Authz.normalizeColor(Authz.str(payload.get("color")), code);
        if (color == null) throw Web.badRequest("角色代表色不合法，应为 #rrggbb");
        String icon = Security.sanitize(Authz.str(payload.get("icon")), 64);
        List<String> permissions = normalizePermissions(payload.get("permissions"));
        if (current != null && "SUPER_ADMIN".equals(Authz.str(current.get("system_type")))) {
            permissions = List.of("*");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("code", code);
        data.put("deptId", Authz.str(dept.get("id")));
        data.put("level", level);
        data.put("color", color);
        data.put("icon", icon);
        data.put("permissions", permissions);
        return data;
    }

    private static void assertCodeUnique(Database db, String code, Object excludeId) {
        Map<String, Object> existing = excludeId == null
                ? db.one("SELECT id FROM roles WHERE code = ? COLLATE NOCASE", code)
                : db.one("SELECT id FROM roles WHERE code = ? COLLATE NOCASE AND id <> ?", code, String.valueOf(excludeId));
        if (existing != null) throw Web.conflict("角色编码已存在");
    }

    public static Map<String, Object> create(Database db, Config config, Map<String, Object> payload) {
        Map<String, Object> data = validate(db, payload, null);
        assertCodeUnique(db, Authz.str(data.get("code")), null);
        String now = Security.isoNow();
        String id = Security.nextId(config.snowflakeDataCenter, config.snowflakeMachine);
        db.update("""
                INSERT INTO roles
                (id, name, dept_id, code, level, system_role, system_type, icon, color, permissions,
                 status, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, 0, NULL, ?, ?, ?, 'ACTIVE', 0, ?, ?)
                """, id, data.get("name"), data.get("deptId"), data.get("code"), data.get("level"),
                emptyToNull(data.get("icon")), data.get("color"), Json.stringify(data.get("permissions")), now, now);
        return row(db, id);
    }

    public static Map<String, Object> update(Database db, Object id, Map<String, Object> payload) {
        Map<String, Object> current = row(db, id);
        if (current == null) throw Web.notFound("角色不存在");
        Map<String, Object> data = validate(db, payload, current);
        assertCodeUnique(db, Authz.str(data.get("code")), current.get("id"));
        String status = Authz.str(current.get("status"));
        if ("DEPRECATED".equals(payload.get("status"))) {
            if (Authz.bool(current.get("system_role"))) throw Web.badRequest("系统角色不可停用");
            if (usedCount(db, current.get("id")) > 0) throw Web.conflict("角色仍被用户使用，不能停用");
            status = "DEPRECATED";
        } else if ("ACTIVE".equals(payload.get("status"))) {
            status = "ACTIVE";
        }
        db.update("""
                UPDATE roles SET name = ?, dept_id = ?, code = ?, level = ?, icon = ?, color = ?,
                  permissions = ?, status = ?, update_time = ?, version = version + 1 WHERE id = ?
                """, data.get("name"), data.get("deptId"), data.get("code"), data.get("level"),
                emptyToNull(data.get("icon")), data.get("color"), Json.stringify(data.get("permissions")),
                status, Security.isoNow(), String.valueOf(current.get("id")));
        return row(db, current.get("id"));
    }

    public static Map<String, Object> disable(Database db, Object id) {
        Map<String, Object> current = row(db, id);
        if (current == null) throw Web.notFound("角色不存在");
        if (Authz.bool(current.get("system_role"))) throw Web.badRequest("系统角色不可停用");
        if (usedCount(db, current.get("id")) > 0) throw Web.conflict("角色仍被用户使用，不能停用");
        db.update("UPDATE roles SET status = 'DEPRECATED', update_time = ?, version = version + 1 WHERE id = ?",
                Security.isoNow(), String.valueOf(current.get("id")));
        return row(db, current.get("id"));
    }

    public static Map<String, Object> enable(Database db, Object id) {
        Map<String, Object> current = row(db, id);
        if (current == null) throw Web.notFound("角色不存在");
        db.update("UPDATE roles SET status = 'ACTIVE', update_time = ?, version = version + 1 WHERE id = ?",
                Security.isoNow(), String.valueOf(current.get("id")));
        return row(db, current.get("id"));
    }

    public static List<Map<String, Object>> permissions(Database db) {
        return db.query("SELECT * FROM permissions WHERE status = 'ACTIVE' ORDER BY module ASC, code ASC")
                .stream().map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", Authz.str(row.get("id")));
                    item.put("code", Authz.str(row.get("code")));
                    item.put("name", Authz.str(row.get("name")));
                    item.put("module", Authz.str(row.get("module")));
                    item.put("description", Authz.str(row.get("description")));
                    item.put("status", Authz.str(row.get("status")));
                    item.put("source", Authz.str(row.get("source")));
                    return item;
                }).toList();
    }

    public static List<Map<String, Object>> departments(Database db) {
        return db.query("SELECT * FROM departments WHERE status = 'ACTIVE' ORDER BY sort_order ASC, id ASC")
                .stream().map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", Authz.str(row.get("id")));
                    item.put("name", Authz.str(row.get("name")));
                    item.put("description", Authz.str(row.get("description")));
                    item.put("parentId", row.get("parent_id") == null ? null : Authz.str(row.get("parent_id")));
                    item.put("sortOrder", Authz.num(row.get("sort_order")));
                    item.put("deptType", Authz.str(row.get("dept_type")));
                    item.put("status", Authz.str(row.get("status")));
                    item.put("color", row.get("color") == null ? null : Authz.str(row.get("color")));
                    item.put("createTime", Authz.str(row.get("create_time")));
                    item.put("updateTime", Authz.str(row.get("update_time")));
                    return item;
                }).toList();
    }

    private static Object emptyToNull(Object value) {
        return value == null || String.valueOf(value).isEmpty() ? null : value;
    }
}

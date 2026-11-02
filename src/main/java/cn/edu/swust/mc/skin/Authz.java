package cn.edu.swust.mc.skin;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Authz {
    public static final String ROOT_DEPT = "1";
    public static final String SYSTEM_DEPT = "2";
    public static final String ADMIN_DEPT = "3";
    public static final String SUPER_ADMIN_ID = String.valueOf("super_admin".hashCode());
    public static final String ADMIN_ID = String.valueOf("admin".hashCode());
    public static final String USER_ID = String.valueOf("user".hashCode());
    public static final String GUEST_ID = String.valueOf("guest".hashCode());

    public static final Map<String, Integer> LEVELS = Map.of(
            "SUPER_ADMIN", 100,
            "ADMIN", 80,
            "USER", 50,
            "GUEST", 20
    );
    public static final Map<String, String> LEVEL_LABELS = Map.of(
            "SUPER_ADMIN", "超级管理员",
            "ADMIN", "管理员",
            "USER", "用户",
            "GUEST", "访客"
    );
    public static final Map<String, String> COLORS = Map.of(
            "SUPER_ADMIN", "#ef4444",
            "ADMIN", "#f59e0b",
            "USER", "#3b82f6",
            "GUEST", "#94a3b8",
            "FALLBACK", "#64748b"
    );
    public static final Set<String> ADMIN_TYPES = Set.of("SUPER_ADMIN", "ADMIN");

    public record Permission(String code, String name, String module, String description) {
    }

    public static final List<Permission> PERMISSIONS = List.of(
            new Permission("system:user:view", "查看用户", "系统管理", "查看用户列表与资料"),
            new Permission("system:user:create", "新增用户", "系统管理", "新增后台用户"),
            new Permission("system:user:edit", "编辑用户", "系统管理", "编辑用户资料与密码"),
            new Permission("system:user:delete", "停用用户", "系统管理", "停用或启用用户"),
            new Permission("system:user:purge", "删除用户", "系统管理", "永久删除用户及级联数据"),
            new Permission("system:user:assign-role", "分配用户角色", "系统管理", "分配或移除用户角色"),
            new Permission("system:user:assign-dept", "分配用户部门", "系统管理", "分配用户所属部门"),
            new Permission("system:role:view", "查看角色", "系统管理", "查看角色与权限目录"),
            new Permission("system:role:create", "新增角色", "系统管理", "新增普通角色"),
            new Permission("system:role:edit", "编辑角色", "系统管理", "编辑角色与分配权限"),
            new Permission("system:role:delete", "停用角色", "系统管理", "停用或启用角色"),
            new Permission("system:dept:view", "查看部门", "系统管理", "查看部门列表"),
            new Permission("system:setting:view", "查看系统设置", "系统管理", "查看站点设置"),
            new Permission("system:setting:edit", "编辑系统设置", "系统管理", "编辑站点设置"),
            new Permission("skin:player:view", "查看角色档案", "皮肤站管理", "查看全部游戏角色"),
            new Permission("skin:player:edit", "编辑角色档案", "皮肤站管理", "改名与转让角色"),
            new Permission("skin:player:delete", "删除角色档案", "皮肤站管理", "删除游戏角色"),
            new Permission("skin:texture:view", "查看材质", "皮肤站管理", "查看全部皮肤与披风"),
            new Permission("skin:texture:edit", "编辑材质", "皮肤站管理", "修改材质名称与公开状态"),
            new Permission("skin:texture:delete", "删除材质", "皮肤站管理", "删除皮肤与披风"),
            new Permission("skin:plugin:view", "查看插件", "皮肤站管理", "查看插件列表"),
            new Permission("skin:plugin:edit", "启停插件", "皮肤站管理", "启用、停用与重载插件"),
            new Permission("skin:plugin:install", "安装插件", "皮肤站管理", "安装插件 JAR"),
            new Permission("skin:plugin:delete", "卸载插件", "皮肤站管理", "卸载插件与配置数据")
    );

    private Authz() {
    }

    public static void seed(Database db, Config config) {
        String now = Security.isoNow();
        insertDepartment(db, ROOT_DEPT, "根部门", "普通用户与访客默认归属部门", SYSTEM_DEPT, 2, "ROOT", "#3b82f6", now);
        insertDepartment(db, SYSTEM_DEPT, "系统", "系统虚拟部门", null, 0, "SYSTEM", "#64748b", now);
        insertDepartment(db, ADMIN_DEPT, "系统管理", "超级管理员与管理员挂靠部门", SYSTEM_DEPT, 1, "SYSTEM_ADMIN", "#f59e0b", now);

        for (Permission permission : PERMISSIONS) {
            db.update("""
                    INSERT OR IGNORE INTO permissions
                    (id, code, name, module, description, status, source, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'ANNOTATION', ?, ?)
                    """,
                    Security.nextId(config.snowflakeDataCenter, config.snowflakeMachine),
                    permission.code(), permission.name(), permission.module(), permission.description(), now, now);
        }

        List<String> adminPermissions = PERMISSIONS.stream()
                .map(Permission::code)
                .filter(code -> !code.equals("system:user:purge"))
                .toList();
        insertRole(db, SUPER_ADMIN_ID, "超级管理员", ADMIN_DEPT, "super_admin", "SUPER_ADMIN", "SUPER_ADMIN",
                "morph:crown", COLORS.get("SUPER_ADMIN"), List.of("*"), now);
        insertRole(db, ADMIN_ID, "管理员", ADMIN_DEPT, "admin", "ADMIN", "ADMIN",
                "morph:shield", COLORS.get("ADMIN"), adminPermissions, now);
        insertRole(db, USER_ID, "普通用户", ROOT_DEPT, "user", "USER", "USER",
                "morph:user", COLORS.get("USER"), List.of(), now);
        insertRole(db, GUEST_ID, "访客", ROOT_DEPT, "guest", "GUEST", "GUEST",
                "morph:ghost", COLORS.get("GUEST"), List.of(), now);
    }

    private static void insertDepartment(Database db, String id, String name, String description, String parentId,
                                         int sortOrder, String type, String color, String now) {
        db.update("""
                INSERT OR IGNORE INTO departments
                (id, name, description, parent_id, sort_order, dept_type, status, color, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """, id, name, description, parentId, sortOrder, type, color, now, now);
    }

    private static void insertRole(Database db, String id, String name, String deptId, String code, String level,
                                   String systemType, String icon, String color, List<String> permissions, String now) {
        db.update("""
                INSERT OR IGNORE INTO roles
                (id, name, dept_id, code, level, system_role, system_type, icon, color, permissions,
                 status, version, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, 'ACTIVE', 0, ?, ?)
                """, id, name, deptId, code, level, systemType, icon, color,
                Json.stringify(permissions), now, now);
    }

    public static AuthUser authUser(Database db, Map<String, Object> row) {
        if (row == null) return null;
        AuthUser user = new AuthUser();
        user.id = str(row.get("id"));
        user.username = str(row.get("username"));
        user.nickname = str(row.get("nickname"));
        user.email = str(row.get("email"));
        user.phone = str(row.get("phone"));
        user.qq = str(row.get("qq"));
        user.avatarFileId = row.get("avatar_file_id") == null ? null : str(row.get("avatar_file_id"));
        user.emailVerified = num(row.get("email_verified")) != 0;
        user.status = str(row.get("status"));
        user.createTime = str(row.get("create_time"));
        user.updateTime = str(row.get("update_time"));
        user.lastLoginTime = row.get("last_login_time") == null ? null : str(row.get("last_login_time"));
        user.tags = parseJson(row.get("tags"), List.of());
        user.fields = parseJson(row.get("fields"), Map.of());

        List<Map<String, Object>> roles = db.query("""
                SELECT r.*, d.name AS dept_name
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                LEFT JOIN departments d ON d.id = r.dept_id
                WHERE ur.user_id = ? AND r.status = 'ACTIVE'
                """, user.id).stream().map(Authz::roleMap).toList();
        roles = new ArrayList<>(roles);
        roles.sort(Comparator
                .comparing((Map<String, Object> role) -> bool(role.get("systemRole")) ? 0 : 1)
                .thenComparing(role -> -((Integer) role.get("levelWeight")))
                .thenComparing(role -> str(role.get("name"))));
        user.roles = roles;

        List<Map<String, Object>> depts = db.query("""
                SELECT d.*, ud.is_default
                FROM user_depts ud JOIN departments d ON d.id = ud.dept_id
                WHERE ud.user_id = ? AND d.status = 'ACTIVE'
                ORDER BY ud.is_default DESC, d.sort_order ASC
                """, user.id).stream().map(dept -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", str(dept.get("id")));
            item.put("name", str(dept.get("name")));
            item.put("parentId", dept.get("parent_id") == null ? null : str(dept.get("parent_id")));
            item.put("deptType", str(dept.get("dept_type")));
            item.put("status", str(dept.get("status")));
            item.put("color", dept.get("color") == null ? null : str(dept.get("color")));
            item.put("isDefault", num(dept.get("is_default")) != 0);
            return item;
        }).toList();
        user.depts = new ArrayList<>(depts);

        Set<String> permissions = new LinkedHashSet<>();
        boolean wildcard = false;
        boolean admin = false;
        int level = 0;
        for (Map<String, Object> role : roles) {
            user.roleIds.add(str(role.get("id")));
            user.roleNames.add(str(role.get("name")));
            String systemType = str(role.get("systemType"));
            if ("SUPER_ADMIN".equals(systemType) || ((List<?>) role.get("permissions")).contains("*")) {
                wildcard = true;
            }
            if (ADMIN_TYPES.contains(systemType)) admin = true;
            level = Math.max(level, (Integer) role.get("levelWeight"));
            for (Object permission : (List<?>) role.get("permissions")) user.rolePermissions.add(str(permission));
        }
        permissions.addAll(user.rolePermissions);
        for (Map<String, Object> rowPermission : db.query(
                "SELECT permission FROM user_permissions WHERE user_id = ? ORDER BY permission", user.id)) {
            String permission = str(rowPermission.get("permission"));
            if (!permission.isEmpty()) {
                user.extraPermissions.add(permission);
                permissions.add(permission);
            }
        }
        for (Map<String, Object> dept : depts) {
            user.deptIds.add(str(dept.get("id")));
            user.deptNames.add(str(dept.get("name")));
            if (Boolean.TRUE.equals(dept.get("isDefault"))) user.defaultDeptId = str(dept.get("id"));
        }
        if (user.defaultDeptId == null && !user.deptIds.isEmpty()) user.defaultDeptId = user.deptIds.get(0);
        user.permissions = permissions;
        user.wildcard = wildcard;
        user.admin = admin;
        user.levelWeight = level;
        return user;
    }

    public static Map<String, Object> roleMap(Map<String, Object> row) {
        List<String> permissions = stringList(parseJson(row.get("permissions"), List.of()));
        String level = str(row.get("level"));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", str(row.get("id")));
        map.put("name", str(row.get("name")));
        map.put("deptId", row.get("dept_id") == null ? null : str(row.get("dept_id")));
        map.put("deptName", row.get("dept_name") == null ? null : str(row.get("dept_name")));
        map.put("code", str(row.get("code")));
        map.put("level", level);
        map.put("levelWeight", LEVELS.getOrDefault(level, 0));
        map.put("systemRole", num(row.get("system_role")) != 0);
        map.put("systemType", row.get("system_type") == null ? null : str(row.get("system_type")));
        map.put("icon", row.get("icon") == null ? null : str(row.get("icon")));
        map.put("color", row.get("color") == null ? null : str(row.get("color")));
        map.put("permissions", permissions);
        map.put("permissionCount", permissions.size());
        map.put("status", str(row.get("status")));
        map.put("createTime", str(row.get("create_time")));
        map.put("updateTime", str(row.get("update_time")));
        return map;
    }

    public static List<String> stringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object item : items) list.add(str(item));
        }
        return list;
    }

    public static Object parseJson(Object value, Object fallback) {
        if (value == null) return fallback;
        try {
            Object parsed = Json.MAPPER.readValue(str(value), new TypeReference<Object>() {
            });
            return parsed == null ? fallback : parsed;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static String pluginPermissionCode(String pluginId) {
        return "plugin:" + pluginId + ":use";
    }

    public static void ensurePluginPermission(Database db, Config config, String pluginId, String pluginName) {
        String code = pluginPermissionCode(pluginId);
        if (db.one("SELECT id FROM permissions WHERE code = ?", code) != null) return;
        String now = Security.isoNow();
        db.update("""
                INSERT OR IGNORE INTO permissions
                (id, code, name, module, description, status, source, create_time, update_time)
                VALUES (?, ?, ?, '插件权限', ?, 'ACTIVE', 'PLUGIN', ?, ?)
                """, Security.nextId(config.snowflakeDataCenter, config.snowflakeMachine),
                code, "使用 " + pluginName, "访问插件 " + pluginName + " 的页面与接口", now, now);
        // 默认授予系统角色，保持插件安装后立即可用；管理员可在角色管理中收回。
        appendRolePermission(db, ADMIN_ID, code, now);
        appendRolePermission(db, USER_ID, code, now);
        appendRolePermission(db, GUEST_ID, code, now);
    }

    public static void removePluginPermission(Database db, String pluginId) {
        String code = pluginPermissionCode(pluginId);
        db.update("DELETE FROM permissions WHERE code = ?", code);
        db.update("DELETE FROM user_permissions WHERE permission = ?", code);
        String now = Security.isoNow();
        for (Map<String, Object> role : db.query("SELECT id, permissions FROM roles")) {
            List<String> permissions = stringList(parseJson(role.get("permissions"), List.of()));
            if (!permissions.remove(code)) continue;
            db.update("UPDATE roles SET permissions = ?, update_time = ?, version = version + 1 WHERE id = ?",
                    Json.stringify(permissions), now, str(role.get("id")));
        }
    }

    public static void setUserExtraPermissions(Database db, String userId, List<String> permissions) {
        Set<String> cleaned = new LinkedHashSet<>();
        for (String permission : permissions == null ? List.<String>of() : permissions) {
            String code = str(permission).trim();
            if (code.isEmpty()) continue;
            if ("*".equals(code)) throw new Web.HttpError(400, "bad_request", "不能直接授予通配权限");
            if (code.length() > 128) throw new Web.HttpError(400, "bad_request", "权限码过长");
            if (db.one("SELECT 1 FROM permissions WHERE code = ? AND status = 'ACTIVE'", code) == null) {
                throw new Web.HttpError(400, "bad_request", "权限不存在或已停用：" + code);
            }
            cleaned.add(code);
            if (cleaned.size() >= 200) break;
        }
        db.update("DELETE FROM user_permissions WHERE user_id = ?", userId);
        String now = Security.isoNow();
        for (String code : cleaned) {
            db.update("INSERT INTO user_permissions (user_id, permission, created_at) VALUES (?, ?, ?)",
                    userId, code, now);
        }
    }

    public static List<Map<String, Object>> allPermissions(Database db) {
        return db.query("SELECT * FROM permissions WHERE status = 'ACTIVE' ORDER BY module ASC, code ASC");
    }

    public static boolean guestCan(Database db, String code) {
        Map<String, Object> row = db.one("SELECT permissions FROM roles WHERE id = ? AND status = 'ACTIVE'", GUEST_ID);
        if (row == null) return false;
        List<String> permissions = stringList(parseJson(row.get("permissions"), List.of()));
        return permissions.contains("*") || permissions.contains(code);
    }

    private static void appendRolePermission(Database db, String roleId, String code, String now) {
        Map<String, Object> role = db.one("SELECT permissions FROM roles WHERE id = ?", roleId);
        if (role == null) return;
        List<String> permissions = stringList(parseJson(role.get("permissions"), List.of()));
        if (permissions.contains("*") || permissions.contains(code)) return;
        permissions.add(code);
        db.update("UPDATE roles SET permissions = ?, update_time = ?, version = version + 1 WHERE id = ?",
                Json.stringify(permissions), now, roleId);
    }

    public static int activeAdminCount(Database db, String excludeId) {
        String sql = """
                SELECT COUNT(DISTINCT u.id) AS count
                FROM users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id
                WHERE u.status = 'ACTIVE' AND r.status = 'ACTIVE'
                  AND r.system_type IN ('SUPER_ADMIN', 'ADMIN')
                """ + (excludeId == null ? "" : " AND u.id <> ?");
        Map<String, Object> row = excludeId == null ? db.one(sql) : db.one(sql, excludeId);
        return row == null ? 0 : num(row.get("count"));
    }

    public static void ensureUserDept(Database db, String userId, String deptId, boolean isDefault) {
        db.update("INSERT OR IGNORE INTO user_depts (user_id, dept_id, is_default) VALUES (?, ?, ?)",
                userId, deptId, isDefault ? 1 : 0);
        if (isDefault) {
            db.update("UPDATE user_depts SET is_default = 0 WHERE user_id = ?", userId);
            db.update("UPDATE user_depts SET is_default = 1 WHERE user_id = ? AND dept_id = ?", userId, deptId);
        }
    }

    public static void assignRoles(Database db, String userId, List<String> roleIds, boolean autoAddDepts) {
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(
                roleIds == null ? List.of() : roleIds.stream().map(String::valueOf).toList()));
        List<Map<String, Object>> roles = new ArrayList<>();
        if (!ids.isEmpty()) {
            String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
            roles = db.query("SELECT * FROM roles WHERE id IN (" + placeholders + ")", ids.toArray());
        }
        if (roles.size() != ids.size()) throw new Web.HttpError(400, "bad_request", "角色不存在");
        for (Map<String, Object> role : roles) {
            if (!"ACTIVE".equals(str(role.get("status")))) {
                throw new Web.HttpError(400, "bad_request", "角色已停用");
            }
        }
        db.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        for (Map<String, Object> role : roles) {
            db.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, str(role.get("id")));
            if (autoAddDepts && role.get("dept_id") != null) {
                ensureUserDept(db, userId, str(role.get("dept_id")), false);
            }
        }
    }

    public static void setUserDepts(Database db, String userId, List<Map<String, Object>> depts) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> item : depts == null ? List.<Map<String, Object>>of() : depts) {
            String deptId = str(item.getOrDefault("deptId", item.get("id")));
            if (deptId.isEmpty()) continue;
            Map<String, Object> dept = db.one("SELECT * FROM departments WHERE id = ? AND status = 'ACTIVE'", deptId);
            if (dept == null) throw new Web.HttpError(400, "bad_request", "部门不存在或已停用");
            boolean isDefault = bool(item.get("defaultDept")) || bool(item.get("isDefault"));
            normalized.add(Map.of("deptId", deptId, "isDefault", isDefault));
        }
        if (normalized.isEmpty()) normalized.add(Map.of("deptId", ROOT_DEPT, "isDefault", true));
        long defaults = normalized.stream().filter(item -> Boolean.TRUE.equals(item.get("isDefault"))).count();
        if (defaults != 1) throw new Web.HttpError(400, "bad_request", "必须设置一个默认部门");
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> item : normalized) ids.add(str(item.get("deptId")));
        if (ids.size() != normalized.size()) throw new Web.HttpError(400, "bad_request", "用户部门不能重复");

        db.update("DELETE FROM user_depts WHERE user_id = ?", userId);
        for (Map<String, Object> item : normalized) {
            db.update("INSERT INTO user_depts (user_id, dept_id, is_default) VALUES (?, ?, ?)",
                    userId, str(item.get("deptId")), Boolean.TRUE.equals(item.get("isDefault")) ? 1 : 0);
        }
        List<Map<String, Object>> userRoles = db.query("SELECT role_id FROM user_roles WHERE user_id = ?", userId);
        for (Map<String, Object> userRole : userRoles) {
            Map<String, Object> role = db.one("SELECT dept_id FROM roles WHERE id = ?", str(userRole.get("role_id")));
            if (role == null || role.get("dept_id") == null || !ids.contains(str(role.get("dept_id")))) {
                db.update("DELETE FROM user_roles WHERE user_id = ? AND role_id = ?", userId, str(userRole.get("role_id")));
            }
        }
    }

    public static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static int num(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(str(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean bool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        String text = str(value);
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    public static String levelLabel(String level) {
        return LEVEL_LABELS.getOrDefault(level, level);
    }

    public static String normalizeColor(String color, String code) {
        String text = color == null ? "" : color.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return COLORS.getOrDefault(code == null ? "" : code.toUpperCase(Locale.ROOT), COLORS.get("FALLBACK"));
        }
        return text.matches("^#[0-9a-f]{6}$") ? text : null;
    }
}

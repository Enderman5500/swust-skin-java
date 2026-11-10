package cn.edu.swust.mc.skin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Players {
    public static final int DEFAULT_PER_USER = 3;
    public static final int MAX_PER_USER = 100;
    public static final Object UNSET = new Object();
    public static final Object CLEAR = new Object();

    private static final String SELECT = """
            SELECT p.*,
                   u.username AS owner_name, u.nickname AS owner_nickname,
                   s.hash AS skin_hash, s.name AS skin_name, s.model AS skin_texture_model,
                   c.hash AS cape_hash, c.name AS cape_name
            FROM players p
            LEFT JOIN users u ON u.id = p.user_id
            LEFT JOIN textures s ON s.id = p.skin_texture_id
            LEFT JOIN textures c ON c.id = p.cape_texture_id
            """;

    private Players() {
    }

    public static boolean slimModel(String model) {
        return "slim".equals(model);
    }

    public static String defaultSkinFile(String model) {
        return slimModel(model) ? "alex.png" : "steve.png";
    }

    public static String defaultSkinName(String model) {
        return slimModel(model) ? "Alex" : "Steve";
    }

    public static Map<String, Object> defaultSkinRef(String model) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("id", null);
        ref.put("hash", null);
        ref.put("name", defaultSkinName(model));
        ref.put("model", slimModel(model) ? "slim" : "classic");
        ref.put("url", "/assets/default/" + defaultSkinFile(model));
        ref.put("isDefault", true);
        return ref;
    }

    private static Map<String, Object> textureRef(Object id, Object hash, Object name, Object model) {
        if (id == null || hash == null) return null;
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("id", id);
        ref.put("hash", Authz.str(hash));
        ref.put("name", Authz.str(name));
        ref.put("model", model == null ? null : Authz.str(model));
        ref.put("url", Textures.url(Authz.str(hash)));
        return ref;
    }

    public static Map<String, Object> serialize(Map<String, Object> row, AuthUser viewer) {
        if (row == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", row.get("id"));
        map.put("name", Authz.str(row.get("name")));
        map.put("uuid", Authz.str(row.get("uuid")));
        map.put("model", Authz.str(row.get("model")));
        map.put("createdAt", Authz.str(row.get("created_at")));
        Object ownerId = row.get("user_id");
        if (ownerId == null) {
            map.put("owner", null);
        } else {
            Map<String, Object> owner = new LinkedHashMap<>();
            owner.put("id", Authz.str(ownerId));
            String name = Authz.str(row.get("owner_name"));
            owner.put("username", name.isEmpty() ? "已注销用户" : name);
            String nickname = Authz.str(row.get("owner_nickname"));
            owner.put("nickname", nickname.isEmpty() ? name : nickname);
            map.put("owner", owner);
        }
        Map<String, Object> skin = textureRef(row.get("skin_texture_id"), row.get("skin_hash"),
                row.get("skin_name"), row.get("skin_texture_model"));
        String model = Authz.str(row.get("model"));
        map.put("skin", skin == null ? defaultSkinRef(model) : skin);
        map.put("cape", textureRef(row.get("cape_texture_id"), row.get("cape_hash"),
                row.get("cape_name"), null));
        map.put("isMine", viewer != null && viewer.id.equals(Authz.str(ownerId)));
        return map;
    }

    public static Map<String, Object> row(Database db, Object id) {
        return db.one(SELECT + " WHERE p.id = ?", id);
    }

    public static Map<String, Object> byUuid(Database db, String uuid) {
        String normalized = Security.stripUuid(uuid);
        if (!normalized.matches("^[0-9a-f]{32}$")) return null;
        return db.one(SELECT + " WHERE p.uuid = ?", normalized);
    }

    public static Map<String, Object> byName(Database db, String name) {
        return db.one(SELECT + " WHERE p.name = ? COLLATE NOCASE", name == null ? "" : name);
    }

    public static List<Map<String, Object>> forUser(Database db, String userId) {
        return db.query(SELECT + " WHERE p.user_id = ? ORDER BY p.created_at ASC", userId);
    }

    public static List<Map<String, Object>> forUserRaw(Database db, String userId) {
        return db.query("SELECT id, name, uuid, model FROM players WHERE user_id = ? ORDER BY created_at ASC", userId);
    }

    public static Map<String, Object> adminPage(Database db, Map<String, String> query) {
        int page = Math.max(1, Users.parseInt(query.get("page"), 1));
        int perPage = Math.max(1, Math.min(100, Users.parseInt(
                query.containsKey("perPage") ? query.get("perPage") : query.get("size"), 30)));
        int offset = (page - 1) * perPage;
        String q = query.getOrDefault("q", "").trim();
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (!q.isEmpty()) {
            conditions.add("(p.name LIKE ? ESCAPE '\\' OR u.username LIKE ? ESCAPE '\\')");
            params.add(Security.likePattern(q));
            params.add(Security.likePattern(q));
        }
        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        Map<String, Object> count = db.one("""
                SELECT COUNT(*) AS count FROM players p
                LEFT JOIN users u ON u.id = p.user_id %s
                """.formatted(where), params.toArray());
        int total = count == null ? 0 : Authz.num(count.get("count"));
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(perPage);
        pageParams.add(offset);
        List<Map<String, Object>> rows = db.query(SELECT + " " + where
                + " ORDER BY p.created_at DESC LIMIT ? OFFSET ?", pageParams.toArray());
        List<Map<String, Object>> items = rows.stream().map(row -> serialize(row, null)).toList();
        return Page.of(items, total, page, perPage);
    }

    public static void assertNameAvailable(Database db, String name, Object exceptId) {
        Map<String, Object> existing = exceptId == null
                ? db.one("SELECT id FROM players WHERE name = ? COLLATE NOCASE", name)
                : db.one("SELECT id FROM players WHERE name = ? COLLATE NOCASE AND id <> ?", name, exceptId);
        if (existing != null) throw Web.conflict("该角色名已被占用");
    }

    public static int limitFor(Database db, String userId) {
        Map<String, Object> row = db.one("SELECT player_limit FROM users WHERE id = ?", userId);
        if (row == null || row.get("player_limit") == null) return DEFAULT_PER_USER;
        return Math.max(0, Authz.num(row.get("player_limit")));
    }

    public static Map<String, Object> create(Database db, AuthUser user, String name, String model) {
        String cleanName = name == null ? "" : name.trim();
        if (!Security.validPlayerName(cleanName)) throw Web.badRequest("角色名需为 3–16 位字母、数字或下划线");
        if (model == null) model = "classic";
        if (!"classic".equals(model) && !"slim".equals(model)) throw Web.badRequest("角色模型不合法");
        int limit = limitFor(db, user.id);
        if (limit <= 0) throw Web.badRequest("当前账号未开放创建角色");
        Map<String, Object> count = db.one("SELECT COUNT(*) AS count FROM players WHERE user_id = ?", user.id);
        if (count != null && Authz.num(count.get("count")) >= limit) {
            throw Web.badRequest("每位社员最多创建 " + limit + " 个角色");
        }
        assertNameAvailable(db, cleanName, null);
        String uuid = Security.playerUuid(cleanName);
        try {
            long id = db.insert("""
                    INSERT INTO players (user_id, name, uuid, model, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, user.id, cleanName, uuid, model, Security.isoNow());
            return row(db, id);
        } catch (RuntimeException e) {
            if (String.valueOf(e.getMessage()).contains("UNIQUE")) {
                throw Web.conflict("该角色名或 UUID 已存在");
            }
            throw e;
        }
    }

    public static Map<String, Object> rename(Database db, Map<String, Object> player, String name) {
        String cleanName = name == null ? "" : name.trim();
        if (!Security.validPlayerName(cleanName)) throw Web.badRequest("角色名需为 3–16 位字母、数字或下划线");
        if (cleanName.equalsIgnoreCase(Authz.str(player.get("name")))) return row(db, player.get("id"));
        assertNameAvailable(db, cleanName, player.get("id"));
        try {
            db.update("UPDATE players SET name = ? WHERE id = ?", cleanName, player.get("id"));
        } catch (RuntimeException e) {
            if (String.valueOf(e.getMessage()).contains("UNIQUE")) throw Web.conflict("该角色名已被占用");
            throw e;
        }
        return row(db, player.get("id"));
    }

    public static Map<String, Object> setModel(Database db, Map<String, Object> player, String model) {
        if (!"classic".equals(model) && !"slim".equals(model)) throw Web.badRequest("角色模型不合法");
        db.update("UPDATE players SET model = ? WHERE id = ?", model, player.get("id"));
        return row(db, player.get("id"));
    }

    public static void delete(Database db, Map<String, Object> player) {
        db.update("DELETE FROM players WHERE id = ?", player.get("id"));
    }

    public static Map<String, Object> setTextures(Database db, Map<String, Object> player,
                                                  Object skinTextureId, Object capeTextureId,
                                                  AuthUser actor) {
        Object skinId = player.get("skin_texture_id");
        Object capeId = player.get("cape_texture_id");
        if (skinTextureId != UNSET) {
            if (skinTextureId == CLEAR) {
                skinId = null;
            } else {
                int id = parseId(skinTextureId);
                Map<String, Object> texture = Textures.row(db, id);
                assertUsable(texture, "skin", actor);
                skinId = texture.get("id");
            }
        }
        if (capeTextureId != UNSET) {
            if (capeTextureId == CLEAR) {
                capeId = null;
            } else {
                int id = parseId(capeTextureId);
                Map<String, Object> texture = Textures.row(db, id);
                assertUsable(texture, "cape", actor);
                capeId = texture.get("id");
            }
        }
        db.update("UPDATE players SET skin_texture_id = ?, cape_texture_id = ? WHERE id = ?",
                skinId, capeId, player.get("id"));
        return row(db, player.get("id"));
    }

    private static void assertUsable(Map<String, Object> texture, String expectedType, AuthUser actor) {
        if (texture == null) throw Web.notFound("选择的材质不存在");
        if (!expectedType.equals(Authz.str(texture.get("type")))) {
            throw Web.badRequest("skin".equals(expectedType) ? "请选择皮肤材质" : "请选择披风材质");
        }
        if (!Authz.bool(texture.get("is_public"))
                && !actor.id.equals(Authz.str(texture.get("uploaded_by")))
                && !actor.admin) {
            throw Web.forbidden("不能使用他人的私有材质");
        }
    }

    public static Map<String, Object> reassign(Database db, Map<String, Object> player, String userId) {
        if (db.one("SELECT id FROM users WHERE id = ?", userId) == null) throw Web.notFound("目标用户不存在");
        db.update("UPDATE players SET user_id = ? WHERE id = ?", userId, player.get("id"));
        return row(db, player.get("id"));
    }

    private static int parseId(Object value) {
        try {
            int id = Integer.parseInt(String.valueOf(value));
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw Web.notFound("选择的材质不存在");
        }
    }
}

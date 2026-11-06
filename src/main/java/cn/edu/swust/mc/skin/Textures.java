package cn.edu.swust.mc.skin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

public final class Textures {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private Textures() {
    }

    public record PngInfo(int width, int height) {
    }

    public record CreateResult(Map<String, Object> row, boolean created, boolean attachedToCloset) {
    }

    public static Path filePath(Config config, String hash) {
        return config.dataDir.resolve("textures").resolve(hash + ".png");
    }

    public static Map<String, Object> row(Database db, Object id) {
        return db.one("""
                SELECT t.*, u.username AS uploader_name, u.nickname AS uploader_nickname
                FROM textures t LEFT JOIN users u ON u.id = t.uploaded_by
                WHERE t.id = ?
                """, id);
    }

    public static Map<String, Object> byHash(Database db, String hash) {
        return db.one("""
                SELECT t.*, u.username AS uploader_name, u.nickname AS uploader_nickname
                FROM textures t LEFT JOIN users u ON u.id = t.uploaded_by
                WHERE t.hash = ?
                """, hash);
    }

    public static String url(String hash) {
        return "/textures/" + hash + ".png";
    }

    public static Map<String, Object> serialize(Map<String, Object> row, AuthUser viewer, boolean inCloset) {
        if (row == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", ((Number) row.get("id")).intValue());
        map.put("hash", Authz.str(row.get("hash")));
        map.put("type", Authz.str(row.get("type")));
        map.put("model", Authz.str(row.get("model")));
        map.put("name", Authz.str(row.get("name")));
        map.put("width", Authz.num(row.get("width")));
        map.put("height", Authz.num(row.get("height")));
        map.put("isPublic", Authz.bool(row.get("is_public")));
        map.put("downloads", Authz.num(row.get("downloads")));
        map.put("createdAt", Authz.str(row.get("created_at")));
        Object uploaderId = row.get("uploaded_by");
        if (uploaderId == null) {
            map.put("uploader", null);
        } else {
            Map<String, Object> uploader = new LinkedHashMap<>();
            uploader.put("id", Authz.str(uploaderId));
            String name = Authz.str(row.get("uploader_name"));
            uploader.put("username", name.isEmpty() ? "已注销用户" : name);
            String nickname = Authz.str(row.get("uploader_nickname"));
            uploader.put("nickname", nickname.isEmpty() ? name : nickname);
            map.put("uploader", uploader);
        }
        map.put("isMine", viewer != null && viewer.id.equals(Authz.str(uploaderId)));
        map.put("inCloset", inCloset);
        map.put("url", url(Authz.str(row.get("hash"))));
        map.put("contentUrl", "/api/textures/" + row.get("id") + "/content");
        map.put("downloadUrl", "/api/textures/" + row.get("id") + "/download");
        return map;
    }

    public static boolean canAccess(Map<String, Object> row, AuthUser user) {
        if (row == null) return false;
        if (Authz.bool(row.get("is_public"))) return true;
        if (user == null) return false;
        return user.admin || user.id.equals(Authz.str(row.get("uploaded_by")));
    }

    public static boolean canManage(Map<String, Object> row, AuthUser user) {
        if (row == null || user == null) return false;
        return user.admin || user.id.equals(Authz.str(row.get("uploaded_by")));
    }

    public static Set<Integer> closetIds(Database db, String userId, List<Integer> ids) {
        Set<Integer> result = new LinkedHashSet<>();
        if (userId == null || ids.isEmpty()) return result;
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        List<Object> params = new ArrayList<>();
        params.add(userId);
        params.addAll(ids);
        for (Map<String, Object> row : db.query(
                "SELECT texture_id FROM closet_items WHERE user_id = ? AND texture_id IN (" + placeholders + ")",
                params.toArray())) {
            result.add(Authz.num(row.get("texture_id")));
        }
        return result;
    }

    public static Map<String, Object> list(Database db, AuthUser viewer, Map<String, String> query) {
        int page = Math.max(1, Users.parseInt(query.get("page"), 1));
        int perPage = Math.max(1, Math.min(60, Users.parseInt(
                first(query, "perPage", "size"), 24)));
        int offset = (page - 1) * perPage;
        String scope = query.getOrDefault("scope", "library");
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if ("mine".equals(scope)) {
            if (viewer == null) throw Web.forbidden("请先登录");
            conditions.add("t.uploaded_by = ?");
            params.add(viewer.id);
        } else if ("admin".equals(scope)) {
            if (viewer == null || !viewer.can("skin:texture:view")) {
                throw Web.forbidden("没有权限执行此操作");
            }
        } else {
            conditions.add("t.is_public = 1");
        }
        String type = query.get("type");
        if ("skin".equals(type) || "cape".equals(type)) {
            conditions.add("t.type = ?");
            params.add(type);
        }
        String q = query.getOrDefault("q", "").trim();
        if (!q.isEmpty()) {
            conditions.add("(t.name LIKE ? ESCAPE '\\' OR u.username LIKE ? ESCAPE '\\')");
            params.add(Security.likePattern(q));
            params.add(Security.likePattern(q));
        }
        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        Map<String, Object> count = db.one("""
                SELECT COUNT(*) AS count FROM textures t
                LEFT JOIN users u ON u.id = t.uploaded_by %s
                """.formatted(where), params.toArray());
        int total = count == null ? 0 : Authz.num(count.get("count"));
        String order = "t.created_at DESC, t.id DESC";
        if ("popular".equals(query.get("sort"))) order = "t.downloads DESC, t.id DESC";
        else if ("random".equals(query.get("sort"))) order = "RANDOM()";
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(perPage);
        pageParams.add(offset);
        List<Map<String, Object>> rows = db.query("""
                SELECT t.*, u.username AS uploader_name, u.nickname AS uploader_nickname
                FROM textures t LEFT JOIN users u ON u.id = t.uploaded_by
                %s ORDER BY %s LIMIT ? OFFSET ?
                """.formatted(where, order), pageParams.toArray());
        List<Integer> ids = rows.stream().map(row -> Authz.num(row.get("id"))).toList();
        Set<Integer> closet = closetIds(db, viewer == null ? null : viewer.id, ids);
        List<Map<String, Object>> items = rows.stream()
                .map(row -> serialize(row, viewer, closet.contains(Authz.num(row.get("id")))))
                .toList();
        return Page.of(items, total, page, perPage);
    }

    public static Map<String, Object> validateInput(String type, String name, String model, boolean isPublic) {
        if (!"skin".equals(type) && !"cape".equals(type)) throw Web.badRequest("材质类型必须是皮肤或披风");
        String cleanName = Security.sanitize(name, 48);
        if (cleanName.isEmpty()) throw Web.badRequest("请填写材质名称");
        if ("skin".equals(type) && !"classic".equals(model) && !"slim".equals(model)) {
            throw Web.badRequest("皮肤模型必须是经典或纤细");
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("type", type);
        input.put("name", cleanName);
        input.put("model", "skin".equals(type) ? model : "classic");
        input.put("isPublic", isPublic ? 1 : 0);
        return input;
    }

    public static PngInfo inspect(byte[] buffer, String type) {
        PngInfo info = readPngInfo(buffer);
        if (info.width() > 256 || info.height() > 256) throw Web.badRequest("材质尺寸不能超过 256×256");
        boolean scaled = (info.width() == 64 || info.width() == 128 || info.width() == 256)
                && (info.height() == info.width() || info.height() * 2 == info.width());
        if ("skin".equals(type)) {
            if (!scaled) throw Web.badRequest("皮肤尺寸应为 64×64、64×32（或同比例高清尺寸）");
        } else if ("cape".equals(type)) {
            if (!(info.width() == 22 && info.height() == 17) && !scaled) {
                throw Web.badRequest("披风尺寸应为 64×32（或同比例高清尺寸）");
            }
        } else {
            throw Web.badRequest("未知的材质类型");
        }
        return info;
    }

    public static PngInfo readPngInfo(byte[] buffer) {
        if (buffer == null || buffer.length < 33) throw Web.badRequest("文件不是有效的 PNG 图片");
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (buffer[i] != PNG_SIGNATURE[i]) throw Web.badRequest("文件不是有效的 PNG 图片");
        }
        int offset = 8;
        int width = 0;
        int height = 0;
        int bitDepth = 0;
        int colorType = 0;
        int compression = 0;
        int filter = 0;
        int interlace = 0;
        boolean header = false;
        boolean sawData = false;
        boolean sawEnd = false;
        while (offset + 12 <= buffer.length) {
            int length = readInt(buffer, offset);
            if (length < 0 || offset + 12L + length > buffer.length) throw Web.badRequest("PNG 数据不完整");
            String chunkType = new String(buffer, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int dataStart = offset + 8;
            int dataEnd = dataStart + length;
            long expectedCrc = Integer.toUnsignedLong(readInt(buffer, dataEnd));
            CRC32 crc = new CRC32();
            crc.update(buffer, offset + 4, 4 + length);
            if (crc.getValue() != expectedCrc) throw Web.badRequest("PNG 校验失败，文件可能已损坏");
            if (!header) {
                if (!"IHDR".equals(chunkType) || length != 13) throw Web.badRequest("PNG 缺少有效的 IHDR");
                width = readInt(buffer, dataStart);
                height = readInt(buffer, dataStart + 4);
                bitDepth = buffer[dataStart + 8] & 0xff;
                colorType = buffer[dataStart + 9] & 0xff;
                compression = buffer[dataStart + 10] & 0xff;
                filter = buffer[dataStart + 11] & 0xff;
                interlace = buffer[dataStart + 12] & 0xff;
                header = true;
            } else if ("IDAT".equals(chunkType)) {
                sawData = true;
            } else if ("IEND".equals(chunkType)) {
                sawEnd = true;
                break;
            }
            offset = dataEnd + 4;
        }
        if (!header || !sawData || !sawEnd) throw Web.badRequest("PNG 数据不完整");
        if (bitDepth != 8) throw Web.badRequest("仅支持 8 位色深的 PNG");
        if (colorType != 2 && colorType != 6) throw Web.badRequest("仅支持 RGB 或 RGBA 格式的 PNG");
        if (compression != 0 || filter != 0) throw Web.badRequest("不支持该 PNG 压缩格式");
        if (interlace != 0) throw Web.badRequest("不支持隔行扫描 PNG");
        return new PngInfo(width, height);
    }

    public static CreateResult create(Database db, Config config, AuthUser user, byte[] buffer,
                                      Map<String, Object> input) throws IOException {
        PngInfo info = inspect(buffer, Authz.str(input.get("type")));
        String hash = Security.sha256(buffer);
        Map<String, Object> existing = byHash(db, hash);
        String type = Authz.str(input.get("type"));
        String model = Authz.str(input.get("model"));
        String name = Authz.str(input.get("name"));
        int isPublic = Authz.num(input.get("isPublic"));
        if (existing != null) {
            if (!user.id.equals(Authz.str(existing.get("uploaded_by"))) && !Authz.bool(existing.get("is_public"))) {
                throw Web.conflict("该材质已存在，但上传者将其设为私有");
            }
            if (user.id.equals(Authz.str(existing.get("uploaded_by")))) {
                db.update("UPDATE textures SET name = ?, model = ?, is_public = ? WHERE id = ?",
                        name, model, isPublic, existing.get("id"));
                return new CreateResult(row(db, existing.get("id")), false, false);
            }
            db.update("INSERT OR IGNORE INTO closet_items (user_id, texture_id, created_at) VALUES (?, ?, ?)",
                    user.id, existing.get("id"), Security.isoNow());
            return new CreateResult(row(db, existing.get("id")), false, true);
        }
        Path file = filePath(config, hash);
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            Files.write(file, buffer, StandardOpenOption.CREATE_NEW);
        }
        try {
            long id = db.insert("""
                    INSERT INTO textures
                    (hash, type, model, name, width, height, is_public, uploaded_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, hash, type, model, name, info.width(), info.height(), isPublic, user.id, Security.isoNow());
            return new CreateResult(row(db, id), true, false);
        } catch (RuntimeException e) {
            if (db.one("SELECT 1 FROM textures WHERE hash = ?", hash) == null) {
                Files.deleteIfExists(file);
            }
            throw e;
        }
    }

    public static byte[] readFile(Config config, String hash) {
        try {
            return Files.readAllBytes(filePath(config, hash));
        } catch (IOException e) {
            return null;
        }
    }

    public static void delete(Database db, Config config, Map<String, Object> row) {
        db.update("DELETE FROM textures WHERE id = ?", row.get("id"));
        if (db.one("SELECT 1 FROM textures WHERE hash = ?", Authz.str(row.get("hash"))) == null) {
            try {
                Files.deleteIfExists(filePath(config, Authz.str(row.get("hash"))));
            } catch (IOException ignored) {
            }
        }
    }

    public static Map<String, Object> setVisibility(Database db, Map<String, Object> row, boolean isPublic) {
        db.update("UPDATE textures SET is_public = ? WHERE id = ?", isPublic ? 1 : 0, row.get("id"));
        return row(db, row.get("id"));
    }

    public static Map<String, Object> rename(Database db, Map<String, Object> row, String name) {
        String clean = Security.sanitize(name, 48);
        if (clean.isEmpty()) throw Web.badRequest("材质名称不能为空");
        db.update("UPDATE textures SET name = ? WHERE id = ?", clean, row.get("id"));
        return row(db, row.get("id"));
    }

    public static Map<String, Object> closet(Database db, String userId, Map<String, String> query) {
        int page = Math.max(1, Users.parseInt(query.get("page"), 1));
        int perPage = Math.max(1, Math.min(60, Users.parseInt(first(query, "perPage", "size"), 24)));
        int offset = (page - 1) * perPage;
        List<String> conditions = new ArrayList<>();
        conditions.add("c.user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(userId);
        String type = query.get("type");
        if ("skin".equals(type) || "cape".equals(type)) {
            conditions.add("t.type = ?");
            params.add(type);
        }
        String q = query.getOrDefault("q", "").trim();
        if (!q.isEmpty()) {
            conditions.add("(COALESCE(c.name, t.name) LIKE ? ESCAPE '\\' OR t.name LIKE ? ESCAPE '\\')");
            params.add(Security.likePattern(q));
            params.add(Security.likePattern(q));
        }
        String where = "WHERE " + String.join(" AND ", conditions);
        Map<String, Object> count = db.one("""
                SELECT COUNT(*) AS count FROM closet_items c
                JOIN textures t ON t.id = c.texture_id %s
                """.formatted(where), params.toArray());
        int total = count == null ? 0 : Authz.num(count.get("count"));
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(perPage);
        pageParams.add(offset);
        List<Map<String, Object>> rows = db.query("""
                SELECT t.*, u.username AS uploader_name, u.nickname AS uploader_nickname,
                  c.name AS closet_name, c.created_at AS collected_at
                FROM closet_items c
                JOIN textures t ON t.id = c.texture_id
                LEFT JOIN users u ON u.id = t.uploaded_by
                %s ORDER BY c.created_at DESC LIMIT ? OFFSET ?
                """.formatted(where), pageParams.toArray());
        AuthUser viewer = new AuthUser();
        viewer.id = userId;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = serialize(row, viewer, true);
            item.put("closetName", row.get("closet_name") == null ? null : Authz.str(row.get("closet_name")));
            item.put("collectedAt", Authz.str(row.get("collected_at")));
            items.add(item);
        }
        return Page.of(items, total, page, perPage);
    }

    public static void addToCloset(Database db, String userId, int textureId) {
        Map<String, Object> row = row(db, textureId);
        if (row == null) throw Web.notFound("材质不存在");
        if (!Authz.bool(row.get("is_public")) && !userId.equals(Authz.str(row.get("uploaded_by")))) {
            throw Web.forbidden("该材质未公开");
        }
        db.update("INSERT OR IGNORE INTO closet_items (user_id, texture_id, created_at) VALUES (?, ?, ?)",
                userId, textureId, Security.isoNow());
    }

    public static void removeFromCloset(Database db, String userId, int textureId) {
        db.update("DELETE FROM closet_items WHERE user_id = ? AND texture_id = ?", userId, textureId);
    }

    public static void renameCloset(Database db, String userId, int textureId, String name) {
        if (db.one("SELECT 1 FROM closet_items WHERE user_id = ? AND texture_id = ?", userId, textureId) == null) {
            throw Web.notFound("衣柜中没有该材质");
        }
        String clean = Security.sanitize(name, 48);
        db.update("UPDATE closet_items SET name = ? WHERE user_id = ? AND texture_id = ?",
                clean.isEmpty() ? null : clean, userId, textureId);
    }

    public static Map<String, Object> stats(Database db) {
        Map<String, Object> textures = db.one("SELECT COUNT(*) AS count FROM textures WHERE is_public = 1");
        Map<String, Object> downloads = db.one("SELECT COALESCE(SUM(downloads), 0) AS count FROM textures");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("textures", textures == null ? 0 : Authz.num(textures.get("count")));
        result.put("downloads", downloads == null ? 0 : Authz.num(downloads.get("count")));
        return result;
    }

    public static Map<String, Object> mapForPlayer(Database db, Object textureId) {
        if (textureId == null) return null;
        Map<String, Object> row = row(db, textureId);
        if (row == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", Authz.num(row.get("id")));
        map.put("hash", Authz.str(row.get("hash")));
        map.put("name", Authz.str(row.get("name")));
        map.put("type", Authz.str(row.get("type")));
        map.put("model", Authz.str(row.get("model")));
        map.put("url", url(Authz.str(row.get("hash"))));
        return map;
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 24)
                | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8)
                | (data[offset + 3] & 0xff);
    }

    private static String first(Map<String, String> query, String... keys) {
        for (String key : keys) {
            if (query.containsKey(key)) return query.get(key);
        }
        return "";
    }
}

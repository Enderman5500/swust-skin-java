package cn.edu.swust.mc.skin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Auth {
    private static final String DUMMY_HASH = Security.hashPassword(Security.randomToken(16));

    private Auth() {
    }

    public record Credentials(Map<String, Object> row, boolean needsRehash) {
    }

    public record CreatedSession(String token, String csrf, String expiresAt) {
    }

    public static Map<String, Object> findUserByIdentifier(Database db, String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        if (value.isEmpty()) return null;
        return db.one("""
                SELECT * FROM users
                WHERE username = ? COLLATE NOCASE OR email = ? COLLATE NOCASE
                LIMIT 1
                """, value, value);
    }

    public static Credentials authenticate(Database db, String identifier, String password) {
        Map<String, Object> user = findUserByIdentifier(db, identifier);
        String stored = user == null ? DUMMY_HASH : Authz.str(user.get("password"));
        boolean valid = Security.verifyPassword(password == null ? "" : password, stored);
        if (user == null || !valid || !"ACTIVE".equals(Authz.str(user.get("status")))) return null;
        boolean needsRehash = !stored.matches("^\\$2[aby]\\$10\\$.*");
        return new Credentials(user, needsRehash);
    }

    public static CreatedSession createSession(Database db, String userId, Config config,
                                               String ip, String userAgent) {
        String token = Security.randomToken(32);
        String csrf = Security.randomToken(24);
        String createdAt = Security.isoNow();
        String expiresAt = Instant.now().plus(config.sessionDays, ChronoUnit.DAYS).toString();
        db.update("DELETE FROM sessions WHERE expires_at <= ?", createdAt);
        db.update("""
                INSERT INTO sessions (token_hash, user_id, csrf, created_at, expires_at, ip, user_agent)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, Security.sha256(token), userId, csrf, createdAt, expiresAt,
                ip == null ? null : ip, truncate(userAgent, 300));
        return new CreatedSession(token, csrf, expiresAt);
    }

    public static Web.Session loadSession(Database db, String token) {
        if (token == null || token.isEmpty()) return null;
        String tokenHash = Security.sha256(token);
        Map<String, Object> session = db.one(
                "SELECT user_id, csrf, expires_at FROM sessions WHERE token_hash = ?", tokenHash);
        if (session == null) return null;
        if (Authz.str(session.get("expires_at")).compareTo(Security.isoNow()) <= 0) {
            db.update("DELETE FROM sessions WHERE token_hash = ?", tokenHash);
            return null;
        }
        Map<String, Object> row = db.one("SELECT * FROM users WHERE id = ?", Authz.str(session.get("user_id")));
        if (row == null || !"ACTIVE".equals(Authz.str(row.get("status")))) {
            db.update("DELETE FROM sessions WHERE token_hash = ?", tokenHash);
            return null;
        }
        Web.Session loaded = new Web.Session();
        loaded.tokenHash = tokenHash;
        loaded.csrf = Authz.str(session.get("csrf"));
        loaded.expiresAt = Authz.str(session.get("expires_at"));
        loaded.user = Authz.authUser(db, row);
        return loaded;
    }

    public static void destroySession(Database db, String token) {
        if (token == null || token.isEmpty()) return;
        db.update("DELETE FROM sessions WHERE token_hash = ?", Security.sha256(token));
    }

    public static void destroyOtherSessions(Database db, String userId, String keepTokenHash) {
        db.update("DELETE FROM sessions WHERE user_id = ? AND token_hash <> ?",
                userId, keepTokenHash == null ? "" : keepTokenHash);
    }

    public static String sessionCookie(String token, Config config) {
        return Web.cookie(config.sessionCookie, token, config.sessionDays * 24L * 60 * 60, config.https);
    }

    public static String expiredSessionCookie(Config config) {
        return Web.cookie(config.sessionCookie, "", 0, config.https);
    }

    public static AuthUser requireUser(Web.Ctx ctx) {
        if (ctx.user == null) throw Web.unauthorized("请先登录");
        return ctx.user;
    }

    public static AuthUser requireAdmin(Web.Ctx ctx) {
        AuthUser user = requireUser(ctx);
        if (!user.admin) throw Web.forbidden("没有权限执行此操作");
        return user;
    }

    public static AuthUser requirePermission(Web.Ctx ctx, String code) {
        AuthUser user = requireUser(ctx);
        if (!user.can(code)) {
            throw Web.forbidden("没有权限执行此操作（需要 " + code + "）");
        }
        return user;
    }

    public static void assertCsrf(Web.Ctx ctx) {
        String provided = ctx.exchange.getRequestHeaders().getFirst("X-CSRF-Token");
        String expected = ctx.session == null ? "" : ctx.session.csrf;
        if (provided == null) provided = "";
        if (expected.isEmpty() || !MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8))) {
            throw Web.forbidden("页面已过期，请刷新页面后重试");
        }
    }

    public static Map<String, Object> sessionPayload(Database db, Config config, Web.Ctx ctx, AuthUser user) {
        CreatedSession session = createSession(db, user.id, config,
                Web.clientIp(ctx.exchange, config.trustProxy),
                ctx.exchange.getRequestHeaders().getFirst("User-Agent"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user", user.toMap());
        payload.put("csrfToken", session.csrf());
        payload.put("cookie", sessionCookie(session.token(), config));
        return payload;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}

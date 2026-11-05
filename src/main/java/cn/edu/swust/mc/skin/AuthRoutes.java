package cn.edu.swust.mc.skin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AuthRoutes {
    private AuthRoutes() {
    }

    public static void register(App app) {
        Web.Router router = app.router;

        router.post("/api/auth/email-code", ctx -> {
            if (!app.mail.enabled()) {
                throw new Web.HttpError(503, "mail_disabled", "邮件服务暂未启用，请联系管理员");
            }
            Map<String, Object> body = ctx.json();
            String email = Authz.str(body.get("email")).trim().toLowerCase();
            if (!Security.validEmail(email)) {
                throw new Web.HttpError(400, "invalid_email", "请输入有效的邮箱地址");
            }
            if (app.db.one("SELECT id FROM users WHERE email = ? COLLATE NOCASE", email) != null) {
                throw Web.conflict("该邮箱已被注册");
            }

            String ip = Web.clientIp(ctx.exchange, app.config.trustProxy);
            app.rateLimiter.hit("email-code-ip:" + ip, 20, 10 * 60 * 1000,
                    "验证码发送过于频繁，请稍后再试");
            app.rateLimiter.hit("email-code-email:" + email, 5, 10 * 60 * 1000,
                    "该邮箱验证码发送过于频繁，请稍后再试");

            long retryAfter = EmailCodes.cooldownRemaining(app.db, email);
            if (retryAfter > 0) {
                throw new Web.HttpError(429, "too_many_requests",
                        "请 " + retryAfter + " 秒后再发送验证码", Map.of("retryAfter", retryAfter));
            }

            LocalDate today = LocalDate.now(ZoneId.systemDefault());
            long untilMidnight = Math.max(1000L,
                    today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            - Instant.now().toEpochMilli());
            app.rateLimiter.hit("email-code-ip-day:" + ip + ":" + today, 5, untilMidnight,
                    "该 IP 今日验证码发送次数已达上限（5 次），请明天再试");

            String code = EmailCodes.generate();
            EmailCodes.store(app.db, email, code, ip);
            try {
                app.mail.sendVerificationCode(email, code, EmailCodes.CODE_MINUTES,
                        Settings.get(app.db, "site_name"), Settings.get(app.db, "site_short_name"));
            } catch (Exception e) {
                EmailCodes.clear(app.db, email);
                Log.error("发送邮箱验证码失败：" + e.getMessage());
                throw new Web.HttpError(502, "mail_send_failed", "验证码发送失败，请稍后再试");
            }

            ctx.ok(Map.of(
                    "ok", true,
                    "cooldown", EmailCodes.COOLDOWN_SECONDS,
                    "expiresIn", EmailCodes.CODE_MINUTES * 60
            ));
        });

        router.post("/api/auth/register", ctx -> {
            if (!"1".equals(Settings.get(app.db, "registration_open"))) {
                throw Web.forbidden("社团暂时关闭了注册，请联系管理员");
            }
            Map<String, Object> body = ctx.json();
            String username = Authz.str(body.get("username")).trim();
            String nickname = Authz.str(body.getOrDefault("nickname", username)).trim();
            if (nickname.length() > 64) nickname = nickname.substring(0, 64);
            String email = Authz.str(body.get("email")).trim().toLowerCase();
            String password = Authz.str(body.get("password"));
            String code = Authz.str(body.get("code")).trim();
            if (!Security.validUsername(username)) {
                throw new Web.HttpError(400, "invalid_username", "用户名需为 3–16 位字母、数字或下划线");
            }
            if (!Security.validEmail(email)) {
                throw new Web.HttpError(400, "invalid_email", "请输入有效的邮箱地址");
            }
            String passwordError = Security.validatePassword(password);
            if (passwordError != null) throw new Web.HttpError(400, "invalid_password", passwordError);

            Map<String, Object> duplicate = app.db.one("""
                    SELECT username, email FROM users
                    WHERE username = ? COLLATE NOCASE OR email = ? COLLATE NOCASE
                    """, username, email);
            if (duplicate != null) {
                if (username.equalsIgnoreCase(Authz.str(duplicate.get("username")))) {
                    throw Web.conflict("该用户名已被注册");
                }
                throw Web.conflict("该邮箱已被注册");
            }
            if (!code.matches("\\d{6}") || !EmailCodes.verify(app.db, email, code)) {
                throw new Web.HttpError(400, "invalid_code", "邮箱验证码错误或已过期");
            }
            String now = Security.isoNow();
            String userId = Security.nextId(app.config.snowflakeDataCenter, app.config.snowflakeMachine);
            app.db.update("""
                    INSERT INTO users
                    (id, username, nickname, email, phone, qq, password, avatar_file_id, email_verified, status,
                     tags, fields, version, create_time, update_time, last_login_time)
                    VALUES (?, ?, ?, ?, NULL, NULL, ?, NULL, 1, 'ACTIVE', '[]', '{}', 0, ?, ?, ?)
                    """, userId, username, nickname.isEmpty() ? username : nickname, email,
                    Security.hashPassword(password), now, now, now);
            app.db.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, Authz.USER_ID);
            app.db.update("INSERT INTO user_depts (user_id, dept_id, is_default) VALUES (?, ?, 1)",
                    userId, Authz.ROOT_DEPT);

            AuthUser user = Authz.authUser(app.db, app.db.one("SELECT * FROM users WHERE id = ?", userId));
            Map<String, Object> session = Auth.sessionPayload(app.db, app.config, ctx, user);
            app.plugins.emit("user.registered", Map.of("user", user.toMap()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("user", user.toMap());
            data.put("csrfToken", session.get("csrfToken"));
            ctx.ok(data, 201, Map.of("Set-Cookie", Authz.str(session.get("cookie"))));
        });

        router.post("/api/auth/login", ctx -> {
            Map<String, Object> body = ctx.json();
            String identifier = Authz.str(body.get("identifier")).trim();
            String password = Authz.str(body.get("password"));
            if (identifier.isEmpty() || password.isEmpty()) {
                throw new Web.HttpError(400, "missing_credentials", "请输入用户名/邮箱和密码");
            }
            String ip = Web.clientIp(ctx.exchange, app.config.trustProxy);
            String limitKey = "login:" + ip + ":" + identifier.toLowerCase();
            app.rateLimiter.hit(limitKey, 10, 10 * 60 * 1000);
            app.rateLimiter.hit("login-ip:" + ip, 40, 10 * 60 * 1000);

            Auth.Credentials credentials = Auth.authenticate(app.db, identifier, password);
            if (credentials == null) {
                throw new Web.HttpError(401, "invalid_credentials", "用户名/邮箱或密码错误");
            }
            app.rateLimiter.reset(limitKey);
            String now = Security.isoNow();
            Map<String, Object> row = credentials.row();
            if (credentials.needsRehash()) {
                app.db.update("UPDATE users SET password = ?, password_changed_time = ? WHERE id = ?",
                        Security.hashPassword(password), now, Authz.str(row.get("id")));
            }
            app.db.update("UPDATE users SET last_login_time = ? WHERE id = ?", now, Authz.str(row.get("id")));
            AuthUser user = Authz.authUser(app.db,
                    app.db.one("SELECT * FROM users WHERE id = ?", Authz.str(row.get("id"))));
            Map<String, Object> session = Auth.sessionPayload(app.db, app.config, ctx, user);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("user", user.toMap());
            data.put("csrfToken", session.get("csrfToken"));
            ctx.ok(data, 200, Map.of("Set-Cookie", Authz.str(session.get("cookie"))));
        });

        router.post("/api/auth/logout", ctx -> {
            if (ctx.token != null) Auth.destroySession(app.db, ctx.token);
            ctx.ok(Map.of("ok", true), 200, Map.of("Set-Cookie", Auth.expiredSessionCookie(app.config)));
        });

        router.get("/api/auth/me", ctx -> {
            if (ctx.user == null) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("user", null);
                data.put("csrfToken", null);
                ctx.ok(data);
                return;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("user", ctx.user.toMap());
            data.put("csrfToken", ctx.session.csrf);
            data.put("counts", counts(app.db, ctx.user.id));
            ctx.ok(data);
        });

        router.patch("/api/account", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            Map<String, Object> body = ctx.json();
            String email = Authz.str(body.get("email")).trim().toLowerCase();
            if (!Security.validEmail(email)) {
                throw new Web.HttpError(400, "invalid_email", "请输入有效的邮箱地址");
            }
            Map<String, Object> duplicate = app.db.one(
                    "SELECT id FROM users WHERE email = ? COLLATE NOCASE AND id <> ?", email, user.id);
            if (duplicate != null) throw Web.conflict("该邮箱已被其他账号使用");
            app.db.update("UPDATE users SET email = ?, update_time = ?, version = version + 1 WHERE id = ?",
                    email, Security.isoNow(), user.id);
            AuthUser updated = Authz.authUser(app.db, app.db.one("SELECT * FROM users WHERE id = ?", user.id));
            ctx.ok(Map.of("user", updated.toMap()));
        });

        router.post("/api/account/password", ctx -> {
            AuthUser user = Auth.requireUser(ctx);
            Map<String, Object> body = ctx.json();
            String current = Authz.str(body.get("currentPassword"));
            String next = Authz.str(body.get("newPassword"));
            Map<String, Object> row = app.db.one("SELECT * FROM users WHERE id = ?", user.id);
            if (row == null || !Security.verifyPassword(current, Authz.str(row.get("password")))) {
                throw new Web.HttpError(400, "invalid_password", "当前密码不正确");
            }
            String passwordError = Security.validatePassword(next);
            if (passwordError != null) throw new Web.HttpError(400, "invalid_password", passwordError);
            if (current.equals(next)) {
                throw new Web.HttpError(400, "invalid_password", "新密码不能与当前密码相同");
            }
            String now = Security.isoNow();
            app.db.update("""
                    UPDATE users SET password = ?, password_changed_time = ?, update_time = ?,
                      version = version + 1 WHERE id = ?
                    """, Security.hashPassword(next), now, now, user.id);
            Auth.destroyOtherSessions(app.db, user.id, ctx.session.tokenHash);
            ctx.ok(Map.of("ok", true));
        });
    }

    private static Map<String, Object> counts(Database db, String userId) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("players", scalar(db, "SELECT COUNT(*) AS count FROM players WHERE user_id = ?", userId));
        counts.put("textures", scalar(db, "SELECT COUNT(*) AS count FROM textures WHERE uploaded_by = ?", userId));
        counts.put("closet", scalar(db, "SELECT COUNT(*) AS count FROM closet_items WHERE user_id = ?", userId));
        return counts;
    }

    private static int scalar(Database db, String sql, Object... params) {
        Map<String, Object> row = db.one(sql, params);
        return row == null ? 0 : Authz.num(row.get("count"));
    }
}

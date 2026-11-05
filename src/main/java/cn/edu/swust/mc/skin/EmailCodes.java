package cn.edu.swust.mc.skin;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

public final class EmailCodes {
    public static final int CODE_MINUTES = 10;
    public static final int COOLDOWN_SECONDS = 120;
    public static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private EmailCodes() {
    }

    public static String generate() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public static long cooldownRemaining(Database db, String email) {
        Map<String, Object> row = db.one("""
                SELECT created_at FROM email_verifications
                WHERE email = ? ORDER BY id DESC LIMIT 1
                """, email);
        if (row == null) return 0;
        try {
            Instant created = Instant.parse(Authz.str(row.get("created_at")));
            long elapsed = Duration.between(created, Instant.now()).getSeconds();
            return Math.max(0, COOLDOWN_SECONDS - elapsed);
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    public static void store(Database db, String email, String code, String ip) {
        Instant now = Instant.now();
        db.update("DELETE FROM email_verifications WHERE email = ? OR expires_at < ?",
                email, now.toString());
        db.update("""
                INSERT INTO email_verifications (email, code_hash, expires_at, attempts, ip, created_at)
                VALUES (?, ?, ?, 0, ?, ?)
                """, email, Security.hashPassword(code),
                now.plusSeconds(CODE_MINUTES * 60L).toString(), ip, now.toString());
    }

    public static boolean verify(Database db, String email, String code) {
        Map<String, Object> row = db.one("""
                SELECT * FROM email_verifications
                WHERE email = ? ORDER BY id DESC LIMIT 1
                """, email);
        if (row == null) return false;

        String expiresAt = Authz.str(row.get("expires_at"));
        try {
            if (Instant.parse(expiresAt).isBefore(Instant.now())) {
                clear(db, email);
                return false;
            }
        } catch (DateTimeParseException e) {
            clear(db, email);
            return false;
        }

        if (Authz.num(row.get("attempts")) >= MAX_ATTEMPTS) {
            clear(db, email);
            return false;
        }

        if (!Security.verifyPassword(code, Authz.str(row.get("code_hash")))) {
            db.update("UPDATE email_verifications SET attempts = attempts + 1 WHERE id = ?", row.get("id"));
            return false;
        }

        clear(db, email);
        return true;
    }

    public static void clear(Database db, String email) {
        db.update("DELETE FROM email_verifications WHERE email = ?", email);
    }
}

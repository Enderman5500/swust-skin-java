package cn.edu.swust.mc.skin;

import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

public final class Security {
    public static final String PLAYER_UUID_NAMESPACE = "SWUSTMCSkin:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long EPOCH = 1704067200000L;
    private static long lastTimestamp = -1L;
    private static long sequence = 0L;

    private Security() {
    }

    public static synchronized String nextId(long dataCenterId, long machineId) {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("系统时钟回拨，拒绝生成 ID");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & 4095L;
            if (sequence == 0) {
                while (timestamp <= lastTimestamp) {
                    timestamp = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = timestamp;
        long id = ((timestamp - EPOCH) << 22) | (dataCenterId << 17) | (machineId << 12) | sequence;
        return Long.toUnsignedString(id);
    }

    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }

    public static boolean verifyPassword(String password, String stored) {
        if (stored == null || stored.isBlank()) return false;
        try {
            return BCrypt.checkpw(password, stored);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String validatePassword(String password) {
        String value = password == null ? "" : password;
        if (value.length() < 6) return "密码长度不能少于 6 位";
        boolean lower = value.matches(".*[a-z].*");
        boolean upper = value.matches(".*[A-Z].*");
        boolean digit = value.matches(".*[0-9].*");
        if ((lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0) < 2) {
            return "密码必须包含小写字母、大写字母、数字中的至少两类";
        }
        return null;
    }

    public static String randomToken(int bytes) {
        byte[] data = new byte[bytes];
        RANDOM.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static String randomHex(int bytes) {
        byte[] data = new byte[bytes];
        RANDOM.nextBytes(data);
        return hex(data);
    }

    public static String sha256(String value) {
        return hex(digest("SHA-256", value.getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha256(byte[] value) {
        return hex(digest("SHA-256", value));
    }

    public static String playerUuid(String name) {
        String value = PLAYER_UUID_NAMESPACE + name.toLowerCase(Locale.ROOT);
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    public static String stripUuid(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "").toLowerCase(Locale.ROOT);
    }

    public static String isoNow() {
        return Instant.now().toString();
    }

    public static String isoDaysFromNow(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).toString();
    }

    public static String isoDaysAgo(long days) {
        return Instant.now().minus(days, ChronoUnit.DAYS).toString();
    }

    private static byte[] digest(String algorithm, byte[] value) {
        try {
            return MessageDigest.getInstance(algorithm).digest(value);
        } catch (Exception e) {
            throw new IllegalStateException(algorithm + " 不可用", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) builder.append(String.format("%02x", b));
        return builder.toString();
    }

    public static boolean validUsername(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{3,16}");
    }

    public static boolean validPlayerName(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{3,16}");
    }

    public static boolean validEmail(String value) {
        return value != null && value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    }

    public static String sanitize(String value, int max) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    public static String likePattern(String value) {
        String text = value == null ? "" : value.trim();
        text = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + text + "%";
    }
}

package cn.edu.swust.mc.report;

public record PlayerTime(
        String uuid,
        String name,
        long totalMillis,
        long afkMillis,
        boolean online,
        boolean afk,
        String sessionStartedAt,
        String lastSeenAt
) {
}

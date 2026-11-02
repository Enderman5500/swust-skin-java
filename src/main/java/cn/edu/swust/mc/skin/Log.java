package cn.edu.swust.mc.skin;

import java.time.OffsetDateTime;

public final class Log {
    private Log() {
    }

    public static void info(String message) {
        System.out.println("[" + OffsetDateTime.now() + "] INFO  " + message);
    }

    public static void warn(String message) {
        System.out.println("[" + OffsetDateTime.now() + "] WARN  " + message);
    }

    public static void error(String message) {
        System.err.println("[" + OffsetDateTime.now() + "] ERROR " + message);
    }

    public static void error(Throwable error) {
        System.err.println("[" + OffsetDateTime.now() + "] ERROR " + error);
        error.printStackTrace(System.err);
    }
}

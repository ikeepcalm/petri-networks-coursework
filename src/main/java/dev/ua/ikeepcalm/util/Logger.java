package dev.ua.ikeepcalm.util;

import java.lang.management.ManagementFactory;
import java.util.Locale;

public class Logger {

    private static int stage;
    private static String stageName;

    public static synchronized void stage(String name) {
        stage++;
        stageName = name;
    }

    public static synchronized void info(String pattern, Object... args) {
        double elapsed = ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;

        String prefix = stageName == null
                ? String.format(Locale.ROOT, "[%7.1f с] ", elapsed)
                : String.format(Locale.ROOT, "[%7.1f с] [етап %d · %s] ", elapsed, stage, stageName);

        System.out.println(prefix + String.format(Locale.ROOT, pattern, args));
    }
}

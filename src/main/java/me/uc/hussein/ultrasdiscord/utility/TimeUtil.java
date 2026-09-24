package me.uc.hussein.ultrasdiscord.utility;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Date/time formatting, configurable from config.yml (general.*). */
public final class TimeUtil {
    private static volatile ZoneId zone = ZoneId.systemDefault();
    private static volatile DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static volatile DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static volatile DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm");

    private TimeUtil() {
    }

    public static void configure(String zoneId, String dateTimePattern, String datePattern, String timePattern) {
        try {
            zone = (zoneId == null || zoneId.isBlank() || zoneId.equalsIgnoreCase("SYSTEM"))
                    ? ZoneId.systemDefault() : ZoneId.of(zoneId);
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        dateTime = safe(dateTimePattern, "yyyy-MM-dd HH:mm");
        date = safe(datePattern, "yyyy-MM-dd");
        time = safe(timePattern, "HH:mm");
    }

    private static DateTimeFormatter safe(String pattern, String def) {
        try {
            return DateTimeFormatter.ofPattern(pattern == null || pattern.isBlank() ? def : pattern);
        } catch (Exception e) {
            return DateTimeFormatter.ofPattern(def);
        }
    }

    public static String dateTime(long ms) {
        return dateTime.format(Instant.ofEpochMilli(ms).atZone(zone));
    }

    public static String date(long ms) {
        return date.format(Instant.ofEpochMilli(ms).atZone(zone));
    }

    public static String time(long ms) {
        return time.format(Instant.ofEpochMilli(ms).atZone(zone));
    }

    public static String duration(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }
}

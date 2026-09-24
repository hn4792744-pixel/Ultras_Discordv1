package me.uc.hussein.ultrasdiscord.detection;

/** 0-29 normal, 30-49 monitor, 50-69 suspicious, 70-84 high, 85-100 critical (all editable in config.yml). */
public enum SuspicionLevel {
    NORMAL("normal"),
    MONITOR("monitor"),
    SUSPICIOUS("suspicious"),
    HIGH("high"),
    CRITICAL("critical");

    private static volatile double tMonitor = 30;
    private static volatile double tSuspicious = 50;
    private static volatile double tHigh = 70;
    private static volatile double tCritical = 85;

    private final String key;

    SuspicionLevel(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static void configure(double monitor, double suspicious, double high, double critical) {
        tMonitor = monitor;
        tSuspicious = suspicious;
        tHigh = high;
        tCritical = critical;
    }

    public static double suspiciousThreshold() {
        return tSuspicious;
    }

    public static SuspicionLevel of(double v) {
        if (v >= tCritical) return CRITICAL;
        if (v >= tHigh) return HIGH;
        if (v >= tSuspicious) return SUSPICIOUS;
        if (v >= tMonitor) return MONITOR;
        return NORMAL;
    }
}

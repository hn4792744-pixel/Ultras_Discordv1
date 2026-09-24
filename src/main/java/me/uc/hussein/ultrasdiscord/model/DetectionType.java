package me.uc.hussein.ultrasdiscord.model;

import java.util.Locale;

/** Kinds of detections/warnings the plugin can raise. */
public enum DetectionType {
    XRAY("xray", "ultras.xray"),
    FLY("fly", "ultras.fly"),
    AUTOCLICKER("autoclicker", "ultras.autoclicker"),
    COMBAT("combat", "ultras.logs");

    private final String key;
    private final String permission;

    DetectionType(String key, String permission) {
        this.key = key;
        this.permission = permission;
    }

    public String key() {
        return key;
    }

    public String permission() {
        return permission;
    }

    public static DetectionType fromKey(String s) {
        if (s == null) return null;
        String k = s.toLowerCase(Locale.ROOT);
        for (DetectionType t : values()) {
            if (t.key.equals(k)) return t;
        }
        return null;
    }
}

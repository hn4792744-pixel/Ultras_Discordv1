package me.uc.hussein.ultrasdiscord.model;

import java.util.List;

/** Immutable stored warning. */
public final class WarningRecord {
    private final int id;
    private final DetectionType type;
    private final long time;
    private final double suspicion;
    private final String reason;
    private final List<String> details;

    public WarningRecord(int id, DetectionType type, long time, double suspicion, String reason, List<String> details) {
        this.id = id;
        this.type = type;
        this.time = time;
        this.suspicion = suspicion;
        this.reason = reason == null ? "" : reason;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public int id() { return id; }
    public DetectionType type() { return type; }
    public long time() { return time; }
    public double suspicion() { return suspicion; }
    public String reason() { return reason; }
    public List<String> details() { return details; }
}

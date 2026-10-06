package com.sentryrf.app;

import java.util.Locale;

final class Observation {
    final String key;
    final String radio;
    final String id;
    String name;
    String category;
    String label;
    String confidence;
    String raw;
    int risk;
    int rssi;
    int seenCount;
    long firstSeen;
    long lastSeen;
    boolean baselineKnown;

    Observation(String key, String radio, String id, String name, int rssi, SignatureMatch match,
                String raw, long now, boolean baselineKnown) {
        this.key = key;
        this.radio = radio;
        this.id = id;
        this.name = clean(name);
        this.category = match.category;
        this.label = match.label;
        this.confidence = match.confidence;
        this.raw = raw == null ? "" : raw;
        this.risk = match.risk;
        this.rssi = rssi;
        this.firstSeen = now;
        this.lastSeen = now;
        this.seenCount = 1;
        this.baselineKnown = baselineKnown;
    }

    void update(String name, int rssi, SignatureMatch match, String raw, long now, boolean baselineKnown) {
        if (name != null && !name.trim().isEmpty()) this.name = clean(name);
        this.rssi = rssi;
        this.lastSeen = now;
        this.seenCount++;
        this.baselineKnown = baselineKnown;
        if (match.risk >= this.risk) {
            this.category = match.category;
            this.label = match.label;
            this.confidence = match.confidence;
            this.risk = match.risk;
        }
        if (raw != null && !raw.isEmpty()) this.raw = raw;
    }

    int effectiveRisk() {
        int score = risk;
        if (!baselineKnown) score += 8;
        if (rssi >= -50) score += 4;
        if (seenCount >= 8) score += 3;
        return Math.min(100, score);
    }

    String displayName() {
        if (name == null || name.isEmpty() || "<unknown>".equals(name)) return label;
        return name;
    }

    String subtitle() {
        String state = baselineKnown ? "KNOWN" : "NEW";
        return String.format(Locale.US, "%s · %s confidence · %s\n%s", label, confidence, state, id);
    }

    private static String clean(String s) {
        if (s == null) return "<unknown>";
        s = s.replace("\u0000", "").trim();
        return s.isEmpty() ? "<unknown>" : s;
    }
}

package com.sentryrf.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

final class ObservationStore {
    private static final String PREFS = "sentryrf";
    private static final String BASELINE = "baseline_keys";
    private final SharedPreferences prefs;
    private final Set<String> baseline = new HashSet<>();

    ObservationStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(BASELINE, null);
        if (saved != null) baseline.addAll(saved);
    }

    boolean isKnown(String key) {
        return baseline.contains(key);
    }

    int baselineSize() {
        return baseline.size();
    }

    void replaceBaseline(Set<String> keys) {
        baseline.clear();
        baseline.addAll(keys);
        prefs.edit().putStringSet(BASELINE, new HashSet<>(baseline)).apply();
    }
}

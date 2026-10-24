package com.tracex.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.tracex.util.KeyUtil;
import com.tracex.util.NameMatcher;
import com.tracex.util.EntityTypes;

/**
 * Merges different spellings of the same person into one node. One instance is created per upload,
 * pre-loaded with the people already in the graph so repeat uploads merge correctly.
 */
public class EntityResolver {

    /** The canonical key and display label a raw name resolved to. */
    public record Resolved(String key, String label) {
    }

    private final Map<String, String> canonical = new LinkedHashMap<>();
    private final Set<String> mergeLog = new LinkedHashSet<>();

    /** Registers a person that already exists in the graph. */
    public void register(String key, String label) {
        canonical.put(key, label);
    }

    public Resolved resolve(String rawName) {
        String clean = rawName.trim().replaceAll("\\s+", " ");
        for (Map.Entry<String, String> entry : canonical.entrySet()) {
            if (NameMatcher.isSame(entry.getValue(), clean)) {
                if (!entry.getValue().equalsIgnoreCase(clean)) {
                    mergeLog.add("Merged \"" + clean + "\" into \"" + entry.getValue() + "\"");
                }
                if (clean.length() > entry.getValue().length()) {
                    entry.setValue(clean);
                }
                return new Resolved(entry.getKey(), entry.getValue());
            }
        }
        String key = KeyUtil.key(EntityTypes.PERSON, NameMatcher.normalize(clean));
        canonical.put(key, clean);
        return new Resolved(key, clean);
    }

    public Set<String> getMergeLog() {
        return mergeLog;
    }
}

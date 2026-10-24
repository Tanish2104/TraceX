package com.tracex.dto;

import com.tracex.util.RelationshipFormatter;

/**
 * Represents an inferred or missing link between two suspects who are not directly
 * connected in official records, but share a common intermediary (middleman, phone,
 * vehicle, bank account, or co-occurrence).
 */
public record MissingLink(
        String personAKey,
        String personALabel,
        String personBKey,
        String personBLabel,
        String sharedKey,
        String sharedLabel,
        String sharedType,
        String linkAKind,
        String linkBKind,
        String formattedType,
        String rationale
) {

    /**
     * Convenience constructor that automatically computes human-friendly formattedType
     * from linkAKind and linkBKind using RelationshipFormatter.
     */
    public MissingLink(String personAKey, String personALabel, String personBKey, String personBLabel,
                       String sharedKey, String sharedLabel, String sharedType,
                       String linkAKind, String linkBKind, String rationale) {
        this(personAKey, personALabel, personBKey, personBLabel, sharedKey, sharedLabel, sharedType,
             linkAKind, linkBKind, RelationshipFormatter.formatCombinedType(linkAKind, linkBKind), rationale);
    }

    public String getFormattedType() {
        return formattedType != null ? formattedType : RelationshipFormatter.formatCombinedType(linkAKind, linkBKind);
    }

    public String getFormattedKindA() {
        return RelationshipFormatter.formatKind(linkAKind);
    }

    public String getFormattedKindB() {
        return RelationshipFormatter.formatKind(linkBKind);
    }
}

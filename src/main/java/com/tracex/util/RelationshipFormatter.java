package com.tracex.util;

/**
 * Utility for formatting raw graph relationship kinds (e.g. MENTIONED_WITH, CALLED, OWNS)
 * into professional, police-grade forensic terminology.
 */
public final class RelationshipFormatter {

    private RelationshipFormatter() {}

    /**
     * Converts a single raw edge kind into human-readable forensic terminology.
     */
    public static String formatKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return "Associated";
        }
        String normalized = kind.trim().toUpperCase();
        return switch (normalized) {
            case "MENTIONED_WITH", "CO_ACCUSED" -> "Co-accused in FIR";
            case "OWNS", "REGISTERED_TO" -> "Owner / Registered";
            case "CALLED" -> "Phone Call Contact";
            case "TRANSFERRED_TO", "TRANSACTION", "SENT_FUNDS", "TRANSFERRED" -> "Fund Transfer";
            case "ASSOCIATED_WITH" -> "Direct Associate";
            case "LOCATED_AT" -> "Geographic Location";
            default -> {
                String clean = kind.replace('_', ' ').toLowerCase();
                yield Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
            }
        };
    }

    /**
     * Formats two relationship types into a concise, professional label for intermediary display.
     */
    public static String formatCombinedType(String kindA, String kindB) {
        if (kindA == null && kindB == null) return "Connected";
        String a = formatKind(kindA);
        String b = formatKind(kindB);
        if (a.equalsIgnoreCase(b)) {
            if ("Co-accused in FIR".equalsIgnoreCase(a)) {
                return "Co-Accused in FIR";
            }
            if ("Owner / Registered".equalsIgnoreCase(a)) {
                return "Shared Ownership";
            }
            if ("Phone Call Contact".equalsIgnoreCase(a)) {
                return "Mutual Call Contacts";
            }
            if ("Fund Transfer".equalsIgnoreCase(a)) {
                return "Mutual Transactors";
            }
            return a + " (Both)";
        }
        return a + " / " + b;
    }
}

package com.tracex.util;

/** Normalises raw values and builds the unique key of a graph node. */
public final class KeyUtil {

    private KeyUtil() {
    }

    /** Node key, e.g. PHONE:9822011111. */
    public static String key(String type, String normalizedValue) {
        return type + ":" + normalizedValue;
    }

    /** Returns a 10-digit mobile number, or null when the input is not a valid one. */
    public static String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.matches("[6-9]\\d{9}") ? digits : null;
    }

    /** Returns an upper-case plate without spaces, or null when invalid. */
    public static String normalizePlate(String raw) {
        if (raw == null) {
            return null;
        }
        String plate = raw.toUpperCase().replaceAll("[\\s-]", "");
        return plate.matches("[A-Z]{2}\\d{2}[A-Z]{1,3}\\d{4}") ? plate : null;
    }

    /** Returns the digits of a 9 to 18 digit account number, or null when invalid. */
    public static String normalizeAccount(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.matches("\\d{9,18}") ? digits : null;
    }

    /** Lower-case, single-spaced text used to build keys for places. */
    public static String normalizeText(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}

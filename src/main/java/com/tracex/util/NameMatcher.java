package com.tracex.util;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;

/**
 * Decides whether two spellings refer to the same person.
 * Rule 1: Jaro-Winkler similarity at or above the threshold (typos).
 * Rule 2: same last name and one first name is just an initial ("R. Patil" and "Rahul Patil").
 * Rule 3: middle name omissions (e.g. "Shubham Khairnar" and "Shubham Madhukar Khairnar").
 */
public final class NameMatcher {

    public static final double THRESHOLD = 0.94;

    private static final JaroWinklerSimilarity JARO_WINKLER = new JaroWinklerSimilarity();

    private NameMatcher() {
    }

    /** Lower-case letters and single spaces only. */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase().replaceAll("[^a-z\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    public static boolean isSame(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return false;
        }
        if (na.equals(nb)) {
            return true;
        }
        if (JARO_WINKLER.apply(na, nb) >= THRESHOLD) {
            return true;
        }
        String[] ta = na.split(" ");
        String[] tb = nb.split(" ");
        if (ta.length >= 2 && tb.length >= 2 && ta[ta.length - 1].equals(tb[tb.length - 1])) {
            String fa = ta[0];
            String fb = tb[0];
            if (fa.equals(fb)) {
                return true;
            }
            return (fa.length() == 1 && fb.startsWith(fa)) || (fb.length() == 1 && fa.startsWith(fb));
        }
        return false;
    }
}
package com.tracex.util;

import java.util.regex.Pattern;

/** Regular expressions used to pull entities out of free text (FIR narratives). */
public final class Patterns {

    /** Indian mobile number, optionally prefixed with +91 / 91 and optionally written as 98220 55555. */
    public static final Pattern PHONE =
            Pattern.compile("(?<!\\d)(?:\\+?91[\\s-]?)?[6-9]\\d{4}[\\s-]?\\d{5}(?!\\d)");

    /** Vehicle plate such as MH12AB1234 or MH 12 AB 1234. */
    public static final Pattern PLATE =
            Pattern.compile("\\b[A-Z]{2}[ -]?\\d{2}[ -]?[A-Z]{1,3}[ -]?\\d{4}\\b");

    /** Bank account, only when introduced by a cue such as "A/C", "acct" or "account number". */
    public static final Pattern ACCOUNT =
            Pattern.compile("(?i)(?:a/c|acct|account)(?:\\s*(?:no\\.?|number))?\\s*[:#-]?\\s*(\\d{9,18})");

    /** Person introduced by a title: "Mr. Suresh Kale", "Dr. Manoj Shirure", "Shri R. Patil". Group 1 is the name. */
    public static final Pattern PERSON =
            Pattern.compile("\\b(?:Mr|Mrs|Ms|Shri|Smt|Dr|Prof)\\.?\\s+((?:[A-Z]\\.\\s*)?[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)");

    private Patterns() {
    }
}
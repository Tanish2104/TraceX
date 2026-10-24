package com.tracex.service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.springframework.stereotype.Component;

import com.tracex.util.KeyUtil;
import com.tracex.util.Patterns;

/**
 * Pattern-based extraction: phones, plates, accounts and titled person names from free text.
 * This is the fallback used when the AI service is not running, and it is also merged with the AI results.
 */
@Component
public class EntityExtractor {

    /** Everything found in one piece of text, normalised and de-duplicated. */
    public static class Extracted {
        public final Set<String> phones = new LinkedHashSet<>();
        public final Set<String> plates = new LinkedHashSet<>();
        public final Set<String> accounts = new LinkedHashSet<>();
        public final Set<String> persons = new LinkedHashSet<>();
        public final Set<String> places = new LinkedHashSet<>();

        /** Which method found each value. Key is TYPE:value. Values not listed here were found by pattern rules. */
        private final Map<String, String> sources = new HashMap<>();

        public void setSource(String type, String value, String source) {
            sources.put(type + ":" + value, source);
        }

        /** "pattern", "spaCy" or "spaCy + pattern". */
        public String sourceOf(String type, String value) {
            return sources.getOrDefault(type + ":" + value, "pattern");
        }
    }

    public Extracted extract(String text) {
        Extracted out = new Extracted();
        if (text == null || text.isBlank()) {
            return out;
        }

        Matcher m = Patterns.PHONE.matcher(text);
        while (m.find()) {
            String phone = KeyUtil.normalizePhone(m.group());
            if (phone != null) {
                out.phones.add(phone);
            }
        }

        m = Patterns.PLATE.matcher(text);
        while (m.find()) {
            String plate = KeyUtil.normalizePlate(m.group());
            if (plate != null) {
                out.plates.add(plate);
            }
        }

        m = Patterns.ACCOUNT.matcher(text);
        while (m.find()) {
            String account = KeyUtil.normalizeAccount(m.group(1));
            if (account != null) {
                out.accounts.add(account);
            }
        }

        m = Patterns.PERSON.matcher(text);
        while (m.find()) {
            out.persons.add(m.group(1).trim());
        }
        return out;
    }
}

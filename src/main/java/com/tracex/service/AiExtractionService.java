package com.tracex.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.tracex.util.EntityTypes;
import com.tracex.util.KeyUtil;
import com.tracex.util.NameMatcher;

/**
 * Combines the AI service (spaCy) with the built-in pattern rules. Pattern results are always kept, so adding
 * the AI can only add findings, never remove them. Each value remembers which method found it, and that
 * label is written into the link's evidence so a detective can see where a link came from.
 */
@Service
public class AiExtractionService {

    private static final String AI = "spaCy";
    private static final String BOTH = "spaCy + pattern";

    /** Results for a list of texts, in the same order, plus a description of how they were produced. */
    public static class Batch {
        private final List<EntityExtractor.Extracted> results;
        private final String mode;
        private final boolean aiUsed;

        Batch(List<EntityExtractor.Extracted> results, String mode, boolean aiUsed) {
            this.results = results;
            this.mode = mode;
            this.aiUsed = aiUsed;
        }

        public List<EntityExtractor.Extracted> getResults() {
            return results;
        }

        public String getMode() {
            return mode;
        }

        public boolean isAiUsed() {
            return aiUsed;
        }
    }

    private final EntityExtractor patterns;
    private final NlpClient nlp;

    public AiExtractionService(EntityExtractor patterns, NlpClient nlp) {
        this.patterns = patterns;
        this.nlp = nlp;
    }

    public Batch extractAll(List<String> texts) {
        List<EntityExtractor.Extracted> base = new ArrayList<>();
        for (String text : texts) {
            base.add(patterns.extract(text));
        }

        Optional<NlpClient.NlpResult> ai = nlp.extract(texts);
        if (ai.isEmpty() || ai.get().results().size() != texts.size()) {
            String why = nlp.isEnabled() ? "the AI service was not reachable" : "the AI service is switched off";
            return new Batch(base, "pattern matching only (" + why + ")", false);
        }

        List<EntityExtractor.Extracted> merged = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            merged.add(merge(base.get(i), ai.get().results().get(i)));
        }
        return new Batch(merged, "AI (spaCy model " + ai.get().model() + ") plus pattern matching", true);
    }

    /** Union of the two results. Identifiers from the AI are re-checked with the same rules as pattern results. */
    static EntityExtractor.Extracted merge(EntityExtractor.Extracted pattern, EntityExtractor.Extracted ai) {
        EntityExtractor.Extracted out = new EntityExtractor.Extracted();
        out.phones.addAll(pattern.phones);
        out.plates.addAll(pattern.plates);
        out.accounts.addAll(pattern.accounts);
        out.persons.addAll(pattern.persons);

        for (String raw : ai.phones) {
            addAi(out, EntityTypes.PHONE, KeyUtil.normalizePhone(raw), out.phones);
        }
        for (String raw : ai.plates) {
            addAi(out, EntityTypes.VEHICLE, KeyUtil.normalizePlate(raw), out.plates);
        }
        for (String raw : ai.accounts) {
            addAi(out, EntityTypes.ACCOUNT, KeyUtil.normalizeAccount(raw), out.accounts);
        }

        // People: compare spellings loosely, so "suresh  kale" and "Suresh Kale" count as the same finding.
        Map<String, String> byNormalized = new HashMap<>();
        for (String name : out.persons) {
            byNormalized.put(NameMatcher.normalize(name), name);
        }
        for (String name : ai.persons) {
            String normalized = NameMatcher.normalize(name);
            if (normalized.isEmpty()) {
                continue;
            }
            String existing = byNormalized.get(normalized);
            if (existing != null) {
                out.setSource(EntityTypes.PERSON, existing, BOTH);
            } else {
                out.persons.add(name);
                byNormalized.put(normalized, name);
                out.setSource(EntityTypes.PERSON, name, AI);
            }
        }

        Set<String> seenPlaces = new HashSet<>();
        for (String place : ai.places) {
            String normalized = KeyUtil.normalizeText(place);
            if (!normalized.isEmpty() && seenPlaces.add(normalized)) {
                out.places.add(place);
                out.setSource(EntityTypes.LOCATION, place, AI);
            }
        }
        return out;
    }

    private static void addAi(EntityExtractor.Extracted out, String type, String value, Set<String> target) {
        if (value == null) {
            return;
        }
        if (target.contains(value)) {
            out.setSource(type, value, BOTH);
        } else {
            target.add(value);
            out.setSource(type, value, AI);
        }
    }
}

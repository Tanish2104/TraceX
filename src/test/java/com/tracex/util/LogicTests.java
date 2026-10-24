package com.tracex.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tracex.dto.MissingLink;
import com.tracex.service.AiExtractionService;
import com.tracex.service.EntityExtractor;
import com.tracex.service.EntityResolver;
import com.tracex.service.NlpClient;

/** Tests for the pure logic (no database needed). Run with: mvn test */
class LogicTests {

    @Test
    void phoneNumbersAreNormalised() {
        assertEquals("9822011111", KeyUtil.normalizePhone("+91 98220 11111"));
        assertEquals("9822011111", KeyUtil.normalizePhone("919822011111"));
        assertNull(KeyUtil.normalizePhone("1234567890"));
    }

    @Test
    void platesAndAccountsAreNormalised() {
        assertEquals("MH12AB1234", KeyUtil.normalizePlate("MH 12 AB 1234"));
        assertNull(KeyUtil.normalizePlate("XYZ"));
        assertEquals("345678901234", KeyUtil.normalizeAccount("3456-7890-1234"));
        assertNull(KeyUtil.normalizeAccount("123"));
    }

    @Test
    void sameNamesAreMatchedAndDifferentOnesAreNot() {
        assertTrue(NameMatcher.isSame("R. Patil", "Rahul Patil"));
        assertTrue(NameMatcher.isSame("Rahul Patil", "Rahul Patel"));
        assertTrue(NameMatcher.isSame("Shubham Khairnar", "Shubham Madhukar Khairnar"));
        assertFalse(NameMatcher.isSame("Rahul Patil", "Suresh Kale"));
        assertFalse(NameMatcher.isSame("S. Patil", "Rahul Patil"));
    }

    @Test
    void entitiesAreExtractedFromNarrative() {
        EntityExtractor.Extracted found = new EntityExtractor().extract(
                "Contacted Mr. Suresh Kale on 9822022222. Paid to A/C 345678901234. "
                + "Shri R. Patil with MH 12 AB 1234, call +91 98220 55555.");
        assertEquals(new LinkedHashSet<>(List.of("9822022222", "9822055555")), found.phones);
        assertEquals(Set.of("MH12AB1234"), found.plates);
        assertEquals(Set.of("345678901234"), found.accounts);
        assertEquals(new LinkedHashSet<>(List.of("Suresh Kale", "R. Patil")), found.persons);
    }

    @Test
    void bareDigitsWithoutAnAccountCueAreIgnored() {
        assertTrue(new EntityExtractor().extract("ref 345678901234 only").accounts.isEmpty());
    }

    @Test
    void resolverMergesInitialsIntoFullName() {
        EntityResolver resolver = new EntityResolver();
        EntityResolver.Resolved first = resolver.resolve("R. Patil");
        EntityResolver.Resolved second = resolver.resolve("Rahul Patil");
        assertEquals(first.key(), second.key());
        assertEquals("Rahul Patil", second.label());
        assertEquals(1, resolver.getMergeLog().size());
    }

    @Test
    void aiResultsAreMergedWithPatternResultsAndSourcesAreRecorded() {
        NlpClient fake = new NlpClient(true, "http://127.0.0.1:1") {
            @Override
            public Optional<NlpResult> extract(List<String> texts) {
                EntityExtractor.Extracted ai = new EntityExtractor.Extracted();
                ai.persons.add("Suresh Kale");
                ai.persons.add("Rahul Patil");
                ai.places.add("Hinjewadi");
                ai.phones.add("9822022222");
                ai.phones.add("123");
                return Optional.of(new NlpResult("test-model", List.of(ai)));
            }
        };
        AiExtractionService service = new AiExtractionService(new EntityExtractor(), fake);
        AiExtractionService.Batch batch = service.extractAll(List.of("Contacted Mr. Suresh Kale on 9822022222."));
        EntityExtractor.Extracted found = batch.getResults().get(0);

        assertTrue(batch.isAiUsed());
        assertTrue(batch.getMode().contains("test-model"));
        assertEquals("spaCy + pattern", found.sourceOf(EntityTypes.PERSON, "Suresh Kale"));
        assertEquals("spaCy", found.sourceOf(EntityTypes.PERSON, "Rahul Patil"));
        assertEquals("spaCy + pattern", found.sourceOf(EntityTypes.PHONE, "9822022222"));
        assertEquals("spaCy", found.sourceOf(EntityTypes.LOCATION, "Hinjewadi"));
        assertEquals(Set.of("9822022222"), found.phones); // the invalid "123" from the AI is dropped
    }

    @Test
    void fallsBackToPatternMatchingWhenAiServiceIsOff() {
        AiExtractionService service = new AiExtractionService(new EntityExtractor(),
                new NlpClient(false, "http://127.0.0.1:1"));
        AiExtractionService.Batch batch = service.extractAll(List.of("Call 9822022222 about Mr. Suresh Kale."));
        assertFalse(batch.isAiUsed());
        assertTrue(batch.getMode().startsWith("pattern matching only"));
        assertEquals(Set.of("9822022222"), batch.getResults().get(0).phones);
        assertEquals("pattern", batch.getResults().get(0).sourceOf(EntityTypes.PERSON, "Suresh Kale"));
    }

    @Test
    void fallsBackWhenAiServiceIsUnreachable() {
        AiExtractionService service = new AiExtractionService(new EntityExtractor(),
                new NlpClient(true, "http://127.0.0.1:1"));
        AiExtractionService.Batch batch = service.extractAll(List.of("Call 9822022222."));
        assertFalse(batch.isAiUsed());
        assertEquals(Set.of("9822022222"), batch.getResults().get(0).phones);
    }

    @Test
    void missingLinkRecordHoldsValues() {
        MissingLink link = new MissingLink("keyA", "Rahul", "keyB", "Sunil", "shared", "Suresh", "PERSON", "CALLED", "CALLED", "Rationale");
        assertEquals("Rahul", link.personALabel());
        assertEquals("Sunil", link.personBLabel());
        assertEquals("Suresh", link.sharedLabel());
    }
}

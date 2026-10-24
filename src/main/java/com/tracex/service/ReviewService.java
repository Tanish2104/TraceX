package com.tracex.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tracex.dto.KeyPerson;
import com.tracex.model.jpa.ReviewDecision;
import com.tracex.repository.ReviewDecisionRepository;

/**
 * Human review gate. Nothing the system ranks counts as a finding until an officer accepts it.
 * Decisions are stored as new rows (never edited); the newest decision for an entity is the current one.
 */
@Service
public class ReviewService {

    public static final String PENDING = "PENDING";

    private final ReviewDecisionRepository repository;
    private final AuditService audit;

    public ReviewService(ReviewDecisionRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    /** Entity key to current decision (ACCEPTED or REJECTED). Entities without a decision are absent. */
    public Map<String, String> latestDecisions() {
        Map<String, String> latest = new HashMap<>();
        for (ReviewDecision d : repository.findAllByOrderByIdDesc()) {
            latest.putIfAbsent(d.getEntityKey(), d.getDecision());
        }
        return latest;
    }

    /** Fills in the decision field of every entity in the list (PENDING when none exists). */
    public void applyDecisions(List<KeyPerson> entities) {
        Map<String, String> latest = latestDecisions();
        for (KeyPerson k : entities) {
            k.setDecision(latest.getOrDefault(k.getKey(), PENDING));
        }
    }

    public void decide(String key, String label, String decision, String note, String reviewer) {
        String value = decision == null ? "" : decision.trim().toUpperCase();
        if (!value.equals("ACCEPTED") && !value.equals("REJECTED")) {
            throw new IllegalArgumentException("The decision must be ACCEPTED or REJECTED.");
        }
        String cleanNote = note == null ? "" : note.trim();
        if (cleanNote.length() > 300) {
            cleanNote = cleanNote.substring(0, 300);
        }
        repository.save(new ReviewDecision(key, label, value, cleanNote, reviewer));
        audit.log(reviewer, "REVIEW_" + value,
                "Entity \"" + label + "\" (" + key + ")" + (cleanNote.isEmpty() ? "" : ": " + cleanNote));
    }
}

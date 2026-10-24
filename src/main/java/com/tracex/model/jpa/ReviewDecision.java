package com.tracex.model.jpa;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An officer's accept or reject decision about a flagged entity. Decisions are never edited; the newest wins. */
@Entity
@Table(name = "review_decision")
public class ReviewDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_key", nullable = false, updatable = false, length = 200)
    private String entityKey;

    @Column(name = "entity_label", nullable = false, updatable = false, length = 200)
    private String entityLabel;

    @Column(nullable = false, updatable = false, length = 10)
    private String decision;

    @Column(name = "reviewer_note", updatable = false, length = 300)
    private String reviewerNote;

    @Column(nullable = false, updatable = false, length = 60)
    private String reviewer;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private LocalDateTime decidedAt;

    protected ReviewDecision() {
        // required by JPA
    }

    public ReviewDecision(String entityKey, String entityLabel, String decision, String reviewerNote, String reviewer) {
        this.entityKey = entityKey;
        this.entityLabel = entityLabel;
        this.decision = decision;
        this.reviewerNote = reviewerNote;
        this.reviewer = reviewer;
        this.decidedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getEntityKey() {
        return entityKey;
    }

    public String getEntityLabel() {
        return entityLabel;
    }

    public String getDecision() {
        return decision;
    }

    public String getReviewerNote() {
        return reviewerNote;
    }

    public String getReviewer() {
        return reviewer;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }
}

package com.tracex.model.jpa;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One line of the append-only audit trail. There are no setters and no update or delete path. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, updatable = false, length = 60)
    private String username;

    @Column(nullable = false, updatable = false, length = 60)
    private String action;

    @Column(updatable = false, length = 1000)
    private String details;

    protected AuditLog() {
        // required by JPA
    }

    public AuditLog(String username, String action, String details) {
        this.createdAt = LocalDateTime.now();
        this.username = username;
        this.action = action;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getUsername() {
        return username;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }
}

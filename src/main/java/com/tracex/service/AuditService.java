package com.tracex.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tracex.model.jpa.AuditLog;
import com.tracex.repository.AuditLogRepository;

/** Writes one line to the append-only audit trail for every important action. */
@Service
public class AuditService {

    private static final int MAX_DETAILS = 1000;

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(String username, String action, String details) {
        String text = details == null ? "" : details;
        if (text.length() > MAX_DETAILS) {
            text = text.substring(0, MAX_DETAILS - 3) + "...";
        }
        repository.save(new AuditLog(username, action, text));
    }

    public List<AuditLog> latest() {
        return repository.findTop200ByOrderByIdDesc();
    }
}

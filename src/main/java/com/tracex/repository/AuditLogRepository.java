package com.tracex.repository;

import java.util.List;

import org.springframework.data.repository.Repository;

import com.tracex.model.jpa.AuditLog;

/** Append-only: this interface exposes save and read methods only. */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    <S extends AuditLog> S save(S entity);

    List<AuditLog> findTop200ByOrderByIdDesc();
}

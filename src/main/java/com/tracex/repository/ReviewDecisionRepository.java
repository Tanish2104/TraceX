package com.tracex.repository;

import java.util.List;

import org.springframework.data.repository.Repository;

import com.tracex.model.jpa.ReviewDecision;

/** Append-only: this interface exposes save and read methods only. */
public interface ReviewDecisionRepository extends Repository<ReviewDecision, Long> {

    <S extends ReviewDecision> S save(S entity);

    List<ReviewDecision> findAllByOrderByIdDesc();
}

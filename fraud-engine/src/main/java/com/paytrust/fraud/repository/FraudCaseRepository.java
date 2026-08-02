package com.paytrust.fraud.repository;

import com.paytrust.fraud.domain.FraudCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FraudCaseRepository extends JpaRepository<FraudCase, Long> {
    Optional<FraudCase> findByIdempotencyKey(String idempotencyKey);
}

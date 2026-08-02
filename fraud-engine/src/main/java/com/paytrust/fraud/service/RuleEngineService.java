package com.paytrust.fraud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class RuleEngineService {

    private static final Logger log = LoggerFactory.getLogger(RuleEngineService.class);
    private final FeatureStoreService featureStoreService;

    // Rules thresholds
    private static final BigDecimal CRITICAL_AMOUNT_LIMIT = new BigDecimal("50000.00"); // Decline above this
    private static final BigDecimal REVIEW_AMOUNT_LIMIT = new BigDecimal("10000.00");   // Review above this
    private static final long VELOCITY_1H_LIMIT = 5;                                     // Decline if > 5 tx in 1h
    private static final long VELOCITY_24H_LIMIT = 20;                                   // Decline if > 20 tx in 24h

    public RuleEngineService(FeatureStoreService featureStoreService) {
        this.featureStoreService = featureStoreService;
    }

    public enum RuleDecision {
        APPROVE, DECLINE, REVIEW
    }

    public static class RuleResult {
        public final RuleDecision decision;
        public final String reason;

        public RuleResult(RuleDecision decision, String reason) {
            this.decision = decision;
            this.reason = reason;
        }
    }

    /**
     * Evaluate rules against the incoming transaction and account history features.
     */
    public RuleResult evaluateRules(String accountId, BigDecimal amount, String country) {
        // 1. Single Transaction Amount Rule
        if (amount.compareTo(CRITICAL_AMOUNT_LIMIT) >= 0) {
            log.warn("Rule Triggered: CRITICAL_AMOUNT_LIMIT for account {} (amount: {})", accountId, amount);
            return new RuleResult(RuleDecision.DECLINE, "Transaction amount exceeds critical limit of " + CRITICAL_AMOUNT_LIMIT);
        }
        if (amount.compareTo(REVIEW_AMOUNT_LIMIT) >= 0) {
            log.info("Rule Triggered: REVIEW_AMOUNT_LIMIT for account {} (amount: {})", accountId, amount);
            return new RuleResult(RuleDecision.REVIEW, "Transaction amount requires review: exceeds " + REVIEW_AMOUNT_LIMIT);
        }

        // 2. Velocity Rules from Feature Store
        long velocity1h = featureStoreService.getTransactionVelocity(accountId, 3600);
        if (velocity1h >= VELOCITY_1H_LIMIT) {
            log.warn("Rule Triggered: VELOCITY_1H_LIMIT for account {} (velocity: {})", accountId, velocity1h);
            return new RuleResult(RuleDecision.DECLINE, "High transaction velocity: " + velocity1h + " transfers in the last 1 hour");
        }

        long velocity24h = featureStoreService.getTransactionVelocity(accountId, 86400);
        if (velocity24h >= VELOCITY_24H_LIMIT) {
            log.warn("Rule Triggered: VELOCITY_24H_LIMIT for account {} (velocity: {})", accountId, velocity24h);
            return new RuleResult(RuleDecision.DECLINE, "Excessive 24h transaction velocity: " + velocity24h + " transfers in the last 24 hours");
        }

        // 3. Simple Blocked Country Check
        if (country != null && ("OFAC_BLOCKED".equalsIgnoreCase(country) || "NORTH_KOREA".equalsIgnoreCase(country))) {
            log.warn("Rule Triggered: BLOCKED_COUNTRY check for account {} (country: {})", accountId, country);
            return new RuleResult(RuleDecision.DECLINE, "Transaction originating from a sanctioned/blocked region: " + country);
        }

        // 4. Default approval
        return new RuleResult(RuleDecision.APPROVE, "Rules verified successfully");
    }
}

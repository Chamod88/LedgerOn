package com.paytrust.fraud.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class RuleEngineServiceTest {

    private FeatureStoreService featureStoreService;
    private RuleEngineService ruleEngineService;

    @BeforeEach
    void setUp() {
        featureStoreService = Mockito.mock(FeatureStoreService.class);
        ruleEngineService = new RuleEngineService(featureStoreService);
    }

    @Test
    void testEvaluateRules_Approve() {
        when(featureStoreService.getTransactionVelocity("ACC-001", 3600)).thenReturn(0L);
        when(featureStoreService.getTransactionVelocity("ACC-001", 86400)).thenReturn(0L);

        RuleEngineService.RuleResult result = ruleEngineService.evaluateRules(
                "ACC-001",
                new BigDecimal("100.00"),
                "USA"
        );

        assertThat(result.decision).isEqualTo(RuleEngineService.RuleDecision.APPROVE);
        assertThat(result.reason).contains("Rules verified successfully");
    }

    @Test
    void testEvaluateRules_DeclineCriticalAmount() {
        RuleEngineService.RuleResult result = ruleEngineService.evaluateRules(
                "ACC-001",
                new BigDecimal("60000.00"),
                "USA"
        );

        assertThat(result.decision).isEqualTo(RuleEngineService.RuleDecision.DECLINE);
        assertThat(result.reason).contains("exceeds critical limit");
    }

    @Test
    void testEvaluateRules_ReviewAmount() {
        RuleEngineService.RuleResult result = ruleEngineService.evaluateRules(
                "ACC-001",
                new BigDecimal("15000.00"),
                "USA"
        );

        assertThat(result.decision).isEqualTo(RuleEngineService.RuleDecision.REVIEW);
        assertThat(result.reason).contains("requires review");
    }

    @Test
    void testEvaluateRules_DeclineHighVelocity1h() {
        // Return 5 transactions in the last hour (reaches limit)
        when(featureStoreService.getTransactionVelocity("ACC-001", 3600)).thenReturn(5L);

        RuleEngineService.RuleResult result = ruleEngineService.evaluateRules(
                "ACC-001",
                new BigDecimal("500.00"),
                "USA"
        );

        assertThat(result.decision).isEqualTo(RuleEngineService.RuleDecision.DECLINE);
        assertThat(result.reason).contains("High transaction velocity");
    }

    @Test
    void testEvaluateRules_DeclineHighVelocity24h() {
        when(featureStoreService.getTransactionVelocity("ACC-001", 3600)).thenReturn(2L);
        when(featureStoreService.getTransactionVelocity("ACC-001", 86400)).thenReturn(20L);

        RuleEngineService.RuleResult result = ruleEngineService.evaluateRules(
                "ACC-001",
                new BigDecimal("500.00"),
                "USA"
        );

        assertThat(result.decision).isEqualTo(RuleEngineService.RuleDecision.DECLINE);
        assertThat(result.reason).contains("Excessive 24h transaction velocity");
    }

    @Test
    void testEvaluateRules_DeclineSanctionedCountry() {
        RuleEngineService.RuleResult result = ruleEngineService.evaluateRules(
                "ACC-001",
                new BigDecimal("500.00"),
                "OFAC_BLOCKED"
        );

        assertThat(result.decision).isEqualTo(RuleEngineService.RuleDecision.DECLINE);
        assertThat(result.reason).contains("sanctioned/blocked region");
    }
}

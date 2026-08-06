package com.paytrust.fraud.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OnnxInferenceServiceTest {

    private OnnxInferenceService onnxInferenceService;

    @BeforeEach
    void setUp() {
        onnxInferenceService = new OnnxInferenceService();
        onnxInferenceService.init();
    }

    @Test
    void testOnnxModelInference_LegitimateVector() {
        if (!onnxInferenceService.isModelLoaded()) {
            System.out.println("Skipping ONNX test: model not found on test classpath");
            return;
        }

        // 12-dimension vector representing a normal legitimate transaction
        float[] legitVector = new float[]{
                50.0f,  // amount
                0.0f,   // tx_velocity_1h
                2.0f,   // tx_velocity_24h
                60.0f,  // user_avg_amount_24h
                0.83f,  // amount_to_avg_ratio
                14.0f,  // time_of_day_hour
                0.0f,   // is_weekend
                120.0f, // device_fingerprint_age_days
                0.0f,   // is_international
                365.0f, // account_age_days
                0.0f,   // failed_attempts_1h
                2.5f    // distance_from_home_km
        };

        float fraudScore = onnxInferenceService.predictFraudProbability(legitVector);

        assertThat(fraudScore).isGreaterThanOrEqualTo(0.0f).isLessThanOrEqualTo(1.0f);
        assertThat(fraudScore).isLessThan(0.40f); // Low risk for normal transaction
    }

    @Test
    void testOnnxModelInference_FraudulentVector() {
        if (!onnxInferenceService.isModelLoaded()) {
            System.out.println("Skipping ONNX test: model not found on test classpath");
            return;
        }

        // 12-dimension vector representing a fraudulent transaction (high velocity burst / new device)
        float[] fraudVector = new float[]{
                2500.0f, // amount
                12.0f,   // tx_velocity_1h
                18.0f,   // tx_velocity_24h
                100.0f,  // user_avg_amount_24h
                25.0f,   // amount_to_avg_ratio
                3.0f,    // time_of_day_hour
                1.0f,    // is_weekend
                0.0f,    // device_fingerprint_age_days
                1.0f,    // is_international
                10.0f,   // account_age_days
                5.0f,    // failed_attempts_1h
                1500.0f  // distance_from_home_km
        };

        float fraudScore = onnxInferenceService.predictFraudProbability(fraudVector);

        assertThat(fraudScore).isGreaterThanOrEqualTo(0.0f).isLessThanOrEqualTo(1.0f);
        assertThat(fraudScore).isGreaterThanOrEqualTo(0.70f); // High risk for anomalous transaction
    }
}

package com.paytrust.fraud.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytrust.fraud.domain.FraudCase;
import com.paytrust.fraud.dto.FeedbackRequest;
import com.paytrust.fraud.dto.FraudScoreRequest;
import com.paytrust.fraud.dto.FraudScoreResponse;
import com.paytrust.fraud.repository.FraudCaseRepository;
import com.paytrust.fraud.service.FeatureStoreService;
import com.paytrust.fraud.service.OnnxInferenceService;
import com.paytrust.fraud.service.RuleEngineService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/v1/fraud")
public class FraudController {

    private static final Logger log = LoggerFactory.getLogger(FraudController.class);
    private final RuleEngineService ruleEngineService;
    private final FeatureStoreService featureStoreService;
    private final OnnxInferenceService onnxInferenceService;
    private final FraudCaseRepository fraudCaseRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DUPLICATE_CACHE_PREFIX = "fraud:duplicate:";

    public FraudController(RuleEngineService ruleEngineService,
                           FeatureStoreService featureStoreService,
                           OnnxInferenceService onnxInferenceService,
                           FraudCaseRepository fraudCaseRepository,
                           StringRedisTemplate redisTemplate) {
        this.ruleEngineService = ruleEngineService;
        this.featureStoreService = featureStoreService;
        this.onnxInferenceService = onnxInferenceService;
        this.fraudCaseRepository = fraudCaseRepository;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/score")
    public ResponseEntity<FraudScoreResponse> getFraudScore(@Valid @RequestBody FraudScoreRequest request) {
        String correlationId = request.idempotencyKey != null ? request.idempotencyKey : "unknown";
        MDC.put("correlationId", correlationId);

        try {
            log.info("Received fraud scoring request for account: {}, amount: {}", request.accountId, request.amount);

            // 1. Cache Check for duplicate requests (TTL: 5min)
            if (request.idempotencyKey != null) {
                String cacheKey = DUPLICATE_CACHE_PREFIX + request.idempotencyKey;
                String cachedResponse = redisTemplate.opsForValue().get(cacheKey);
                if (cachedResponse != null) {
                    log.info("Cache hit: duplicate scoring request detected for key: {}", request.idempotencyKey);
                    FraudScoreResponse response = objectMapper.readValue(cachedResponse, FraudScoreResponse.class);
                    return ResponseEntity.ok(response);
                }
            }

            // 2. Feature Assembly for 12-Dimension Vector
            java.math.BigDecimal userAvgAmount = featureStoreService.getUserAverageAmount(request.accountId);
            if (userAvgAmount == null || userAvgAmount.compareTo(java.math.BigDecimal.ZERO) == 0) {
                userAvgAmount = request.amount;
            }
            double amountToAvgRatio = request.amount.doubleValue() / Math.max(1.0, userAvgAmount.doubleValue());

            long velocity1h = featureStoreService.getTransactionVelocity(request.accountId, 3600);
            long velocity24h = featureStoreService.getTransactionVelocity(request.accountId, 86400);

            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            int hour = now.getHour();
            int isWeekend = (now.getDayOfWeek().getValue() >= 6) ? 1 : 0;
            int isInternational = (request.country != null && !"USA".equalsIgnoreCase(request.country)) ? 1 : 0;

            float[] featureVector = new float[] {
                request.amount.floatValue(),
                (float) velocity1h,
                (float) velocity24h,
                userAvgAmount.floatValue(),
                (float) amountToAvgRatio,
                (float) hour,
                (float) isWeekend,
                30.0f, // device_fingerprint_age_days
                (float) isInternational,
                180.0f, // account_age_days
                0.0f,   // failed_attempts_1h
                5.0f    // distance_from_home_km
            };

            // 3. Rule Engine Evaluation
            RuleEngineService.RuleResult ruleResult = ruleEngineService.evaluateRules(
                    request.accountId,
                    request.amount,
                    request.country
            );

            // 4. ONNX ML Model Inference Evaluation
            float mlFraudProb = onnxInferenceService.predictFraudProbability(featureVector);
            log.info("ONNX ML Fraud Probability: {} for account: {}", mlFraudProb, request.accountId);

            String finalDecision;
            String finalReason;
            double finalScore;

            if (ruleResult.decision == RuleEngineService.RuleDecision.DECLINE) {
                finalDecision = "DECLINE";
                finalReason = ruleResult.reason;
                finalScore = 1.0;
            } else if (mlFraudProb >= 0.70f) {
                finalDecision = "DECLINE";
                finalReason = String.format("High ML Fraud Probability (Score: %.2f)", mlFraudProb);
                finalScore = mlFraudProb;
            } else if (mlFraudProb >= 0.40f || ruleResult.decision == RuleEngineService.RuleDecision.REVIEW) {
                finalDecision = "REVIEW";
                finalReason = ruleResult.decision == RuleEngineService.RuleDecision.REVIEW ?
                        ruleResult.reason : String.format("Elevated ML Fraud Risk (Score: %.2f)", mlFraudProb);
                finalScore = mlFraudProb;
            } else {
                finalDecision = "APPROVE";
                finalReason = "Transaction passed deterministic rules and ML risk scoring";
                finalScore = mlFraudProb;
            }

            FraudScoreResponse response = new FraudScoreResponse(
                    finalDecision,
                    finalReason,
                    finalScore
            );

            // 3. Save Case in PostgreSQL
            try {
                FraudCase fraudCase = new FraudCase();
                fraudCase.setAccountId(request.accountId);
                fraudCase.setAmount(request.amount);
                fraudCase.setCurrency(request.currency);
                fraudCase.setTransactionType(request.transactionType);
                fraudCase.setDecision(response.decision);
                fraudCase.setReason(response.reason);
                fraudCase.setIdempotencyKey(request.idempotencyKey);
                fraudCaseRepository.save(fraudCase);
                log.info("Recorded fraud case in DB for key: {}", request.idempotencyKey);
            } catch (Exception e) {
                log.error("Failed to save fraud case in PostgreSQL", e);
                // Continue responding to the client even if DB logging fails
            }

            // 4. Cache response in Redis
            if (request.idempotencyKey != null) {
                try {
                    String cacheKey = DUPLICATE_CACHE_PREFIX + request.idempotencyKey;
                    String jsonResponse = objectMapper.writeValueAsString(response);
                    redisTemplate.opsForValue().set(cacheKey, jsonResponse, Duration.ofMinutes(5));
                } catch (Exception e) {
                    log.error("Failed to cache fraud response in Redis", e);
                }
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error calculating fraud score", e);
            // Default Fallback: APPROVE transactions if the scoring engine encounters internal errors
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FraudScoreResponse("APPROVE", "Internal Scoring Engine Error: " + e.getMessage(), 0.0));
        } finally {
            MDC.remove("correlationId");
        }
    }

    @PostMapping("/feedback")
    public ResponseEntity<Object> submitFeedback(@Valid @RequestBody FeedbackRequest request) {
        log.info("Received analyst feedback for key: {}, label: {}", request.idempotencyKey, request.label);

        Optional<FraudCase> caseOpt = fraudCaseRepository.findByIdempotencyKey(request.idempotencyKey);
        if (caseOpt.isPresent()) {
            FraudCase fraudCase = caseOpt.get();
            fraudCase.setLabel(request.label.toUpperCase());
            fraudCase.setStatus("CLOSED");
            fraudCaseRepository.save(fraudCase);
            return ResponseEntity.ok(Map.of("message", "Feedback recorded successfully"));
        } else {
            // If the transaction wasn't scored, we create a placeholder labeled case
            FraudCase fraudCase = new FraudCase();
            fraudCase.setIdempotencyKey(request.idempotencyKey);
            fraudCase.setAccountId("UNKNOWN");
            fraudCase.setAmount(java.math.BigDecimal.ZERO);
            fraudCase.setCurrency("USD");
            fraudCase.setTransactionType("UNKNOWN");
            fraudCase.setDecision("UNKNOWN");
            fraudCase.setReason("Feedback placeholder created");
            fraudCase.setLabel(request.label.toUpperCase());
            fraudCase.setStatus("CLOSED");
            fraudCaseRepository.save(fraudCase);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "No existing case found. Created placeholder with label: " + request.label
            ));
        }
    }
}

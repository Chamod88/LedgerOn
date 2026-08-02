package com.paytrust.fraud.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytrust.fraud.domain.FraudCase;
import com.paytrust.fraud.dto.FeedbackRequest;
import com.paytrust.fraud.dto.FraudScoreRequest;
import com.paytrust.fraud.dto.FraudScoreResponse;
import com.paytrust.fraud.repository.FraudCaseRepository;
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
    private final FraudCaseRepository fraudCaseRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DUPLICATE_CACHE_PREFIX = "fraud:duplicate:";

    public FraudController(RuleEngineService ruleEngineService,
                           FraudCaseRepository fraudCaseRepository,
                           StringRedisTemplate redisTemplate) {
        this.ruleEngineService = ruleEngineService;
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

            // 2. Evaluate rules
            RuleEngineService.RuleResult ruleResult = ruleEngineService.evaluateRules(
                    request.accountId,
                    request.amount,
                    request.country
            );

            double score = ruleResult.decision == RuleEngineService.RuleDecision.DECLINE ? 1.0 :
                    (ruleResult.decision == RuleEngineService.RuleDecision.REVIEW ? 0.5 : 0.0);

            FraudScoreResponse response = new FraudScoreResponse(
                    ruleResult.decision.name(),
                    ruleResult.reason,
                    score
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

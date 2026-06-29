package com.ledger.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.math.BigDecimal;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}

// Data format representing the JSON incoming request
class TransferRequest {
    @NotBlank(message = "accountId is required")
    public String accountId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than zero")
    public BigDecimal amount;

    @NotBlank(message = "currency is required")
    public String currency;

    @NotBlank(message = "transactionType is required")
    public String transactionType;
}

@RestController
@RequestMapping("/api/v1/ledger")
class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public GatewayController(StringRedisTemplate redisTemplate, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/transfer")
    public ResponseEntity<Object> processTransfer(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        // 1. Validation for header
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_IDEMPOTENCY_KEY"));
        }

        // Set correlationId in MDC for tracing
        MDC.put("correlationId", idempotencyKey);
        log.info("Received transfer request for account: {}, amount: {}, type: {}", 
                request.accountId, request.amount, request.transactionType);

        try {
            // 2. Redis Idempotency Check
            Boolean isUnique = redisTemplate.opsForValue().setIfAbsent(
                    "idempotency:" + idempotencyKey,
                    "processing",
                    Duration.ofHours(24)
            );

            if (Boolean.FALSE.equals(isUnique)) {
                log.warn("Duplicate request detected for idempotency key: {}", idempotencyKey);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "DUPLICATE_REQUEST",
                        "message", "Transaction with this key is already processed or being processed."
                ));
            }

            // 3. Serialize and publish event to Kafka
            try {
                Map<String, Object> eventPayloadMap = Map.of(
                        "idempotencyKey", idempotencyKey,
                        "accountId", request.accountId,
                        "amount", request.amount,
                        "currency", request.currency,
                        "transactionType", request.transactionType
                );
                String eventPayload = objectMapper.writeValueAsString(eventPayloadMap);
                kafkaTemplate.send("ledger-transactions", request.accountId, eventPayload);
                log.info("Successfully published transaction to Kafka topic 'ledger-transactions'");
            } catch (Exception e) {
                log.error("Failed to serialize or publish event", e);
                // Clean up Redis key so request can be retried since publishing failed
                redisTemplate.delete("idempotency:" + idempotencyKey);
                return ResponseEntity.internalServerError().body(Map.of("error", "Failed to serialize event"));
            }

            // 4. Respond instantly with 202 Accepted
            return ResponseEntity.accepted().body(Map.of(
                    "transactionId", UUID.randomUUID().toString(),
                    "status", "QUEUED",
                    "message", "Transaction accepted for processing"
            ));
        } finally {
            MDC.remove("correlationId");
        }
    }
}
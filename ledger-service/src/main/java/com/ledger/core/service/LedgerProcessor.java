package com.ledger.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.core.dto.TransactionEvent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class LedgerProcessor {

    private final TransactionExecutor transactionExecutor;
    private final Validator validator;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(LedgerProcessor.class);

    public LedgerProcessor(TransactionExecutor transactionExecutor, Validator validator) {
        this.transactionExecutor = transactionExecutor;
        this.validator = validator;
    }

    @KafkaListener(topics = "ledger-transactions", groupId = "ledger-group")
    public void processTransaction(String message) {
        try {
            TransactionEvent event = objectMapper.readValue(message, TransactionEvent.class);
            
            // Set correlationId in MDC using the message's idempotency key
            MDC.put("correlationId", event.idempotencyKey);
            log.info("Processing event: {} for account {}", event.idempotencyKey, event.accountId);

            // Programmatic DTO validation
            Set<ConstraintViolation<TransactionEvent>> violations = validator.validate(event);
            if (!violations.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (ConstraintViolation<TransactionEvent> violation : violations) {
                    sb.append(violation.getPropertyPath()).append(" ").append(violation.getMessage()).append("; ");
                }
                throw new ConstraintViolationException("Invalid transaction event: " + sb.toString(), violations);
            }

            // Delegate core database transaction and financial rules to TransactionExecutor
            transactionExecutor.execute(event);

        } catch (Exception e) {
            log.error("Failed to process transaction event: {}", e.getMessage(), e);
            throw new RuntimeException("Rolling back transaction for retry", e);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
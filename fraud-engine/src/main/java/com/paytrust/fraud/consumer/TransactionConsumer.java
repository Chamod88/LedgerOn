package com.paytrust.fraud.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytrust.fraud.dto.TransactionEvent;
import com.paytrust.fraud.service.FeatureStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class TransactionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionConsumer.class);
    private final FeatureStoreService featureStoreService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TransactionConsumer(FeatureStoreService featureStoreService) {
        this.featureStoreService = featureStoreService;
    }

    @KafkaListener(topics = "ledger-transactions", groupId = "fraud-group")
    public void consumeTransaction(String message) {
        try {
            TransactionEvent event = objectMapper.readValue(message, TransactionEvent.class);
            if (event.idempotencyKey != null) {
                MDC.put("correlationId", event.idempotencyKey);
            }
            log.info("Received transaction for offline feature extraction: {}", event.idempotencyKey);

            // Record transaction in the Redis feature store
            featureStoreService.recordTransaction(event.accountId, event.amount, event.idempotencyKey);

        } catch (Exception e) {
            log.error("Failed to process transaction event for offline features: {}", e.getMessage(), e);
        } finally {
            MDC.remove("correlationId");
        }
    }
}

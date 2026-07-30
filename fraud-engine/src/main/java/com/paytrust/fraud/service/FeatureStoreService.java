package com.paytrust.fraud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Set;

@Service
public class FeatureStoreService {

    private static final Logger log = LoggerFactory.getLogger(FeatureStoreService.class);
    private final StringRedisTemplate redisTemplate;
    private static final String HISTORY_KEY_PREFIX = "fraud:tx_history:";

    public FeatureStoreService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Record a transaction in Redis.
     * We use a ZSET: Key = fraud:tx_history:{accountId}, Score = timestamp (epoch seconds), Value = amount:idempotencyKey
     */
    public void recordTransaction(String accountId, BigDecimal amount, String idempotencyKey) {
        String key = HISTORY_KEY_PREFIX + accountId;
        long now = Instant.now().getEpochSecond();
        String value = amount.toPlainString() + ":" + idempotencyKey;

        redisTemplate.opsForZSet().add(key, value, now);
        log.info("Recorded tx in Redis for account: {}, amount: {}, key: {}", accountId, amount, key);

        // Auto-cleanup items older than 24 hours (86400 seconds) to prevent unbounded memory growth
        long cutoff = now - 86400;
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
    }

    /**
     * Get transaction count (velocity) in the last duration (in seconds).
     */
    public long getTransactionVelocity(String accountId, long durationInSeconds) {
        String key = HISTORY_KEY_PREFIX + accountId;
        long now = Instant.now().getEpochSecond();
        long start = now - durationInSeconds;

        Long count = redisTemplate.opsForZSet().count(key, start, now);
        return count != null ? count : 0;
    }

    /**
     * Get the average transaction amount for the account in the last 24 hours.
     */
    public BigDecimal getUserAverageAmount(String accountId) {
        String key = HISTORY_KEY_PREFIX + accountId;
        long now = Instant.now().getEpochSecond();
        long start = now - 86400; // Last 24 hours

        Set<String> range = redisTemplate.opsForZSet().rangeByScore(key, start, now);
        if (range == null || range.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;

        for (String item : range) {
            try {
                // Item is format "amount:idempotencyKey"
                String[] parts = item.split(":");
                if (parts.length > 0) {
                    BigDecimal amount = new BigDecimal(parts[0]);
                    sum = sum.add(amount);
                    count++;
                }
            } catch (Exception e) {
                log.warn("Failed to parse transaction history item: {}", item, e);
            }
        }

        if (count == 0) {
            return BigDecimal.ZERO;
        }

        return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}

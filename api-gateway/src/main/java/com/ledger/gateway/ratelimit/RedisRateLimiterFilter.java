package com.ledger.gateway.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

@Component
public class RedisRateLimiterFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    
    @Value("${rate-limiter.replenish-rate}")
    private int replenishRate;
    
    @Value("${rate-limiter.burst-capacity}")
    private int burstCapacity;

    public RedisRateLimiterFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Use X-User-Id if authenticated, else IP address
        String keyId = request.getHeader("X-User-Id");
        if (keyId == null || keyId.isEmpty()) {
            keyId = request.getRemoteAddr();
        }
        
        String bucketKey = "rate_limit:" + keyId;

        // Basic Token Bucket Implementation in Redis without Lua for simplicity in Phase 1
        // (A production-grade would use a Lua script for atomicity, but this demonstrates the pattern)
        long currentTimestamp = Instant.now().getEpochSecond();
        String countKey = bucketKey + ":" + currentTimestamp;
        
        Long requestCount = redisTemplate.opsForValue().increment(countKey, 1);
        
        if (requestCount != null && requestCount == 1) {
            // Expire the key after 2 seconds to avoid polluting Redis
            redisTemplate.expire(countKey, java.time.Duration.ofSeconds(2));
        }

        if (requestCount != null && requestCount > replenishRate) {
            response.setStatus(429); // Too Many Requests
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("Retry-After", "1");
            response.getWriter().write("Too Many Requests");
            return;
        }

        response.setHeader("X-RateLimit-Remaining", String.valueOf(replenishRate - requestCount));
        filterChain.doFilter(request, response);
    }
}

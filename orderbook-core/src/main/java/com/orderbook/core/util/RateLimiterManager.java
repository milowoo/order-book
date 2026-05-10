package com.orderbook.core.util;

import com.orderbook.core.config.ApolloConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-operation-type rate limiters.
 * Limits are configurable via ApolloConfig.
 */
@Slf4j
@Service
public class RateLimiterManager {

    private final ApolloConfig apolloConfig;
    private final Map<String, TokenBucketRateLimiter> limiters = new ConcurrentHashMap<>();

    public RateLimiterManager(ApolloConfig apolloConfig) {
        this.apolloConfig = apolloConfig;
    }

    @PostConstruct
    public void init() {
        refreshLimiters();
        log.info("RateLimiterManager initialized");
    }

    /** Refresh all limiters from config. Call on config change. */
    public void refreshLimiters() {
        createLimiter("place", 10, Math.max(0.1, apolloConfig.getPlaceRateLimit()));
        createLimiter("cancel", 20, Math.max(0.1, apolloConfig.getCancelRateLimit()));
        log.debug("Rate limiters refreshed: place={}/s, cancel={}/s",
                apolloConfig.getPlaceRateLimit(), apolloConfig.getCancelRateLimit());
    }

    public void acquirePlace() {
        limiters.computeIfAbsent("place", k -> new TokenBucketRateLimiter(10, 10)).acquire();
    }

    public void acquireCancel() {
        limiters.computeIfAbsent("cancel", k -> new TokenBucketRateLimiter(20, 20)).acquire();
    }

    private void createLimiter(String name, long capacity, double refillPerSecond) {
        limiters.put(name, new TokenBucketRateLimiter(capacity, refillPerSecond));
    }
}

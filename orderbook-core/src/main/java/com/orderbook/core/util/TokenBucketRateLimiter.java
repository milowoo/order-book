package com.orderbook.core.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Token bucket rate limiter using Semaphore + scheduled refill.
 * Replaces the old spin-wait loop with proper blocking via Semaphore.acquire().
 * Thread-safe.
 */
@Slf4j
public class TokenBucketRateLimiter {

    private final long capacity;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;

    public TokenBucketRateLimiter(long capacity, double refillPerSecond) {
        this.capacity = capacity;
        this.semaphore = new Semaphore((int) capacity);
        this.scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limiter-" + hashCode());
            t.setDaemon(true);
            return t;
        });

        // Refill tokens at the configured rate
        long intervalMicros = (long) (1_000_000 / refillPerSecond);
        this.scheduler.scheduleAtFixedRate(() -> {
            semaphore.release(1);
        }, intervalMicros, intervalMicros, TimeUnit.MICROSECONDS);
    }

    /** Acquire one token, blocking if necessary. */
    public void acquire() {
        acquire(1);
    }

    /** Acquire N tokens, blocking if necessary. */
    public void acquire(int permits) {
        if (permits <= 0) return;
        try {
            semaphore.acquire(permits);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Try to acquire without blocking. Returns true if token acquired. */
    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    /** Shutdown the refill scheduler. */
    public void destroy() {
        scheduler.shutdown();
    }
}

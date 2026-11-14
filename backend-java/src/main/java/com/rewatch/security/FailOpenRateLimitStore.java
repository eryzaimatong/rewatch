package com.rewatch.security;

import java.time.Duration;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The shared store is an optimisation of correctness, not a dependency of
 * availability: when it errors, rate limiting degrades to per-instance memory
 * instead of failing closed (which would lock every user out of login). After
 * a failure the shared store is skipped for a cool-down so a Redis outage does
 * not add a timeout to every request.
 */
public class FailOpenRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(FailOpenRateLimitStore.class);
    private static final long COOL_DOWN_MS = 10_000;

    private final RateLimitStore primary;
    private final RateLimitStore fallback;
    private volatile long skipPrimaryUntil = 0;

    public FailOpenRateLimitStore(RateLimitStore primary, RateLimitStore fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    private <T> T call(Function<RateLimitStore, T> op) {
        if (System.currentTimeMillis() >= skipPrimaryUntil) {
            try {
                return op.apply(primary);
            } catch (RuntimeException e) {
                skipPrimaryUntil = System.currentTimeMillis() + COOL_DOWN_MS;
                log.warn("Shared rate-limit store unavailable; using per-instance memory for {}s: {}",
                        COOL_DOWN_MS / 1000, e.toString());
            }
        }
        return op.apply(fallback);
    }

    @Override
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        return call(s -> s.tryAcquire(key, maxAttempts, window));
    }

    @Override
    public boolean isBlocked(String key, int maxAttempts, Duration window) {
        return call(s -> s.isBlocked(key, maxAttempts, window));
    }

    @Override
    public void record(String key, Duration retention) {
        call(s -> {
            s.record(key, retention);
            return null;
        });
    }

    @Override
    public long secondsUntilRetry(String key, Duration window) {
        return call(s -> s.secondsUntilRetry(key, window));
    }
}

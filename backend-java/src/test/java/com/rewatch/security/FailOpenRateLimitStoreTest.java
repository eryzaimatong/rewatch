package com.rewatch.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * If Redis is down the site must stay up: a limiter that fails CLOSED would
 * lock every user out of login. It degrades to per-instance memory instead.
 */
class FailOpenRateLimitStoreTest {

    /** A Redis that is unreachable. */
    private static final RateLimitStore DOWN = new RateLimitStore() {
        public boolean tryAcquire(String k, int m, Duration w) { throw new IllegalStateException("redis down"); }
        public boolean isBlocked(String k, int m, Duration w) { throw new IllegalStateException("redis down"); }
        public void record(String k, Duration r) { throw new IllegalStateException("redis down"); }
        public long secondsUntilRetry(String k, Duration w) { throw new IllegalStateException("redis down"); }
    };

    @Test
    void usesTheFallbackInsteadOfFailingWhenTheSharedStoreIsDown() {
        RateLimitStore store = new FailOpenRateLimitStore(DOWN, new InMemoryRateLimitStore());
        Duration window = Duration.ofSeconds(30);
        assertTrue(store.tryAcquire("k", 2, window));
        assertTrue(store.tryAcquire("k", 2, window));
        // Still rate limited (per instance) rather than unlimited or erroring.
        assertFalse(store.tryAcquire("k", 2, window));
        assertTrue(store.secondsUntilRetry("k", window) >= 0);
        store.record("login:x", Duration.ofHours(1));
        assertFalse(store.isBlocked("login:x", 2, window));
    }

    @Test
    void usesTheSharedStoreWhileItIsHealthy() {
        InMemoryRateLimitStore shared = new InMemoryRateLimitStore();
        InMemoryRateLimitStore local = new InMemoryRateLimitStore();
        RateLimitStore store = new FailOpenRateLimitStore(shared, local);
        assertTrue(store.tryAcquire("k", 1, Duration.ofSeconds(30)));
        // The attempt was recorded in the shared store, not the local fallback.
        assertFalse(shared.tryAcquire("k", 1, Duration.ofSeconds(30)));
        assertTrue(local.tryAcquire("k", 1, Duration.ofSeconds(30)));
    }

    @Test
    void afterAFailureItStopsCallingTheBrokenStoreForACoolDown() {
        // Otherwise a Redis outage would add a connection timeout to every request.
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        RateLimitStore counting = new RateLimitStore() {
            public boolean tryAcquire(String k, int m, Duration w) { calls.incrementAndGet(); throw new IllegalStateException("down"); }
            public boolean isBlocked(String k, int m, Duration w) { calls.incrementAndGet(); throw new IllegalStateException("down"); }
            public void record(String k, Duration r) { calls.incrementAndGet(); throw new IllegalStateException("down"); }
            public long secondsUntilRetry(String k, Duration w) { calls.incrementAndGet(); throw new IllegalStateException("down"); }
        };
        RateLimitStore store = new FailOpenRateLimitStore(counting, new InMemoryRateLimitStore());
        for (int i = 0; i < 20; i++) {
            store.tryAcquire("k" + i, 5, Duration.ofSeconds(30));
        }
        org.junit.jupiter.api.Assertions.assertEquals(1, calls.get());
    }
}

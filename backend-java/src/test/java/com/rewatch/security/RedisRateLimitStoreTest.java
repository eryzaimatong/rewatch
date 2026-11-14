package com.rewatch.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import com.rewatch.testsupport.RedisTestSupport;

/**
 * Against a real Redis. The point of moving rate limits out of memory is that
 * TWO app instances enforce ONE limit, so most tests use two separate stores
 * (each with its own connection) sharing the same Redis.
 */
@EnabledIf("com.rewatch.testsupport.RedisTestSupport#available")
class RedisRateLimitStoreTest {

    private static String key() {
        return "test:" + UUID.randomUUID();
    }

    private static RedisRateLimitStore instance() {
        return new RedisRateLimitStore(RedisTestSupport.newTemplate());
    }

    @Test
    void allowsUpToTheLimitThenBlocks() {
        RedisRateLimitStore store = instance();
        String k = key();
        Duration window = Duration.ofSeconds(30);
        assertTrue(store.tryAcquire(k, 3, window));
        assertTrue(store.tryAcquire(k, 3, window));
        assertTrue(store.tryAcquire(k, 3, window));
        assertFalse(store.tryAcquire(k, 3, window));
    }

    @Test
    void twoAppInstancesShareOneLimit() {
        RedisRateLimitStore instanceA = instance();
        RedisRateLimitStore instanceB = instance();
        String k = key();
        Duration window = Duration.ofSeconds(30);

        assertTrue(instanceA.tryAcquire(k, 3, window));
        assertTrue(instanceB.tryAcquire(k, 3, window));
        assertTrue(instanceA.tryAcquire(k, 3, window));
        // Three attempts landed across two instances; the fourth is refused on EITHER one.
        assertFalse(instanceB.tryAcquire(k, 3, window));
        assertFalse(instanceA.tryAcquire(k, 3, window));
    }

    @Test
    void theLimitIsAtomicUnderConcurrency() throws Exception {
        String k = key();
        Duration window = Duration.ofSeconds(30);
        int threads = 16;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var allowed = new java.util.concurrent.atomic.AtomicInteger();
        var start = new java.util.concurrent.CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            RedisRateLimitStore own = instance();
            pool.submit(() -> {
                start.await();
                for (int n = 0; n < 5; n++) {
                    if (own.tryAcquire(k, 10, window)) allowed.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS));
        // 80 attempts raced for a budget of 10: exactly 10 get through, never 11.
        assertEquals(10, allowed.get());
    }

    @Test
    void budgetComesBackWhenTheWindowPasses() throws Exception {
        RedisRateLimitStore store = instance();
        String k = key();
        Duration window = Duration.ofMillis(400);
        assertTrue(store.tryAcquire(k, 1, window));
        assertFalse(store.tryAcquire(k, 1, window));
        Thread.sleep(550);
        assertTrue(store.tryAcquire(k, 1, window));
    }

    @Test
    void isBlockedReadsWithoutConsumingBudget() {
        RedisRateLimitStore store = instance();
        String k = key();
        Duration window = Duration.ofSeconds(30);
        assertFalse(store.isBlocked(k, 2, window));
        assertFalse(store.isBlocked(k, 2, window));
        store.record(k, Duration.ofHours(1));
        store.record(k, Duration.ofHours(1));
        assertTrue(store.isBlocked(k, 2, window));
    }

    @Test
    void reportsWhenBudgetReturns() {
        RedisRateLimitStore store = instance();
        String k = key();
        Duration window = Duration.ofSeconds(30);
        assertEquals(0, store.secondsUntilRetry(k, window));
        store.tryAcquire(k, 1, window);
        long secs = store.secondsUntilRetry(k, window);
        assertTrue(secs > 0 && secs <= 30, "got " + secs);
    }

    @Test
    void theServiceEnforcesTheSameLimitAcrossInstances() {
        RateLimiterService a = new RateLimiterService(instance());
        RateLimiterService b = new RateLimiterService(instance());
        String k = key();
        Duration window = Duration.ofSeconds(30);
        assertTrue(a.allow(k, 2, window));
        assertTrue(b.allow(k, 2, window));
        assertFalse(a.allow(k, 2, window));
        assertFalse(b.allow(k, 2, window));
    }
}

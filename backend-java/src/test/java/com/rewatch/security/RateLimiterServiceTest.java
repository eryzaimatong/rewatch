package com.rewatch.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class RateLimiterServiceTest {

    @Test
    void allowsUpToTheLimitThenBlocks() {
        RateLimiterService limiter = new RateLimiterService();

        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.allow("key", 3, Duration.ofMinutes(1)), "attempt " + i + " should be allowed");
        }
        assertFalse(limiter.allow("key", 3, Duration.ofMinutes(1)), "the 4th attempt must be blocked");
    }

    @Test
    void differentKeysAreIndependent() {
        RateLimiterService limiter = new RateLimiterService();

        for (int i = 0; i < 3; i++) {
            limiter.allow("a", 3, Duration.ofMinutes(1));
        }
        assertFalse(limiter.allow("a", 3, Duration.ofMinutes(1)));
        assertTrue(limiter.allow("b", 3, Duration.ofMinutes(1)), "a different key must have its own budget");
    }

    @Test
    void oldAttemptsOutsideTheWindowDoNotCountAgainstTheLimit() throws InterruptedException {
        RateLimiterService limiter = new RateLimiterService();
        Duration tinyWindow = Duration.ofMillis(50);

        assertTrue(limiter.allow("key", 1, tinyWindow));
        assertFalse(limiter.allow("key", 1, tinyWindow), "still inside the window");

        Thread.sleep(80);

        assertTrue(limiter.allow("key", 1, tinyWindow), "the earlier attempt has aged out of the window");
    }
}

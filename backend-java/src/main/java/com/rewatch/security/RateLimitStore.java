package com.rewatch.security;

import java.time.Duration;

/**
 * Where sliding-window rate-limit attempts live. In memory for a single
 * instance; in Redis when REDIS_URL is set, so several instances enforce one
 * shared limit instead of one limit each.
 */
public interface RateLimitStore {

    /** Atomic check-and-consume: true if allowed (and now counted), false if the key is at its limit within the window. */
    boolean tryAcquire(String key, int maxAttempts, Duration window);

    /** Read-only: true if the key is at or over its limit within the window. */
    boolean isBlocked(String key, int maxAttempts, Duration window);

    /** Records one attempt unconditionally. {@code retention} bounds how long the record may live. */
    void record(String key, Duration retention);

    /** Seconds until the oldest attempt in the window ages out; 0 if there is none. */
    long secondsUntilRetry(String key, Duration window);
}

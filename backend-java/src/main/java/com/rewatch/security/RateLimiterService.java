package com.rewatch.security;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Sliding-window rate limiter for the auth endpoints — this app's
 * only unauthenticated, abuse-prone surface (login invites credential
 * stuffing, register/forgot-password can be used to spam an inbox or a real
 * Gmail sending account). A sliding window of recent-attempt timestamps per
 * key, kept in a {@link RateLimitStore}: in memory by default, or in Redis when
 * REDIS_URL is set so that several backend instances enforce ONE shared limit.
 */
@Service
public class RateLimiterService {

    /** Upper bound on how long a failure-counted attempt (login guard) is kept; every window used is shorter. */
    private static final Duration FAILURE_RETENTION = Duration.ofHours(24);

    private final RateLimitStore store;

    /** Single-instance default: all state in this process's memory. */
    public RateLimiterService() {
        this(new InMemoryRateLimitStore());
    }

    /** Spring wiring: the store is Redis-backed when REDIS_URL is set (see RateLimitConfig). */
    @Autowired
    public RateLimiterService(RateLimitStore store) {
        this.store = store;
    }

    /**
     * @return true if this call is allowed (and is now counted against the
     *         limit); false if {@code key} has already hit {@code maxAttempts}
     *         within {@code window}.
     */
    public boolean allow(String key, int maxAttempts, Duration window) {
        return store.tryAcquire(key, maxAttempts, window);
    }

    /**
     * Read-only: true if {@code key} is already at or over {@code maxAttempts}
     * within {@code window}, without recording a new attempt. For a
     * failure-counted limit (e.g. login's per-account guard below) — the gate
     * check and the "this attempt itself failed" recording happen at
     * different points in the same request, unlike allow()'s single
     * check-and-consume.
     */
    public boolean isBlocked(String key, int maxAttempts, Duration window) {
        return store.isBlocked(key, maxAttempts, window);
    }

    /** Records one attempt against {@code key} unconditionally — pairs with isBlocked() for failure-only counting. */
    public void recordAttempt(String key) {
        store.record(key, FAILURE_RETENTION);
    }

    /**
     * Seconds until {@code key}'s oldest attempt in the current window ages
     * out — i.e. how long until this key has budget again, assuming no
     * further attempts land in the meantime. Only meaningful right after
     * allow()/isBlocked() found the key already at its limit.
     */
    public long secondsUntilRetry(String key, Duration window) {
        return store.secondsUntilRetry(key, window);
    }

    /**
     * A 429 response every rate-limited endpoint in this app builds the same
     * way — a Retry-After header (RFC 9110) plus copy that tells the user
     * what happened and roughly when to try again, not just "too many
     * attempts" with no timeframe. Centralized here rather than duplicated
     * per controller, since every call site already has the key/window this
     * needs anyway.
     */
    public ResponseEntity<?> tooManyRequests(String key, Duration window, String whatHappened) {
        long secs = secondsUntilRetry(key, window);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(secs))
                .body(Map.of(
                        "status", "error",
                        "message", retryMessage(whatHappened, secs),
                        "retryAfterSeconds", secs));
    }

    /**
     * Same message/timeframe as tooManyRequests() above, as a throwable
     * instead — for routes that throw a rate-limit rejection rather than
     * building their own ResponseEntity (their method return type is the
     * actual payload type, e.g. List<MovieDTO>, not ResponseEntity), where a
     * plain ResponseStatusException would drop the Retry-After header
     * entirely (see RateLimitExceededException's own comment).
     */
    public RateLimitExceededException tooManyRequestsException(String key, Duration window, String whatHappened) {
        long secs = secondsUntilRetry(key, window);
        return new RateLimitExceededException(retryMessage(whatHappened, secs), secs);
    }

    private String retryMessage(String whatHappened, long secs) {
        return whatHappened + " Try again " + friendlyRetryAfter(secs) + ".";
    }

    private String friendlyRetryAfter(long seconds) {
        if (seconds <= 5) {
            return "in a few seconds";
        }
        if (seconds < 60) {
            return "in " + seconds + " seconds";
        }
        long minutes = (seconds + 59) / 60;
        if (minutes < 60) {
            return "in about " + minutes + " minute" + (minutes == 1 ? "" : "s");
        }
        long hours = (minutes + 59) / 60;
        return "in about " + hours + " hour" + (hours == 1 ? "" : "s");
    }
}

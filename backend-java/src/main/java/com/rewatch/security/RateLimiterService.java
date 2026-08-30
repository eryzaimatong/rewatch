package com.rewatch.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * In-memory sliding-window rate limiter for the auth endpoints — this app's
 * only unauthenticated, abuse-prone surface (login invites credential
 * stuffing, register/forgot-password can be used to spam an inbox or a real
 * Gmail sending account). No new dependency (Bucket4j etc.): a per-key deque
 * of recent-attempt timestamps, pruned to the window on each check, is
 * enough at this app's current single-instance scale — same "revisit if this
 * ever needs to survive a restart or run across multiple instances" caveat
 * as everywhere else in this codebase that trades a small in-memory
 * structure for not adding Redis this early.
 */
@Service
public class RateLimiterService {

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    /**
     * @return true if this call is allowed (and is now counted against the
     *         limit); false if {@code key} has already hit {@code maxAttempts}
     *         within {@code window}.
     */
    public boolean allow(String key, int maxAttempts, Duration window) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);

        Deque<Instant> log = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (log) {
            while (!log.isEmpty() && log.peekFirst().isBefore(cutoff)) {
                log.pollFirst();
            }
            if (log.size() >= maxAttempts) {
                return false;
            }
            log.addLast(now);
            return true;
        }
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
        Deque<Instant> log = attempts.get(key);
        if (log == null) {
            return false;
        }
        Instant cutoff = Instant.now().minus(window);
        synchronized (log) {
            while (!log.isEmpty() && log.peekFirst().isBefore(cutoff)) {
                log.pollFirst();
            }
            return log.size() >= maxAttempts;
        }
    }

    /** Records one attempt against {@code key} unconditionally — pairs with isBlocked() for failure-only counting. */
    public void recordAttempt(String key) {
        Deque<Instant> log = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (log) {
            log.addLast(Instant.now());
        }
    }

    /**
     * Seconds until {@code key}'s oldest attempt in the current window ages
     * out — i.e. how long until this key has budget again, assuming no
     * further attempts land in the meantime. Only meaningful right after
     * allow()/isBlocked() found the key already at its limit.
     */
    public long secondsUntilRetry(String key, Duration window) {
        Deque<Instant> log = attempts.get(key);
        if (log == null) {
            return 0;
        }
        synchronized (log) {
            Instant oldest = log.peekFirst();
            if (oldest == null) {
                return 0;
            }
            long secs = Duration.between(Instant.now(), oldest.plus(window)).getSeconds();
            return Math.max(0, secs);
        }
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

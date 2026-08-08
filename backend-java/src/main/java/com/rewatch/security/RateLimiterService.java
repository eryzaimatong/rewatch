package com.rewatch.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
}

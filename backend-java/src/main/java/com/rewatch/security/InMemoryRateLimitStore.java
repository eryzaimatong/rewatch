package com.rewatch.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-key deque of recent attempt timestamps, pruned to the window on each check. Single-instance only. */
public class InMemoryRateLimitStore implements RateLimitStore {

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
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

    @Override
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

    @Override
    public void record(String key, Duration retention) {
        Deque<Instant> log = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (log) {
            log.addLast(Instant.now());
        }
    }

    @Override
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
            return Math.max(0, Duration.between(Instant.now(), oldest.plus(window)).getSeconds());
        }
    }
}

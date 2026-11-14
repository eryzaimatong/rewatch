package com.rewatch.security;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Sliding window in a Redis sorted set (score = attempt time in ms). The
 * check-and-consume runs as one Lua script so concurrent requests, on this
 * instance or any other, can never both take the last slot.
 */
public class RedisRateLimitStore implements RateLimitStore {

    private static final String PREFIX = "rewatch:rl:";

    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local max = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            if redis.call('ZCARD', key) >= max then
              return 0
            end
            redis.call('ZADD', key, now, ARGV[4])
            redis.call('PEXPIRE', key, window)
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private static String k(String key) {
        return PREFIX + key;
    }

    private static String member(long now) {
        return now + ":" + UUID.randomUUID();
    }

    @Override
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        long now = System.currentTimeMillis();
        Long allowed = redis.execute(ACQUIRE, List.of(k(key)),
                String.valueOf(now), String.valueOf(window.toMillis()), String.valueOf(maxAttempts), member(now));
        return allowed != null && allowed == 1L;
    }

    @Override
    public boolean isBlocked(String key, int maxAttempts, Duration window) {
        long now = System.currentTimeMillis();
        ZSetOperations<String, String> z = redis.opsForZSet();
        z.removeRangeByScore(k(key), 0, now - window.toMillis());
        Long size = z.zCard(k(key));
        return size != null && size >= maxAttempts;
    }

    @Override
    public void record(String key, Duration retention) {
        long now = System.currentTimeMillis();
        redis.opsForZSet().add(k(key), member(now), now);
        redis.expire(k(key), retention);
    }

    @Override
    public long secondsUntilRetry(String key, Duration window) {
        Set<ZSetOperations.TypedTuple<String>> oldest = redis.opsForZSet().rangeWithScores(k(key), 0, 0);
        if (oldest == null || oldest.isEmpty()) {
            return 0;
        }
        Double score = oldest.iterator().next().getScore();
        if (score == null) {
            return 0;
        }
        long remainingMs = (long) (score + window.toMillis()) - System.currentTimeMillis();
        return Math.max(0, (remainingMs + 999) / 1000);
    }
}

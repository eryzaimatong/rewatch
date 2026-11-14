package com.rewatch.config;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * {@code SET key token NX PX ttl} to take the lock, and a compare-and-delete
 * script to release it, so an instance whose lock expired mid-run can never
 * free a lock that someone else has since taken. If Redis errors the job runs
 * anyway: a duplicate sweep is wasteful, a job that silently stops is worse.
 */
public class RedisJobLock implements JobLock {

    private static final Logger log = LoggerFactory.getLogger(RedisJobLock.class);
    private static final String PREFIX = "rewatch:lock:";

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisJobLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @return the holder token if the lock was taken, null if someone else holds it. */
    String tryAcquire(String name, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(PREFIX + name, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    void release(String name, String token) {
        redis.execute(RELEASE, List.of(PREFIX + name), token);
    }

    @Override
    public boolean runIfLeader(String name, Duration ttl, Runnable job) {
        String token;
        try {
            token = tryAcquire(name, ttl);
        } catch (RuntimeException e) {
            log.warn("Job lock '{}' unavailable; running without it: {}", name, e.toString());
            job.run();
            return true;
        }
        if (token == null) {
            return false;
        }
        try {
            job.run();
        } finally {
            try {
                release(name, token);
            } catch (RuntimeException e) {
                log.warn("Could not release job lock '{}' (it will expire on its own): {}", name, e.toString());
            }
        }
        return true;
    }
}

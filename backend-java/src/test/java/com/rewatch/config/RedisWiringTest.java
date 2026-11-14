package com.rewatch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.rewatch.security.FailOpenRateLimitStore;
import com.rewatch.security.InMemoryRateLimitStore;
import com.rewatch.security.RateLimitConfig;
import com.rewatch.security.RateLimitStore;
import com.rewatch.security.RateLimiterService;
import com.rewatch.service.TmdbClient.TmdbMovie;

/** Proves REDIS_URL is the single switch between in-memory and shared state. */
class RedisWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
            .withUserConfiguration(RateLimitConfig.class, TmdbCacheManager.class, RateLimiterService.class);

    private static String redisUrl() {
        String url = System.getenv("REDIS_URL");
        return url == null || url.isBlank() ? "redis://127.0.0.1:6379" : url;
    }

    @Test
    void withoutRedisUrlEverythingStaysInMemory() {
        runner.run(ctx -> {
            assertInstanceOf(InMemoryRateLimitStore.class, ctx.getBean(RateLimitStore.class));
            CacheManager cm = ctx.getBean(CacheManager.class);
            // Local TTL cache, not the Redis-backed wrapper.
            assertTrue(!(cm.getCache("tmdbPopular") instanceof FailOpenCache));
        });
    }

    @Test
    @EnabledIf("com.rewatch.testsupport.RedisTestSupport#available")
    void withRedisUrlBothMoveToRedisAndSurviveItsOutage() {
        runner.withPropertyValues("spring.data.redis.url=" + redisUrl()).run(ctx -> {
            assertInstanceOf(FailOpenRateLimitStore.class, ctx.getBean(RateLimitStore.class));
            CacheManager cm = ctx.getBean(CacheManager.class);
            assertInstanceOf(FailOpenCache.class, cm.getCache("tmdbPopular"));

            // The cache entry really lands in Redis, readable by any other instance.
            String key = "wiring-" + UUID.randomUUID();
            List<TmdbMovie> movies = List.of(new TmdbMovie(1, "T", null, null, null, null, List.of(), "en", 1.0, 2, 3.0));
            cm.getCache("tmdbPopular").put(key, movies);
            StringRedisTemplate redis = ctx.getBean(StringRedisTemplate.class);
            assertNotNull(redis.opsForValue().get("rewatch:cache:tmdbPopular:" + key));

            // And the limiter counts in Redis: a second service on the same Redis sees the budget spent.
            RateLimiterService a = ctx.getBean(RateLimiterService.class);
            RateLimiterService b = new RateLimiterService(new com.rewatch.security.RedisRateLimitStore(redis));
            String rl = "wiring:" + UUID.randomUUID();
            assertTrue(a.allow(rl, 1, Duration.ofSeconds(30)));
            assertEquals(false, b.allow(rl, 1, Duration.ofSeconds(30)));
        });
    }

    @Test
    void anUnreachableRedisNeverBreaksStartupOrRequests() {
        runner.withPropertyValues("spring.data.redis.url=redis://127.0.0.1:1", "spring.data.redis.timeout=200ms").run(ctx -> {
            assertNotNull(ctx.getBean(RateLimitStore.class));
            RateLimiterService limiter = ctx.getBean(RateLimiterService.class);
            assertTrue(limiter.allow("k", 1, Duration.ofSeconds(30)));   // served from memory
            assertEquals(false, limiter.allow("k", 1, Duration.ofSeconds(30)));
            CacheManager cm = ctx.getBean(CacheManager.class);
            cm.getCache("tmdbPopular").put("k", List.of());               // must not throw
        });
    }
}

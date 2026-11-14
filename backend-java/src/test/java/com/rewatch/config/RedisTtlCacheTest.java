package com.rewatch.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.cache.Cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rewatch.service.TmdbClient.TmdbMovie;
import com.rewatch.testsupport.RedisTestSupport;

class RedisTtlCacheTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static List<TmdbMovie> movies() {
        return List.of(
                new TmdbMovie(603, "The Matrix", "A hacker learns the truth.", "/p.jpg", "/b.jpg", "1999-03-30",
                        List.of(28, 878), "en", 8.2, 25000, 90.5),
                new TmdbMovie(null, "Sparse", null, null, null, null, List.of(), null, null, null, null));
    }

    private static Cache redisCache(String name, Duration ttl) {
        return new RedisTtlCache(name, RedisTestSupport.newTemplate(), MAPPER,
                MAPPER.getTypeFactory().constructCollectionType(List.class, TmdbMovie.class), ttl);
    }

    @Test
    @EnabledIf("com.rewatch.testsupport.RedisTestSupport#available")
    void roundTripsTheRealCachedTypeIncludingNullFields() {
        Cache cache = redisCache("tmdbPopular-" + UUID.randomUUID(), Duration.ofMinutes(5));
        cache.put("k", movies());
        Object back = cache.get("k").get();
        assertEquals(movies(), back);
    }

    @Test
    @EnabledIf("com.rewatch.testsupport.RedisTestSupport#available")
    void twoAppInstancesSeeTheSameCache() {
        String name = "tmdbTrending-" + UUID.randomUUID();
        Cache a = redisCache(name, Duration.ofMinutes(5));
        Cache b = redisCache(name, Duration.ofMinutes(5));
        a.put("k", movies());
        assertNotNull(b.get("k"));
        assertEquals(movies(), b.get("k").get());
    }

    @Test
    @EnabledIf("com.rewatch.testsupport.RedisTestSupport#available")
    void entriesExpireAfterTheTtl() throws Exception {
        Cache cache = redisCache("tmdbTopRated-" + UUID.randomUUID(), Duration.ofMillis(300));
        cache.put("k", movies());
        assertNotNull(cache.get("k"));
        Thread.sleep(450);
        assertNull(cache.get("k"));
    }

    @Test
    @EnabledIf("com.rewatch.testsupport.RedisTestSupport#available")
    void evictAndClearRemoveEntries() {
        Cache cache = redisCache("tmdbDiscover-" + UUID.randomUUID(), Duration.ofMinutes(5));
        cache.put("a", movies());
        cache.put("b", movies());
        cache.evict("a");
        assertNull(cache.get("a"));
        assertNotNull(cache.get("b"));
        cache.clear();
        assertNull(cache.get("b"));
    }

    @Test
    @EnabledIf("com.rewatch.testsupport.RedisTestSupport#available")
    void aCorruptEntryIsAMissNotAnError() {
        var template = RedisTestSupport.newTemplate();
        String name = "tmdbPopular-" + UUID.randomUUID();
        Cache cache = new RedisTtlCache(name, template, MAPPER,
                MAPPER.getTypeFactory().constructCollectionType(List.class, TmdbMovie.class), Duration.ofMinutes(5));
        template.opsForValue().set("rewatch:cache:" + name + ":k", "{not json");
        assertNull(cache.get("k"));
    }

    @Test
    void theCacheStaysUpWhenRedisIsUnreachable() {
        // Nothing listens on this port: every Redis call fails.
        var dead = new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
                new org.springframework.data.redis.connection.RedisStandaloneConfiguration("127.0.0.1", 1),
                org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofMillis(200)).build());
        dead.afterPropertiesSet();
        var template = new org.springframework.data.redis.core.StringRedisTemplate(dead);
        template.afterPropertiesSet();

        Cache redis = new RedisTtlCache("tmdbPopular", template, MAPPER,
                MAPPER.getTypeFactory().constructCollectionType(List.class, TmdbMovie.class), Duration.ofMinutes(5));
        Cache cache = new FailOpenCache(redis, new TmdbCacheManager(30).getCache("tmdbPopular"));

        cache.put("k", movies());                       // must not throw
        assertEquals(movies(), cache.get("k").get());   // served from the local fallback
    }
}

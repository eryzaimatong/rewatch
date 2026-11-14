package com.rewatch.config;

import java.time.Duration;
import java.util.concurrent.Callable;

import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A cache whose entries live in Redis as JSON with a TTL, so every backend
 * instance shares one TMDB cache (and a cold instance does not re-hit TMDB).
 * Values are (de)serialised against an explicit type per cache; no default
 * typing, so nothing in Redis can pick a class to instantiate.
 *
 * Redis errors propagate on purpose: {@link FailOpenCache} decides what to do
 * about them. An undecodable entry (corrupt, or written by an older build with
 * a different shape) is treated as a miss and removed.
 */
public class RedisTtlCache implements Cache {

    private static final String PREFIX = "rewatch:cache:";

    private final String name;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final JavaType type;
    private final Duration ttl;

    public RedisTtlCache(String name, StringRedisTemplate redis, ObjectMapper mapper, JavaType type, Duration ttl) {
        this.name = name;
        this.redis = redis;
        this.mapper = mapper;
        this.type = type;
        this.ttl = ttl;
    }

    private String k(Object key) {
        return PREFIX + name + ":" + key;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return redis;
    }

    @Override
    public ValueWrapper get(Object key) {
        String json = redis.opsForValue().get(k(key));
        if (json == null) {
            return null;
        }
        try {
            return new SimpleValueWrapper(mapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            redis.delete(k(key));
            return null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper existing = get(key);
        if (existing != null) {
            return (T) existing.get();
        }
        try {
            T value = valueLoader.call();
            put(key, value);
            return value;
        } catch (Exception ex) {
            throw new ValueRetrievalException(key, valueLoader, ex);
        }
    }

    @Override
    public <T> T get(Object key, Class<T> requiredType) {
        ValueWrapper w = get(key);
        return w == null ? null : requiredType.cast(w.get());
    }

    @Override
    public void put(Object key, Object value) {
        if (value == null) {
            return;
        }
        try {
            redis.opsForValue().set(k(key), mapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot cache " + name + ":" + key, e);
        }
    }

    @Override
    public void evict(Object key) {
        redis.delete(k(key));
    }

    @Override
    public void clear() {
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(PREFIX + name + ":*").count(200).build())) {
            while (cursor.hasNext()) {
                redis.delete(cursor.next());
            }
        }
    }
}

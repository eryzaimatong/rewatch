package com.rewatch.config;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.stereotype.Component;

/**
 * Replaces the default {@code ConcurrentMapCacheManager} that {@code @EnableCaching}
 * (WebConfig) falls back to when no CacheManager bean is present — that default
 * never expires anything, so TmdbClient's {@code @Cacheable} methods
 * (popular/trending/topRated/discover*) freeze at whatever TMDB returned on the
 * very first call for the life of the process; only a restart clears them. A
 * hand-rolled TTL wrapper is the same "small in-memory structure, no new
 * dependency" trade-off this codebase already makes for RateLimiterService,
 * rather than pulling in Caffeine for what's just an expiring map.
 */
@Component
public class TmdbCacheManager implements CacheManager {

    private final Map<String, Cache> caches = new ConcurrentHashMap<>();
    private final Duration ttl;

    public TmdbCacheManager(@Value("${tmdb.cache.ttl-minutes:30}") long ttlMinutes) {
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, n -> new TtlCache(n, ttl));
    }

    @Override
    public Collection<String> getCacheNames() {
        return caches.keySet();
    }

    /** One entry per distinct method-argument key, evicted lazily on read once older than {@code ttl}. */
    private static final class TtlCache implements Cache {
        private record Entry(Object value, long storedAtNanos) {}

        private final String name;
        private final Duration ttl;
        private final Map<Object, Entry> store = new ConcurrentHashMap<>();

        TtlCache(String name, Duration ttl) {
            this.name = name;
            this.ttl = ttl;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return store;
        }

        @Override
        public ValueWrapper get(Object key) {
            Entry e = store.get(key);
            if (e == null) {
                return null;
            }
            if (System.nanoTime() - e.storedAtNanos() > ttl.toNanos()) {
                store.remove(key, e);
                return null;
            }
            return new SimpleValueWrapper(e.value());
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
        public <T> T get(Object key, Class<T> type) {
            ValueWrapper w = get(key);
            return w == null ? null : type.cast(w.get());
        }

        @Override
        public void put(Object key, Object value) {
            store.put(key, new Entry(value, System.nanoTime()));
        }

        @Override
        public void evict(Object key) {
            store.remove(key);
        }

        @Override
        public void clear() {
            store.clear();
        }
    }
}

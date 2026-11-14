package com.rewatch.config;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

/**
 * Redis is a speed-up, never a dependency of availability. If the shared cache
 * errors, the call is served from a local in-memory cache instead and the
 * shared one is skipped for a short cool-down, so an outage neither fails
 * TMDB-backed pages nor adds a timeout to every request.
 */
public class FailOpenCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(FailOpenCache.class);
    private static final long COOL_DOWN_MS = 10_000;

    private final Cache primary;
    private final Cache fallback;
    private volatile long skipPrimaryUntil = 0;

    public FailOpenCache(Cache primary, Cache fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    private <T> T guarded(Supplier<T> onPrimary, Supplier<T> onFallback) {
        if (System.currentTimeMillis() >= skipPrimaryUntil) {
            try {
                return onPrimary.get();
            } catch (RuntimeException e) {
                skipPrimaryUntil = System.currentTimeMillis() + COOL_DOWN_MS;
                log.warn("Shared cache '{}' unavailable; using local memory for {}s: {}",
                        primary.getName(), COOL_DOWN_MS / 1000, e.toString());
            }
        }
        return onFallback.get();
    }

    private void guardedVoid(Runnable onPrimary, Runnable onFallback) {
        guarded(() -> {
            onPrimary.run();
            return null;
        }, () -> {
            onFallback.run();
            return null;
        });
    }

    @Override
    public String getName() {
        return primary.getName();
    }

    @Override
    public Object getNativeCache() {
        return primary.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        return guarded(() -> primary.get(key), () -> fallback.get(key));
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
        guardedVoid(() -> primary.put(key, value), () -> fallback.put(key, value));
    }

    @Override
    public void evict(Object key) {
        guardedVoid(() -> primary.evict(key), () -> fallback.evict(key));
        fallback.evict(key);
    }

    @Override
    public void clear() {
        guardedVoid(primary::clear, fallback::clear);
        fallback.clear();
    }
}

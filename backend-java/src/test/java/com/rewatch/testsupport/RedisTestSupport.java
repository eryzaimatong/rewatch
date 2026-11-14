package com.rewatch.testsupport;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.time.Duration;

/**
 * Connects the integration tests to a real Redis (REDIS_URL, default
 * redis://127.0.0.1:6379). They are skipped, not failed, when none is
 * reachable, so a plain `./mvnw test` still works without Redis installed; CI
 * provides one so they always run there.
 */
public final class RedisTestSupport {

    private RedisTestSupport() {}

    private static URI uri() {
        String url = System.getenv("REDIS_URL");
        return URI.create(url == null || url.isBlank() ? "redis://127.0.0.1:6379" : url);
    }

    /** A fresh, started template per call, i.e. a separate "app instance" talking to the same Redis. */
    public static StringRedisTemplate newTemplate() {
        URI u = uri();
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(u.getHost(), u.getPort() == -1 ? 6379 : u.getPort());
        if (u.getUserInfo() != null && u.getUserInfo().contains(":")) {
            cfg.setPassword(u.getUserInfo().substring(u.getUserInfo().indexOf(':') + 1));
        }
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(500))
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(cfg, client);
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    /** Referenced by @EnabledIf. */
    public static boolean available() {
        try {
            StringRedisTemplate t = newTemplate();
            String pong = t.execute((org.springframework.data.redis.core.RedisCallback<String>) c -> c.ping());
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            return false;
        }
    }
}

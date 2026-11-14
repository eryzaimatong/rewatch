package com.rewatch.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Picks where rate-limit state lives: Redis when REDIS_URL is set, otherwise in memory (unchanged behaviour). */
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimitStore rateLimitStore(ObjectProvider<StringRedisTemplate> redis,
                                         @Value("${spring.data.redis.url:}") String redisUrl) {
        StringRedisTemplate template = redisUrl.isBlank() ? null : redis.getIfAvailable();
        if (template == null) {
            return new InMemoryRateLimitStore();
        }
        return new FailOpenRateLimitStore(new RedisRateLimitStore(template), new InMemoryRateLimitStore());
    }
}

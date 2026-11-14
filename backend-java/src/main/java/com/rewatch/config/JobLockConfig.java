package com.rewatch.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis-backed leader lock when REDIS_URL is set; otherwise every instance leads (single-instance behaviour). */
@Configuration
public class JobLockConfig {

    @Bean
    public JobLock jobLock(ObjectProvider<StringRedisTemplate> redis, @Value("${spring.data.redis.url:}") String redisUrl) {
        StringRedisTemplate template = redisUrl.isBlank() ? null : redis.getIfAvailable();
        return template == null ? JobLock.NONE : new RedisJobLock(template);
    }
}

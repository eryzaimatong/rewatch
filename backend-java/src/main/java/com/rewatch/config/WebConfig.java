package com.rewatch.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Single source of truth for cross-cutting web concerns.
 *
 * Previously CORS was declared per-controller with @CrossOrigin, and the values
 * disagreed: TitleController pinned localhost:5173 while every other controller
 * used origins="*". Configure it once here instead.
 */
@Configuration
@EnableCaching
public class WebConfig implements WebMvcConfigurer {

    @Value("${rewatch.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Value("${tmdb.connect-timeout-ms:3000}")
    private long connectTimeoutMs;

    @Value("${tmdb.read-timeout-ms:5000}")
    private long readTimeoutMs;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * TMDB calls previously used `new RestTemplate()` with no timeouts, so a single
     * slow upstream response would pin a Tomcat worker thread indefinitely.
     */
    @Bean
    public RestTemplate tmdbRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}

package com.rewatch.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
                // Retry-After isn't on the CORS response-header safelist, so
                // without this a cross-origin fetch() genuinely cannot read
                // it via Headers.get() — confirmed live via Playwright: the
                // header is sent correctly (curl sees it), but
                // res.headers.get('retry-after') came back null from the
                // actual frontend origin. The 429 body's own
                // retryAfterSeconds field is what the UI actually reads, so
                // this wasn't blocking anything, but the header should be
                // genuinely usable, not just RFC-compliant on paper.
                .exposedHeaders("Retry-After")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * TMDB calls previously used `new RestTemplate()` with no timeouts, so a single
     * slow upstream response would pin a Tomcat worker thread indefinitely.
     *
     * @Primary since EmailConfig's resendRestTemplate bean means there are now
     * two RestTemplate beans in the context — confirmed live: the deploy that
     * added it crashed boot outright with "expected single matching bean but
     * found 2" the moment TmdbClient's unqualified RestTemplate constructor
     * param needed resolving. This keeps every existing unqualified
     * RestTemplate injection point (TmdbClient and anything else) resolving
     * to the same bean it always did; resendRestTemplate is only ever
     * consumed by name in EmailConfig.httpEmailSender's own parameter.
     */
    @Bean
    @Primary
    public RestTemplate tmdbRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}

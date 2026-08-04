package com.rewatch.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT auth. No sessions, no CSRF token (there's no cookie to forge),
 * CORS delegates to the existing WebConfig registry.
 *
 * A handful of GET routes stay public on purpose — TmdbController's comment on
 * getpopular() explains why: "userId personalizes it; omitting it serves a
 * neutral, quality-weighted view for logged-out browsing." Those controllers now
 * derive the personalizing id from the authenticated principal (if any) rather
 * than trusting a client-supplied query param, so anonymous access to them can't
 * leak another user's personalized feed. Everything that reads or writes a
 * specific user's private data requires a token, enforced here at the route
 * level and again per-endpoint via SecurityUtil.requireSelf.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    // Spring Security's own default here is 403, which conflates "not
                    // logged in" with "logged in as the wrong user" (the latter is
                    // SecurityUtil.requireSelf's 403). Split them: missing/invalid/
                    // expired token is 401, so the frontend knows to send the user to
                    // login rather than just showing a generic error.
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"status\":\"error\",\"message\":\"Login required\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    // Explicit, not left to Spring's default: a real, non-anonymous
                    // ROLE_USER token hitting /api/admin/** (hasRole("ADMIN")) needs to
                    // land here as a genuine 403 ("you're someone, just not allowed"),
                    // not get folded into the 401 authenticationEntryPoint above.
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"status\":\"error\",\"message\":\"Forbidden\"}");
                }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/titles").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/movies/popular", "/api/movies/search",
                        "/api/movies/nlp-search", "/api/movies/search-suggestions",
                        "/api/movies/trending", "/api/movies/top-rated")
                    .permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

package com.rewatch.security;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.rewatch.config.WebConfig;

import com.rewatch.model.User;
import com.rewatch.repository.UserRepository;

/**
 * Exercises the REAL SecurityFilterChain end to end (real SecurityConfig,
 * real JwtAuthFilter, real JwtService — only UserRepository is mocked, to
 * control tokenVersion/role/existence) rather than reading the matcher
 * rules off the source. SecurityConfigProbeController's handlers all just
 * return 200, so "reached the handler" is unambiguous — a 401/403 means
 * security actually blocked the request before dispatch.
 */
// WebConfig implements WebMvcConfigurer, so @WebMvcTest's slice would
// otherwise pull it in automatically — and its tmdbRestTemplate bean needs
// a RestTemplateBuilder this narrow slice doesn't autoconfigure. Excluded
// deliberately: this test is about the security filter chain, not CORS/TMDB
// wiring, which WebConfig handles and SecurityConfigTest never touches.
@WebMvcTest(
        controllers = SecurityConfigProbeController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class))
@Import({ SecurityConfig.class, SecurityConfigTest.TestSecurityBeans.class })
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockBean private UserRepository userRepo;

    @org.springframework.boot.test.context.TestConfiguration
    public static class TestSecurityBeans {
        @Bean
        public JwtAuthFilter jwtAuthFilter(JwtService jwtService, UserRepository userRepo) {
            return new JwtAuthFilter(jwtService, userRepo);
        }

        @Bean
        public JwtService jwtService() {
            return new JwtService("test-only-secret-at-least-256-bits-long-for-hs256-0000", 604_800_000L);
        }
    }

    private User userWithRole(User.Role role, int tokenVersion) {
        User user = new User();
        user.setId(1L);
        user.setRole(role);
        user.setTokenVersion(tokenVersion);
        return user;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    // ---- Public routes: reachable with no Authorization header at all ----

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void titlesIsPublic() throws Exception {
        mockMvc.perform(get("/api/titles")).andExpect(status().isOk());
    }

    @Test
    void moviesPopularIsPublic() throws Exception {
        mockMvc.perform(get("/api/movies/popular")).andExpect(status().isOk());
    }

    @Test
    void compatibilityQuizIsPublic() throws Exception {
        mockMvc.perform(get("/api/compatibility/quiz")).andExpect(status().isOk());
    }

    @Test
    void compatibilityCheckIsPublic() throws Exception {
        mockMvc.perform(post("/api/compatibility/check").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void authRoutesArePublic() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    // ---- Default rule: anyRequest().authenticated() ----

    @Test
    void aCatchAllRouteRejectsAnAnonymousRequestWith401() throws Exception {
        mockMvc.perform(get("/api/watch-status/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void aCatchAllRouteAcceptsAValidToken() throws Exception {
        when(userRepo.findById(1L)).thenReturn(Optional.of(userWithRole(User.Role.USER, 0)));
        String token = jwtService.issue(1L, "someone", 0);

        mockMvc.perform(get("/api/watch-status/1").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    // ---- Admin routes: hasRole("ADMIN") ----

    @Test
    void adminRouteRejectsAnAnonymousRequestWith401() throws Exception {
        mockMvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminRouteRejectsAValidNonAdminTokenWith403() throws Exception {
        when(userRepo.findById(1L)).thenReturn(Optional.of(userWithRole(User.Role.USER, 0)));
        String token = jwtService.issue(1L, "someone", 0);

        mockMvc.perform(get("/api/admin/reports").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRouteAcceptsAValidAdminToken() throws Exception {
        when(userRepo.findById(1L)).thenReturn(Optional.of(userWithRole(User.Role.ADMIN, 0)));
        String token = jwtService.issue(1L, "someone", 0);

        mockMvc.perform(get("/api/admin/reports").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    // ---- tokenVersion revocation, exercised through the real filter chain ----

    @Test
    void aTokenWithAStaleTokenVersionIsTreatedAsUnauthenticated() throws Exception {
        // The token embeds tv=0, but the live row is now at tokenVersion=1
        // (e.g. a password/email change elsewhere) — JwtAuthFilter must
        // leave this request unauthenticated, and the catch-all rule below
        // then rejects it with 401 exactly like a missing token would.
        when(userRepo.findById(1L)).thenReturn(Optional.of(userWithRole(User.Role.USER, 1)));
        String staleToken = jwtService.issue(1L, "someone", 0);

        mockMvc.perform(get("/api/watch-status/1").header("Authorization", bearer(staleToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenForADeletedUserIsTreatedAsUnauthenticated() throws Exception {
        when(userRepo.findById(anyLong())).thenReturn(Optional.empty());
        String token = jwtService.issue(1L, "someone", 0);

        mockMvc.perform(get("/api/watch-status/1").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIsTreatedAsUnauthenticated() throws Exception {
        // Same secret as the app's JwtService (so the signature still
        // verifies), but issued with an already-past expiration.
        JwtService expiredIssuer = new JwtService("test-only-secret-at-least-256-bits-long-for-hs256-0000", -60_000L);
        String expiredToken = expiredIssuer.issue(1L, "someone", 0);

        mockMvc.perform(get("/api/watch-status/1").header("Authorization", bearer(expiredToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTamperedTokenIsTreatedAsUnauthenticated() throws Exception {
        String token = jwtService.issue(1L, "someone", 0);
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        mockMvc.perform(get("/api/watch-status/1").header("Authorization", bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }
}

package com.rewatch.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.rewatch.model.User;
import com.rewatch.repository.UserRepository;
import com.rewatch.security.JwtService;
import com.rewatch.security.RateLimiterService;
import com.rewatch.service.PasswordResetService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The auth surface: register/login/forgot/reset. RateLimiterService and
 * JwtService are real instances rather than mocks — both are concrete
 * classes, and this JDK/Mockito combination can only mock interfaces (see
 * ReportControllerTest and RecommenderTest for the same constraint).
 * PasswordResetService is likewise concrete, so its own logic (already
 * covered by PasswordResetServiceTest) is stood in for by a hand-written
 * fake — this file is about AuthController's own logic: which service
 * method gets called, with what, and how the result maps to a status code.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String SECRET = "auth-controller-test-secret-at-least-256-bits-000000";

    @Mock private UserRepository userRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private HttpServletRequest request;

    private RateLimiterService rateLimiter;
    private JwtService jwtService;
    private FakePasswordResetService passwordResetService;

    private static class FakePasswordResetService extends PasswordResetService {
        boolean requestResetCalled;
        String lastRequestResetEmail;
        boolean resetPasswordCalled;
        String lastResetToken;
        String lastNewPassword;
        RuntimeException resetPasswordThrows;

        FakePasswordResetService() {
            super(null, null, null, null, null);
        }

        @Override
        public void requestReset(String email) {
            requestResetCalled = true;
            lastRequestResetEmail = email;
        }

        @Override
        public void resetPassword(String rawToken, String newPassword) {
            resetPasswordCalled = true;
            lastResetToken = rawToken;
            lastNewPassword = newPassword;
            if (resetPasswordThrows != null) {
                throw resetPasswordThrows;
            }
        }
    }

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiterService();
        jwtService = new JwtService(SECRET, 604_800_000L);
        passwordResetService = new FakePasswordResetService();
        when(request.getRemoteAddr()).thenReturn("203.0.113.5");
    }

    private AuthController controller(String adminEmailsCsv) {
        return new AuthController(userRepo, passwordEncoder, jwtService, passwordResetService, rateLimiter, adminEmailsCsv);
    }

    private AuthController controller() {
        return controller("");
    }

    private User newUser(String username, String email, String password) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);
        return user;
    }

    // ---------- register ----------

    @Test
    void registerRejectsATooShortPassword() {
        ResponseEntity<?> res = controller().register(newUser("bob", "bob@example.com", "short"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registerRejectsADuplicateEmail() {
        when(userRepo.findByEmail("taken@example.com")).thenReturn(new User());

        ResponseEntity<?> res = controller().register(newUser("bob", "taken@example.com", "longenough"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registerRejectsADuplicateUsername() {
        when(userRepo.findByEmail("bob@example.com")).thenReturn(null);
        when(userRepo.findByUsername("bob")).thenReturn(new User());

        ResponseEntity<?> res = controller().register(newUser("bob", "bob@example.com", "longenough"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registerSucceedsAndReturnsATokenWithTheHashedPasswordSaved() {
        when(userRepo.findByEmail("bob@example.com")).thenReturn(null);
        when(userRepo.findByUsername("bob")).thenReturn(null);
        when(passwordEncoder.encode("longenough")).thenReturn("hashed-password");
        when(userRepo.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> {
                    User saved = invocation.getArgument(0);
                    saved.setId(10L);
                    return saved;
                });

        ResponseEntity<?> res = controller().register(newUser("bob", "bob@example.com", "longenough"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) res.getBody();
        assertThat(body.get("token")).isNotNull();

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed-password");
    }

    @Test
    void registerAutoPromotesAnAdminEmailToTheAdminRole() {
        when(userRepo.findByEmail("owner@example.com")).thenReturn(null);
        when(userRepo.findByUsername("owner")).thenReturn(null);
        when(userRepo.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        controller("owner@example.com").register(newUser("owner", "owner@example.com", "longenough"), request);

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    void registerIsRateLimitedAfterFiftyAttemptsInAnHourFromTheSameIp() {
        // A too-short password already short-circuits before any repo
        // lookup — irrelevant here, since this test is about the rate
        // limiter tripping before the 51st attempt is even evaluated. 50
        // (not the old 5) — carrier-grade NAT means a shared IP behind one
        // carrier gateway is a real, common case, not just a single abuser.
        AuthController controller = controller();
        ResponseEntity<?> last = null;
        for (int i = 0; i < 51; i++) {
            last = controller.register(newUser("bob" + i, "bob" + i + "@example.com", "short"), request);
        }

        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(last.getHeaders().getFirst("Retry-After")).isNotNull();
    }

    // ---------- login ----------

    @Test
    void loginSucceedsWithAUsernameAndReturnsAToken() {
        User user = newUser("bob", "bob@example.com", "hashed");
        user.setId(3L);
        when(userRepo.findByEmail("bob")).thenReturn(null);
        when(userRepo.findByUsername("bob")).thenReturn(user);
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

        ResponseEntity<?> res = controller().login(Map.of("username", "bob", "password", "correct"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void loginAcceptsAnEmailInThePlaceOfAUsername() {
        User user = newUser("bob", "bob@example.com", "hashed");
        user.setId(3L);
        when(userRepo.findByEmail("bob@example.com")).thenReturn(user);
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

        ResponseEntity<?> res = controller().login(Map.of("username", "bob@example.com", "password", "correct"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void loginRejectsAWrongPassword() {
        User user = newUser("bob", "bob@example.com", "hashed");
        // A real persisted user always has an id — needed now that a failed
        // attempt is recorded against the resolved account's id, not just
        // the submitted username.
        user.setId(3L);
        when(userRepo.findByEmail("bob")).thenReturn(null);
        when(userRepo.findByUsername("bob")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        ResponseEntity<?> res = controller().login(Map.of("username", "bob", "password", "wrong"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginRejectsAnUnknownUser() {
        when(userRepo.findByEmail("ghost")).thenReturn(null);
        when(userRepo.findByUsername("ghost")).thenReturn(null);

        ResponseEntity<?> res = controller().login(Map.of("username", "ghost", "password", "whatever"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginSelfHealsAnAdminGrantForAKnownAdminEmailNotYetPromoted() {
        User user = newUser("owner", "owner@example.com", "hashed");
        user.setId(3L);
        user.setRole(User.Role.USER);
        when(userRepo.findByEmail("owner")).thenReturn(null);
        when(userRepo.findByUsername("owner")).thenReturn(user);
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(userRepo.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        controller("owner@example.com").login(Map.of("username", "owner", "password", "correct"), request);

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    void loginIsRateLimitedAfterOneHundredAttemptsIn15MinutesFromTheSameIp() {
        // A different username per attempt — isolates the IP-keyed limit
        // (100/15min, raised from the old 10: carrier-grade NAT means this
        // was never actually stopping credential stuffing for anyone behind
        // shared NAT, only ever a single machine) from the new per-account
        // failure limit below, which would otherwise trip first at 10.
        AuthController controller = controller();
        ResponseEntity<?> last = null;
        for (int i = 0; i < 101; i++) {
            String username = "bob" + i;
            // lenient: the very last iteration trips the IP limiter before
            // ever reaching these repo lookups, so that one stub goes
            // unused by design — that early return is exactly what's being
            // tested.
            org.mockito.Mockito.lenient().when(userRepo.findByEmail(username)).thenReturn(null);
            org.mockito.Mockito.lenient().when(userRepo.findByUsername(username)).thenReturn(null);
            last = controller.login(Map.of("username", username, "password", "wrong"), request);
        }

        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(last.getHeaders().getFirst("Retry-After")).isNotNull();
    }

    @Test
    void loginLocksOutOneAccountAfterTenFailuresRegardlessOfIpLimit() {
        // The limit that actually stops credential stuffing: repeated wrong
        // guesses against ONE account, independent of the (now much higher)
        // IP-keyed limit above. A real account owner behind shared NAT can
        // still log in freely from that IP; only the account being guessed
        // against gets throttled.
        User user = newUser("bob", "bob@example.com", "hashed");
        user.setId(3L);
        when(userRepo.findByEmail("bob")).thenReturn(null);
        when(userRepo.findByUsername("bob")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        AuthController controller = controller();
        ResponseEntity<?> last = null;
        for (int i = 0; i < 11; i++) {
            last = controller.login(Map.of("username", "bob", "password", "wrong"), request);
        }

        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void loginSucceedingNeverCountsAgainstTheAccountFailureLimit() {
        User user = newUser("bob", "bob@example.com", "hashed");
        user.setId(3L);
        when(userRepo.findByEmail("bob")).thenReturn(null);
        when(userRepo.findByUsername("bob")).thenReturn(user);
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

        AuthController controller = controller();
        ResponseEntity<?> last = null;
        // Comfortably more than the 10-failure budget — every one of these
        // is a real, successful login, so none of them should ever count.
        for (int i = 0; i < 15; i++) {
            last = controller.login(Map.of("username", "bob", "password", "correct"), request);
        }

        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---------- forgot-password ----------

    @Test
    void forgotPasswordAlwaysReturnsSuccessAndDelegatesToTheService() {
        ResponseEntity<?> res = controller().forgotPassword(Map.of("email", "anyone@example.com"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(passwordResetService.requestResetCalled).isTrue();
        assertThat(passwordResetService.lastRequestResetEmail).isEqualTo("anyone@example.com");
    }

    @Test
    void forgotPasswordIsRateLimitedPerIpAfterThreeAttemptsInAnHour() {
        AuthController controller = controller();
        ResponseEntity<?> last = null;
        for (int i = 0; i < 4; i++) {
            last = controller.forgotPassword(Map.of("email", "someone" + i + "@example.com"), request);
        }

        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void forgotPasswordIsRateLimitedPerEmailEvenAcrossDifferentIps() {
        AuthController controller = controller();
        ResponseEntity<?> last = null;
        for (int i = 0; i < 4; i++) {
            when(request.getRemoteAddr()).thenReturn("203.0.113." + i);
            last = controller.forgotPassword(Map.of("email", "victim@example.com"), request);
        }

        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ---------- reset-password ----------

    @Test
    void resetPasswordDelegatesToTheServiceAndReturnsSuccess() {
        ResponseEntity<?> res = controller().resetPassword(Map.of("token", "abc", "newPassword", "newpass123"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(passwordResetService.resetPasswordCalled).isTrue();
        assertThat(passwordResetService.lastResetToken).isEqualTo("abc");
        assertThat(passwordResetService.lastNewPassword).isEqualTo("newpass123");
    }

    @Test
    void resetPasswordSurfacesTheServicesValidationMessageAsA400() {
        passwordResetService.resetPasswordThrows = new IllegalArgumentException("Invalid or expired reset link.");

        ResponseEntity<?> res = controller().resetPassword(Map.of("token", "bad", "newPassword", "newpass123"), request);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().toString()).contains("Invalid or expired reset link.");
    }

    @Test
    void resetPasswordIsRateLimitedAfterTenAttemptsInAnHour() {
        AuthController controller = controller();
        ResponseEntity<?> last = null;
        for (int i = 0; i < 11; i++) {
            last = controller.resetPassword(Map.of("token", "t" + i, "newPassword", "newpass123"), request);
        }

        assertThat(last.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}

package com.rewatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.rewatch.model.PasswordResetToken;
import com.rewatch.model.User;
import com.rewatch.repository.PasswordResetTokenRepository;
import com.rewatch.repository.UserRepository;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * The two P0 findings this closes, verified directly rather than by reading
 * the source: (1) the reset link/token used to appear in a log line on any
 * unexpected send failure — a real, live risk given Render's log retention
 * — and (2) the token column stored the raw, directly-usable secret rather
 * than a hash of it, so a leaked read of that table needed no further work
 * to be exploitable.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private PasswordResetTokenRepository tokenRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    private ListAppender<ILoggingEvent> logAppender;

    private PasswordResetService newService() {
        return new PasswordResetService(userRepo, tokenRepo, passwordEncoder, emailService, "https://rewatch.example");
    }

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(PasswordResetService.class)).addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ((Logger) LoggerFactory.getLogger(PasswordResetService.class)).detachAppender(logAppender);
    }

    private static String sha256Hex(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private User aUser() {
        User user = new User();
        user.setId(42L);
        user.setEmail("person@example.com");
        user.setUsername("person");
        return user;
    }

    @Test
    void requestResetStoresAHashOfTheTokenNotTheRawToken() {
        when(userRepo.findByEmail("person@example.com")).thenReturn(aUser());
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);

        newService().requestReset("person@example.com");

        verify(tokenRepo).save(captor.capture());
        verify(emailService).sendPasswordResetEmail(any(), any(), linkCaptor.capture());

        String emailedLink = linkCaptor.getValue();
        String rawTokenFromLink = emailedLink.substring(emailedLink.indexOf("token=") + "token=".length());
        String storedValue = captor.getValue().getToken();

        assertThat(storedValue).isNotEqualTo(rawTokenFromLink);
        assertThat(storedValue).isEqualTo(sha256Hex(rawTokenFromLink));
    }

    @Test
    void resetPasswordSucceedsWithTheRawTokenFromTheEmailLinkDespiteTheHashedStorage() {
        when(userRepo.findByEmail("person@example.com")).thenReturn(aUser());
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        newService().requestReset("person@example.com");
        verify(tokenRepo).save(tokenCaptor.capture());
        verify(emailService).sendPasswordResetEmail(any(), any(), linkCaptor.capture());

        String emailedLink = linkCaptor.getValue();
        String rawToken = emailedLink.substring(emailedLink.indexOf("token=") + "token=".length());
        PasswordResetToken savedRow = tokenCaptor.getValue();
        when(tokenRepo.findByToken(savedRow.getToken())).thenReturn(Optional.of(savedRow));
        when(userRepo.findById(42L)).thenReturn(Optional.of(aUser()));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded");

        newService().resetPassword(rawToken, "new-password");

        verify(userRepo).save(any(User.class));
    }

    @Test
    void resetPasswordRejectsATokenThatWasNeverIssued() {
        when(tokenRepo.findByToken(anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> newService().resetPassword("forged-token-guess", "new-password"));
    }

    @Test
    void anUnexpectedSendFailureNeverLogsTheLinkOrTheRawToken() {
        when(userRepo.findByEmail("person@example.com")).thenReturn(aUser());
        doThrow(new RuntimeException("boom")).when(emailService).sendPasswordResetEmail(any(), any(), any());
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);

        newService().requestReset("person@example.com");

        verify(tokenRepo).save(tokenCaptor.capture());
        String storedHash = tokenCaptor.getValue().getToken();

        assertThat(logAppender.list).isNotEmpty();
        for (ILoggingEvent event : logAppender.list) {
            String formatted = event.getFormattedMessage();
            assertThat(formatted).doesNotContain("https://rewatch.example");
            assertThat(formatted).doesNotContain("reset-password?token=");
            assertThat(formatted).doesNotContain(storedHash);
        }
    }

    @Test
    void anUnexpectedSendFailureStillLeavesTheTokenUsable() {
        // The catch block's whole point: a bug in the send path must not
        // lose the already-saved token row, so a retried request (or the
        // same link, once the sender is fixed) still works.
        when(userRepo.findByEmail("person@example.com")).thenReturn(aUser());
        doThrow(new RuntimeException("boom")).when(emailService).sendPasswordResetEmail(any(), any(), any());

        newService().requestReset("person@example.com");

        verify(tokenRepo, times(1)).save(any(PasswordResetToken.class));
    }

    @Test
    void unknownEmailNeitherSavesATokenNorSendsAnEmail() {
        // requestReset()'s user-enumeration defense: identical outward
        // behavior whether or not the email is registered.
        when(userRepo.findByEmail("nobody@example.com")).thenReturn(null);

        newService().requestReset("nobody@example.com");

        verify(tokenRepo, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }
}

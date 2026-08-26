package com.rewatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.Notification;
import com.rewatch.model.User;
import com.rewatch.repository.NotificationRepository;
import com.rewatch.repository.UserRepository;

/**
 * Delivery-assertion tests at the NotificationService level, not just
 * EmailService's own unit tests — this is the layer that decides WHETHER an
 * email goes out at all (opted in, has a real address) and whose contract
 * ("the follow action itself must never fail because mail delivery did")
 * actually matters to a caller.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepo;
    @Mock private SocialService socialService;
    @Mock private UserRepository userRepo;
    @Mock private EmailService emailService;

    private NotificationService newService() {
        return new NotificationService(notificationRepo, socialService, userRepo, emailService, "https://rewatch.example");
    }

    private User followee(boolean emailNotificationsEnabled, String email) {
        User user = new User();
        user.setId(7L);
        user.setUsername("followee");
        user.setEmail(email);
        user.setEmailNotificationsEnabled(emailNotificationsEnabled);
        return user;
    }

    @Test
    void newFollowerAlwaysWritesTheInAppNotificationRegardlessOfEmailPreference() {
        when(userRepo.findById(7L)).thenReturn(Optional.of(followee(false, null)));

        newService().notifyNewFollower(7L, 9L, "follower_name");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(Notification.Type.NEW_FOLLOWER);
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getRelatedUserId()).isEqualTo(9L);
    }

    @Test
    void sendsTheEmailWhenOptedInWithARealAddress() {
        when(userRepo.findById(7L)).thenReturn(Optional.of(followee(true, "followee@example.com")));

        newService().notifyNewFollower(7L, 9L, "follower_name");

        verify(emailService).sendNewFollowerEmail(
                eq("followee@example.com"), eq("followee"), eq("follower_name"), eq("https://rewatch.example"));
    }

    @Test
    void neverSendsWhenEmailNotificationsAreDisabled() {
        when(userRepo.findById(7L)).thenReturn(Optional.of(followee(false, "followee@example.com")));

        newService().notifyNewFollower(7L, 9L, "follower_name");

        verify(emailService, never()).sendNewFollowerEmail(any(), any(), any(), any());
    }

    @Test
    void neverSendsWhenTheFolloweeHasNoRecoveryEmailOnFile() {
        // The `.rewatch.local` placeholder-domain accounts never got a real
        // email — same population changeEmail's own recovery fix targets.
        when(userRepo.findById(7L)).thenReturn(Optional.of(followee(true, null)));

        newService().notifyNewFollower(7L, 9L, "follower_name");

        verify(emailService, never()).sendNewFollowerEmail(any(), any(), any(), any());
    }

    @Test
    void anUnexpectedEmailFailureNeverPropagatesOutOfTheFollowAction() {
        // The actual contract this test exists to prove: EmailService.send()
        // no longer throws for an ordinary delivery failure at all (it
        // records the outcome instead), so this is a backstop for a truly
        // unexpected exception — and even then, the follow action and its
        // in-app notification must not fail alongside it.
        when(userRepo.findById(7L)).thenReturn(Optional.of(followee(true, "followee@example.com")));
        doThrow(new RuntimeException("smtp exploded")).when(emailService)
                .sendNewFollowerEmail(any(), any(), any(), any());

        assertThatCode(() -> newService().notifyNewFollower(7L, 9L, "follower_name")).doesNotThrowAnyException();

        verify(notificationRepo, times(1)).save(any(Notification.class));
    }

    @Test
    void aMissingFolloweeRecordNeverThrows() {
        // Defensive: notifyNewFollower's contract is "never break the follow
        // action," and the in-app row it writes doesn't require the followee
        // to still exist in the email-lookup step.
        when(userRepo.findById(7L)).thenReturn(Optional.empty());

        assertThatCode(() -> newService().notifyNewFollower(7L, 9L, "follower_name")).doesNotThrowAnyException();

        verify(emailService, never()).sendNewFollowerEmail(any(), any(), any(), any());
    }
}

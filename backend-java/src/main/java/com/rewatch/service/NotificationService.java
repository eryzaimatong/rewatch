package com.rewatch.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.dto.UserSummaryDTO;
import com.rewatch.model.Notification;
import com.rewatch.model.Trait;
import com.rewatch.model.User;
import com.rewatch.repository.NotificationRepository;
import com.rewatch.repository.UserRepository;

/**
 * Fetch-on-mount notifications — no polling/websocket infra exists anywhere in
 * this app yet (every screen is one-shot fetch-on-mount), so this deliberately
 * doesn't introduce any. New-follower and taste-milestone notifications are
 * written at the moment they happen (see SocialController.follow and
 * RatingService.submit). DNA-match notifications have no discrete "event" to
 * hook — SocialService.dnaMatches is a fully on-demand computation with
 * nothing persisted — so list() lazily recomputes and diffs against what's
 * already been notified, on read, instead of needing a scheduled job.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Cosine similarity (range -1..1) considered a "strikingly similar" match. */
    private static final double DNA_MATCH_THRESHOLD = 0.85;

    private final NotificationRepository notificationRepo;
    private final SocialService socialService;
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final String frontendBaseUrl;

    public NotificationService(NotificationRepository notificationRepo, SocialService socialService,
                               UserRepository userRepo, EmailService emailService,
                               @Value("${rewatch.frontend.base-url}") String frontendBaseUrl) {
        this.notificationRepo = notificationRepo;
        this.socialService = socialService;
        this.userRepo = userRepo;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public List<Notification> list(Long userId, int limit) {
        lazyRecomputeDnaMatches(userId);
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
    }

    public long unreadCount(Long userId) {
        return notificationRepo.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        notificationRepo.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .ifPresent(n -> {
                    n.setReadAt(Instant.now());
                    notificationRepo.save(n);
                });
    }

    @Transactional
    public void markAllRead(Long userId) {
        Instant now = Instant.now();
        notificationRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 200)).forEach(n -> {
            if (n.getReadAt() == null) {
                n.setReadAt(now);
                notificationRepo.save(n);
            }
        });
    }

    @Transactional
    public void notifyNewFollower(Long followeeId, Long followerId, String followerUsername) {
        notificationRepo.save(new Notification(followeeId, Notification.Type.NEW_FOLLOWER,
                followerUsername + " started following you", followerId, Instant.now()));
        emailNewFollowerIfOptedIn(followeeId, followerUsername);
    }

    /**
     * Best-effort — a broken/unreachable SMTP server (Gmail sending limits,
     * network issues) must never fail the follow action itself or leave the
     * in-app notification unwritten. The in-app notification above is already
     * saved and is this feature's source of truth; the email is a bonus
     * nudge, not a requirement.
     */
    private void emailNewFollowerIfOptedIn(Long followeeId, String followerUsername) {
        User followee = userRepo.findById(followeeId).orElse(null);
        if (followee == null || !followee.isEmailNotificationsEnabled() || followee.getEmail() == null) {
            return;
        }
        try {
            emailService.sendNewFollowerEmail(followee.getEmail(), followee.getUsername(), followerUsername, frontendBaseUrl);
        } catch (Exception e) {
            log.warn("Failed to send new-follower email to user {}: {}", followeeId, e.getMessage());
        }
    }

    @Transactional
    public void notifyTasteMilestone(Long userId, Trait trait, double delta) {
        String direction = delta >= 0 ? "rose" : "fell";
        String message = String.format(java.util.Locale.ROOT, "Your %s %s %.0f pts after your latest rating",
                trait.label(), direction, Math.abs(delta) * 100);
        notificationRepo.save(new Notification(userId, Notification.Type.TASTE_MILESTONE, message, null, Instant.now()));
    }

    @Transactional
    public void notifyAchievementUnlocked(Long userId, String achievementTitle) {
        notificationRepo.save(new Notification(userId, Notification.Type.ACHIEVEMENT_UNLOCKED,
                "Achievement unlocked: " + achievementTitle, null, Instant.now()));
    }

    @Transactional
    public void notifyReviewLiked(Long reviewOwnerId, Long likerId, String likerUsername, String titleName) {
        notificationRepo.save(new Notification(reviewOwnerId, Notification.Type.REVIEW_LIKED,
                likerUsername + " liked your review of " + titleName, likerId, Instant.now()));
    }

    @Transactional
    public void notifyReviewCommented(Long reviewOwnerId, Long commenterId, String commenterUsername, String titleName) {
        notificationRepo.save(new Notification(reviewOwnerId, Notification.Type.REVIEW_COMMENTED,
                commenterUsername + " commented on your review of " + titleName, commenterId, Instant.now()));
    }

    /**
     * O(all users) per call, via SocialService.dnaMatches — already the cost of
     * that feature today, just now also paid on every notification-list fetch.
     * Fine at this app's current scale; first thing to revisit if the user base
     * grows past a few hundred.
     */
    private void lazyRecomputeDnaMatches(Long userId) {
        for (UserSummaryDTO match : socialService.dnaMatches(userId, 10)) {
            if (match.getMatchScore() < DNA_MATCH_THRESHOLD) {
                continue;
            }
            boolean alreadyNotified = notificationRepo.existsByUserIdAndTypeAndRelatedUserId(
                    userId, Notification.Type.DNA_MATCH, match.getUserId());
            if (!alreadyNotified) {
                notificationRepo.save(new Notification(userId, Notification.Type.DNA_MATCH,
                        "You and " + match.getUsername() + " have strikingly similar taste",
                        match.getUserId(), Instant.now()));
            }
        }
    }
}

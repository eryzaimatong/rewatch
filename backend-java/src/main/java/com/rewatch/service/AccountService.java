package com.rewatch.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.model.Rating;
import com.rewatch.model.User;
import com.rewatch.model.WatchlistFolder;
import com.rewatch.repository.BlockRepository;
import com.rewatch.repository.CollectionFollowRepository;
import com.rewatch.repository.FollowRepository;
import com.rewatch.repository.NotificationRepository;
import com.rewatch.repository.PasswordResetTokenRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.ReportRepository;
import com.rewatch.repository.ReviewCommentRepository;
import com.rewatch.repository.ReviewLikeRepository;
import com.rewatch.repository.TraitEventRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.UserTraitRepository;
import com.rewatch.repository.WatchStatusRepository;
import com.rewatch.repository.WatchlistFolderRepository;
import com.rewatch.repository.WatchlistItemRepository;
import com.rewatch.security.JwtService;

/**
 * Self-service account management: change password, delete account. Both
 * require re-proving the current password rather than trusting the bearer
 * token alone — a stolen-but-still-valid token shouldn't be enough to hijack
 * the account permanently or destroy it.
 */
@Service
public class AccountService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RatingRepository ratingRepo;
    private final WatchlistItemRepository watchlistItemRepo;
    private final WatchlistFolderRepository watchlistFolderRepo;
    private final FollowRepository followRepo;
    private final BlockRepository blockRepo;
    private final ReportRepository reportRepo;
    private final UserTraitRepository userTraitRepo;
    private final TraitEventRepository traitEventRepo;
    private final PasswordResetTokenRepository passwordResetTokenRepo;
    private final NotificationRepository notificationRepo;
    private final CollectionFollowRepository collectionFollowRepo;
    private final ReviewLikeRepository reviewLikeRepo;
    private final ReviewCommentRepository reviewCommentRepo;
    private final WatchStatusRepository watchStatusRepo;
    private final AchievementService achievementService;

    public AccountService(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtService jwtService,
                          RatingRepository ratingRepo, WatchlistItemRepository watchlistItemRepo,
                          WatchlistFolderRepository watchlistFolderRepo, FollowRepository followRepo,
                          BlockRepository blockRepo, ReportRepository reportRepo,
                          UserTraitRepository userTraitRepo, TraitEventRepository traitEventRepo,
                          PasswordResetTokenRepository passwordResetTokenRepo,
                          NotificationRepository notificationRepo,
                          CollectionFollowRepository collectionFollowRepo,
                          ReviewLikeRepository reviewLikeRepo,
                          ReviewCommentRepository reviewCommentRepo,
                          WatchStatusRepository watchStatusRepo,
                          AchievementService achievementService) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.ratingRepo = ratingRepo;
        this.watchlistItemRepo = watchlistItemRepo;
        this.watchlistFolderRepo = watchlistFolderRepo;
        this.followRepo = followRepo;
        this.reviewLikeRepo = reviewLikeRepo;
        this.reviewCommentRepo = reviewCommentRepo;
        this.blockRepo = blockRepo;
        this.reportRepo = reportRepo;
        this.userTraitRepo = userTraitRepo;
        this.traitEventRepo = traitEventRepo;
        this.passwordResetTokenRepo = passwordResetTokenRepo;
        this.notificationRepo = notificationRepo;
        this.collectionFollowRepo = collectionFollowRepo;
        this.watchStatusRepo = watchStatusRepo;
        this.achievementService = achievementService;
    }

    /**
     * @return a freshly issued token. The token that authenticated this very
     *         request now embeds a stale tokenVersion (see User.tokenVersion)
     *         and would 401 on the next call — the caller must re-save the
     *         session with this new one immediately.
     */
    @Transactional
    public String changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user = userRepo.save(user);

        return jwtService.issue(user.getId(), user.getUsername(), user.getTokenVersion());
    }

    public boolean getProfileVisibility(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"))
                .isProfilePublic();
    }

    @Transactional
    public void setProfileVisibility(Long userId, boolean isPublic) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        user.setProfilePublic(isPublic);
        userRepo.save(user);
    }

    /** Interactive-chrome accent — see User.accentColor for what this does and doesn't recolor. */
    public static final java.util.Set<String> VALID_ACCENT_COLORS =
            java.util.Set.of("purple", "blue", "orange", "emerald");

    @Transactional
    public void setAccentColor(Long userId, String accentColor) {
        if (!VALID_ACCENT_COLORS.contains(accentColor)) {
            throw new IllegalArgumentException("Unknown accent color: " + accentColor);
        }
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        user.setAccentColor(accentColor);
        userRepo.save(user);
    }

    /**
     * A skin for the user's own /social/:id page — see User.profileTheme.
     * "cinema" is the default look (same as leaving this null), included here
     * so it's a real, settable choice rather than an implicit fallback only.
     */
    public static final java.util.Set<String> VALID_PROFILE_THEMES =
            java.util.Set.of("cinema", "vhs", "dreamy", "midnight", "indie");

    @Transactional
    public void setProfileTheme(Long userId, String profileTheme) {
        if (!VALID_PROFILE_THEMES.contains(profileTheme)) {
            throw new IllegalArgumentException("Unknown profile theme: " + profileTheme);
        }
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        user.setProfileTheme(profileTheme);
        userRepo.save(user);
    }

    /** Decoded-size ceiling for an avatar — a 256px square JPEG at reasonable quality is well under this. */
    private static final int MAX_AVATAR_BYTES = 400_000;

    @Transactional
    public void setAvatarUrl(Long userId, String avatarUrl) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));

        if (avatarUrl == null || avatarUrl.isBlank()) {
            user.setAvatarUrl(null);
            userRepo.save(user);
            return;
        }
        if (!avatarUrl.startsWith("data:image/")) {
            throw new IllegalArgumentException("Avatar must be an image data URL.");
        }
        int commaIdx = avatarUrl.indexOf(',');
        String base64Payload = commaIdx >= 0 ? avatarUrl.substring(commaIdx + 1) : "";
        // Base64 inflates size by ~4/3 — checking the encoded string length
        // directly is a cheap, sufficiently-accurate proxy for decoded bytes
        // without actually decoding just to measure.
        if (base64Payload.length() * 3L / 4 > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("Avatar image is too large — resize and try again.");
        }

        user.setAvatarUrl(avatarUrl);
        userRepo.save(user);
    }

    /**
     * Frame key -> the real AchievementService key that unlocks it. Deliberately
     * spans different categories (evidence/tastedna/social) rather than five
     * tiers of the same metric, so there isn't one single "correct" grind path.
     */
    public static final java.util.Map<String, String> FRAME_REQUIREMENTS = java.util.Map.of(
            "bronze", "rating_15",
            "silver", "rating_30",
            "gold", "rating_100",
            "signal", "conf_90",
            "social", "first_follower"
    );

    @Transactional
    public void setAvatarFrame(Long userId, String frame) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));

        if (frame == null || frame.isBlank()) {
            user.setAvatarFrame(null);
            userRepo.save(user);
            return;
        }
        String requiredAchievement = FRAME_REQUIREMENTS.get(frame);
        if (requiredAchievement == null) {
            throw new IllegalArgumentException("Unknown frame: " + frame);
        }
        boolean unlocked = achievementService.unlockedTitles(userId).containsKey(requiredAchievement);
        if (!unlocked) {
            throw new IllegalArgumentException("That frame isn't unlocked yet.");
        }

        user.setAvatarFrame(frame);
        userRepo.save(user);
    }

    private static final int MAX_NICKNAME_LENGTH = 60;

    @Transactional
    public void setNickname(Long userId, String nickname) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));

        if (nickname == null || nickname.isBlank()) {
            user.setNickname(null);
            userRepo.save(user);
            return;
        }
        String trimmed = nickname.trim();
        if (trimmed.length() > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("Nickname is too long (max " + MAX_NICKNAME_LENGTH + " characters).");
        }
        user.setNickname(trimmed);
        userRepo.save(user);
    }

    private static final int MAX_BIO_LENGTH = 200;

    @Transactional
    public void setBio(Long userId, String bio) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));

        if (bio == null || bio.isBlank()) {
            user.setBio(null);
            userRepo.save(user);
            return;
        }
        String trimmed = bio.trim();
        if (trimmed.length() > MAX_BIO_LENGTH) {
            throw new IllegalArgumentException("Bio is too long (max " + MAX_BIO_LENGTH + " characters).");
        }
        user.setBio(trimmed);
        userRepo.save(user);
    }

    private static final int MAX_PROFILE_SONG_LENGTH = 120;

    @Transactional
    public void setProfileSong(Long userId, String profileSong) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));

        if (profileSong == null || profileSong.isBlank()) {
            user.setProfileSong(null);
            userRepo.save(user);
            return;
        }
        String trimmed = profileSong.trim();
        if (trimmed.length() > MAX_PROFILE_SONG_LENGTH) {
            throw new IllegalArgumentException("That's too long (max " + MAX_PROFILE_SONG_LENGTH + " characters).");
        }
        user.setProfileSong(trimmed);
        userRepo.save(user);
    }

    private static final int MAX_PINNED_TITLES = 4;

    /**
     * All three pins are validated against this user's own real data — a
     * pinned film must be something they've actually rated, a pinned review
     * must be their own rating with a real moment, a pinned list must be
     * their own folder. Any of the three may be null/empty to unpin it.
     */
    @Transactional
    public void setPinnedContent(Long userId, List<Long> titleIds, Long ratingId, Long folderId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));

        if (titleIds != null && !titleIds.isEmpty()) {
            if (titleIds.size() > MAX_PINNED_TITLES) {
                throw new IllegalArgumentException("You can pin at most " + MAX_PINNED_TITLES + " titles.");
            }
            for (Long titleId : titleIds) {
                if (!ratingRepo.existsByUserIdAndTitleId(userId, titleId)) {
                    throw new IllegalArgumentException("You can only pin titles you've rated.");
                }
            }
            user.setPinnedTitleIds(titleIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        } else {
            user.setPinnedTitleIds(null);
        }

        if (ratingId != null) {
            Rating rating = ratingRepo.findById(ratingId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown rating"));
            if (!userId.equals(rating.getUserId()) || rating.getMoment() == null || rating.getMoment().isBlank()) {
                throw new IllegalArgumentException("You can only pin your own written review.");
            }
            user.setPinnedRatingId(ratingId);
        } else {
            user.setPinnedRatingId(null);
        }

        if (folderId != null) {
            if (!watchlistFolderRepo.existsByIdAndUserId(folderId, userId)) {
                throw new IllegalArgumentException("You can only pin your own list.");
            }
            user.setPinnedFolderId(folderId);
        } else {
            user.setPinnedFolderId(null);
        }

        userRepo.save(user);
    }

    /**
     * Hard-deletes the account and every row this codebase's schema attributes
     * to it. There are no FK constraints anywhere in this schema (ddl-auto=update
     * never added any), so this cleanup has to be explicit and ordered here
     * rather than relied on at the DB level.
     *
     * Known accepted gap: another user's Notification row that references this
     * user as relatedUserId (e.g. "X started following you") is not cleaned up
     * — same trust-the-app-layer convention as WatchlistItem.titleId elsewhere
     * in this codebase, not a new regression.
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Password is incorrect.");
        }

        traitEventRepo.deleteByUserId(userId);
        userTraitRepo.deleteByUserId(userId);
        // Same "read ids before the parent row disappears" reasoning as the
        // folder/collection-follow cleanup below — anyone who liked or
        // commented on this user's reviews needs those edges cleared too.
        List<Long> ratingIds = ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(Rating::getId).toList();
        reviewLikeRepo.deleteByRatingIdIn(ratingIds);
        reviewCommentRepo.deleteByRatingIdIn(ratingIds);
        reviewLikeRepo.deleteByUserId(userId);
        reviewCommentRepo.deleteByAuthorUserId(userId);
        ratingRepo.deleteByUserId(userId);
        watchStatusRepo.deleteByUserId(userId);
        watchlistItemRepo.deleteByUserId(userId);
        // Read this user's folder ids before deleting them — anyone who
        // followed one of these collections needs their edge cleared too, or
        // it points at a folder that no longer exists.
        for (WatchlistFolder folder : watchlistFolderRepo.findByUserIdOrderByNameAsc(userId)) {
            collectionFollowRepo.deleteByFolderId(folder.getId());
        }
        watchlistFolderRepo.deleteByUserId(userId);
        collectionFollowRepo.deleteByUserId(userId);
        followRepo.deleteByFollowerId(userId);
        followRepo.deleteByFolloweeId(userId);
        blockRepo.deleteByBlockerId(userId);
        blockRepo.deleteByBlockedId(userId);
        reportRepo.deleteByReporterId(userId);
        reportRepo.deleteByReportedUserId(userId);
        passwordResetTokenRepo.deleteByUserId(userId);
        notificationRepo.deleteByUserId(userId);
        userRepo.delete(user);
    }
}

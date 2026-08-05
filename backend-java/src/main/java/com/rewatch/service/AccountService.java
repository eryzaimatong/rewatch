package com.rewatch.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.model.User;
import com.rewatch.repository.BlockRepository;
import com.rewatch.repository.FollowRepository;
import com.rewatch.repository.NotificationRepository;
import com.rewatch.repository.PasswordResetTokenRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.ReportRepository;
import com.rewatch.repository.TraitEventRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.UserTraitRepository;
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

    public AccountService(UserRepository userRepo, PasswordEncoder passwordEncoder, JwtService jwtService,
                          RatingRepository ratingRepo, WatchlistItemRepository watchlistItemRepo,
                          WatchlistFolderRepository watchlistFolderRepo, FollowRepository followRepo,
                          BlockRepository blockRepo, ReportRepository reportRepo,
                          UserTraitRepository userTraitRepo, TraitEventRepository traitEventRepo,
                          PasswordResetTokenRepository passwordResetTokenRepo,
                          NotificationRepository notificationRepo) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.ratingRepo = ratingRepo;
        this.watchlistItemRepo = watchlistItemRepo;
        this.watchlistFolderRepo = watchlistFolderRepo;
        this.followRepo = followRepo;
        this.blockRepo = blockRepo;
        this.reportRepo = reportRepo;
        this.userTraitRepo = userTraitRepo;
        this.traitEventRepo = traitEventRepo;
        this.passwordResetTokenRepo = passwordResetTokenRepo;
        this.notificationRepo = notificationRepo;
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
        ratingRepo.deleteByUserId(userId);
        watchlistItemRepo.deleteByUserId(userId);
        watchlistFolderRepo.deleteByUserId(userId);
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

package com.rewatch.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.repository.BlockRepository;
import com.rewatch.repository.DailyGuessRepository;
import com.rewatch.repository.FollowRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.ReviewCommentRepository;
import com.rewatch.repository.ReviewLikeRepository;
import com.rewatch.repository.TraitEventRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.UserTraitRepository;
import com.rewatch.repository.WatchlistFolderRepository;
import com.rewatch.repository.WatchlistItemRepository;

/**
 * Disaster-recovery export, not a general-purpose data API — the whole point
 * is a downloadable snapshot of everything that can't be recreated from
 * TMDB or recomputed (Titles/catalog data are deliberately excluded; see
 * CatalogService/EnrichmentService for how those get rebuilt on a fresh
 * database instead). Written specifically as a stopgap for this app's free
 * Postgres instance having a hard 30-day expiry with no automated backup
 * tool wired up yet (see DEPLOYMENT.md's Known Gaps and db-guardian.yml).
 *
 * Deliberately reuses the existing ADMIN-role JWT auth (SecurityConfig
 * already gates all of /api/admin/** behind hasRole("ADMIN")) rather than
 * inventing a new API key/secret — this app already has exactly the
 * authorization primitive this needs.
 *
 * User.password is WRITE_ONLY (see User.java's own doc comment: "must never
 * leave the process in a response body") and this endpoint does not bypass
 * that, on purpose — a backup that could leak every user's password hash
 * over HTTP, even admin-gated, is a worse tradeoff than accounts needing a
 * password reset after a real restore. Everything else that actually
 * defines a user's account and history is included.
 */
@RestController
@RequestMapping("/api/admin")
public class BackupController {

    private final UserRepository userRepo;
    private final RatingRepository ratingRepo;
    private final WatchlistFolderRepository watchlistFolderRepo;
    private final WatchlistItemRepository watchlistItemRepo;
    private final FollowRepository followRepo;
    private final ReviewCommentRepository reviewCommentRepo;
    private final ReviewLikeRepository reviewLikeRepo;
    private final BlockRepository blockRepo;
    private final DailyGuessRepository dailyGuessRepo;
    private final UserTraitRepository userTraitRepo;
    private final TraitEventRepository traitEventRepo;

    public BackupController(UserRepository userRepo, RatingRepository ratingRepo,
            WatchlistFolderRepository watchlistFolderRepo, WatchlistItemRepository watchlistItemRepo,
            FollowRepository followRepo, ReviewCommentRepository reviewCommentRepo,
            ReviewLikeRepository reviewLikeRepo, BlockRepository blockRepo,
            DailyGuessRepository dailyGuessRepo, UserTraitRepository userTraitRepo,
            TraitEventRepository traitEventRepo) {
        this.userRepo = userRepo;
        this.ratingRepo = ratingRepo;
        this.watchlistFolderRepo = watchlistFolderRepo;
        this.watchlistItemRepo = watchlistItemRepo;
        this.followRepo = followRepo;
        this.reviewCommentRepo = reviewCommentRepo;
        this.reviewLikeRepo = reviewLikeRepo;
        this.blockRepo = blockRepo;
        this.dailyGuessRepo = dailyGuessRepo;
        this.userTraitRepo = userTraitRepo;
        this.traitEventRepo = traitEventRepo;
    }

    @GetMapping("/backup")
    public Map<String, Object> backup() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now().toString());
        out.put("users", userRepo.findAll());
        out.put("ratings", ratingRepo.findAll());
        out.put("watchlistFolders", watchlistFolderRepo.findAll());
        out.put("watchlistItems", watchlistItemRepo.findAll());
        out.put("follows", followRepo.findAll());
        out.put("reviewComments", reviewCommentRepo.findAll());
        out.put("reviewLikes", reviewLikeRepo.findAll());
        out.put("blocks", blockRepo.findAll());
        out.put("dailyGuesses", dailyGuessRepo.findAll());
        out.put("userTraits", userTraitRepo.findAll());
        out.put("traitEvents", traitEventRepo.findAll());
        return out;
    }
}

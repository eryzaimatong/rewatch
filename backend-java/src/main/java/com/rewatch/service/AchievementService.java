package com.rewatch.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;
import com.rewatch.model.User;
import com.rewatch.repository.FollowRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.WatchlistItemRepository;

/**
 * Milestones over data that already exists elsewhere in the app — every
 * threshold here reads a real, already-persisted count (ratings, follows,
 * watchlist size) or a real derived value (meanConfidence). Nothing is
 * invented for this feature; a badge only lights up when the underlying
 * number actually crosses the line.
 *
 * Confidence tiers start above {@code ONBOARDING_CONFIDENCE} (0.35, see
 * ProfileService) so a badge can't unlock from onboarding alone — it has to
 * come from rating evidence, since evid (and therefore conf) only rises
 * through ProfileService.replay().
 */
@Service
public class AchievementService {

    public record Achievement(String key, String category, String title, String description,
                              boolean unlocked, double current, double target) {}

    public record Summary(int unlockedCount, int totalCount, List<Achievement> achievements) {}

    private record RatingTier(String key, String title, String description, int threshold) {}
    private record ConfidenceTier(String key, String title, String description, double threshold) {}

    private static final List<RatingTier> RATING_TIERS = List.of(
            new RatingTier("first_rating", "Opening Night", "Rate your first title.", 1),
            new RatingTier("rating_5", "Taste Emerging", "Rate 5 titles.", 5),
            new RatingTier("rating_15", "Well-Defined Profile", "Rate 15 titles.", 15),
            new RatingTier("rating_30", "Seasoned Critic", "Rate 30 titles.", 30),
            new RatingTier("rating_100", "Completionist", "Rate 100 titles.", 100));

    private static final List<ConfidenceTier> CONFIDENCE_TIERS = List.of(
            new ConfidenceTier("conf_40", "Signal Forming", "Reach 40% TasteDNA confidence.", 0.40),
            new ConfidenceTier("conf_60", "Solid Read", "Reach 60% TasteDNA confidence.", 0.60),
            new ConfidenceTier("conf_80", "High Fidelity", "Reach 80% TasteDNA confidence.", 0.80),
            new ConfidenceTier("conf_90", "TasteDNA Mastery", "Reach 90% TasteDNA confidence.", 0.90));

    /** Keyed off the all-time longest streak, not the current one — a streak
     *  you hit once and then broke should stay unlocked, the same way rating
     *  and confidence tiers below never re-lock if the underlying number
     *  later dips. */
    private record StreakTier(String key, String title, String description, int days) {}

    private static final List<StreakTier> STREAK_TIERS = List.of(
            new StreakTier("streak_3", "Warming Up", "Rate something 3 days in a row.", 3),
            new StreakTier("streak_7", "On a Roll", "Rate something 7 days in a row.", 7),
            new StreakTier("streak_30", "Certified Regular", "Rate something 30 days in a row.", 30));

    private final RatingRepository ratingRepo;
    private final FollowRepository followRepo;
    private final WatchlistItemRepository watchlistItemRepo;
    private final UserRepository userRepo;
    private final ProfileService profileService;
    private final StreakService streakService;

    public AchievementService(RatingRepository ratingRepo, FollowRepository followRepo,
                              WatchlistItemRepository watchlistItemRepo, UserRepository userRepo,
                              ProfileService profileService, StreakService streakService) {
        this.ratingRepo = ratingRepo;
        this.followRepo = followRepo;
        this.watchlistItemRepo = watchlistItemRepo;
        this.userRepo = userRepo;
        this.profileService = profileService;
        this.streakService = streakService;
    }

    public Summary build(Long userId) {
        long ratingCount = ratingRepo.countByUserId(userId);
        long followingCount = followRepo.countByFollowerId(userId);
        long followerCount = followRepo.countByFolloweeId(userId);
        long watchlistCount = watchlistItemRepo.countByUserId(userId);
        Map<Trait, TraitNode> profile = profileService.currentProfile(userId);
        double meanConfidence = profileService.meanConfidence(profile);
        String dealbreakers = userRepo.findById(userId).map(User::getDealbreakers).orElse(null);

        List<Achievement> out = new ArrayList<>();

        for (RatingTier tier : RATING_TIERS) {
            out.add(new Achievement(tier.key(), "evidence", tier.title(), tier.description(),
                    ratingCount >= tier.threshold(), ratingCount, tier.threshold()));
        }

        for (ConfidenceTier tier : CONFIDENCE_TIERS) {
            out.add(new Achievement(tier.key(), "tastedna", tier.title(), tier.description(),
                    meanConfidence >= tier.threshold(),
                    Math.round(meanConfidence * 100.0), Math.round(tier.threshold() * 100.0)));
        }

        int longestStreak = streakService.compute(userId).longest();
        for (StreakTier tier : STREAK_TIERS) {
            out.add(new Achievement(tier.key(), "streak", tier.title(), tier.description(),
                    longestStreak >= tier.days(), longestStreak, tier.days()));
        }

        out.add(new Achievement("first_follow", "social", "On Your Radar",
                "Follow another taste profile.", followingCount >= 1, followingCount, 1));
        out.add(new Achievement("first_follower", "social", "Word's Out",
                "Get your first follower.", followerCount >= 1, followerCount, 1));
        out.add(new Achievement("watchlist_5", "collecting", "Starter Pack",
                "Add 5 titles to your watchlist.", watchlistCount >= 5, watchlistCount, 5));
        out.add(new Achievement("dealbreakers_set", "tastedna", "Know Thyself",
                "Set at least one dealbreaker during onboarding.",
                dealbreakers != null && !dealbreakers.isBlank(),
                dealbreakers != null && !dealbreakers.isBlank() ? 1 : 0, 1));

        int unlockedCount = (int) out.stream().filter(Achievement::unlocked).count();
        return new Summary(unlockedCount, out.size(), out);
    }

    /**
     * Key -> title of every currently-unlocked achievement. Callers that mutate
     * something an achievement depends on (a rating, a follow, a watchlist add)
     * snapshot this before and after the write and notify on any new key — see
     * RatingService.submit for the reference usage.
     */
    public Map<String, String> unlockedTitles(Long userId) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Achievement a : build(userId).achievements()) {
            if (a.unlocked()) {
                out.put(a.key(), a.title());
            }
        }
        return out;
    }
}

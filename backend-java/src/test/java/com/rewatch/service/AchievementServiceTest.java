package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;
import com.rewatch.model.User;
import com.rewatch.model.UserTrait;
import com.rewatch.repository.FollowRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.TraitEventRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.UserTraitRepository;
import com.rewatch.repository.WatchlistItemRepository;

/**
 * Every badge here must trace to a real number — this test locks that down by
 * checking both sides of each threshold rather than just the happy path.
 */
@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock private RatingRepository ratingRepo;
    @Mock private FollowRepository followRepo;
    @Mock private WatchlistItemRepository watchlistItemRepo;
    @Mock private UserRepository userRepo;
    @Mock private TitleRepository titleRepo;
    @Mock private UserTraitRepository userTraitRepo;
    @Mock private TraitEventRepository traitEventRepo;

    private static final Long USER_ID = 42L;

    private AchievementService newService() {
        ProfileService profileService = new ProfileService(
                ratingRepo, titleRepo, userTraitRepo, traitEventRepo, userRepo, new VectorEngine());
        return new AchievementService(
                ratingRepo, followRepo, watchlistItemRepo, userRepo, profileService, new StreakService(ratingRepo));
    }

    /** A materialised profile where every trait carries the given confidence. */
    private List<UserTrait> profileAtConfidence(double conf) {
        List<UserTrait> rows = new java.util.ArrayList<>();
        for (Trait t : Trait.values()) {
            rows.add(new UserTrait(USER_ID, t, new TraitNode(0.5, conf, 0, 10, Instant.now())));
        }
        return rows;
    }

    @Test
    void ratingCountTiersUnlockOnlyAtOrAboveTheirThreshold() {
        when(userTraitRepo.findByUserId(USER_ID)).thenReturn(List.of());
        when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());
        when(followRepo.countByFollowerId(USER_ID)).thenReturn(0L);
        when(followRepo.countByFolloweeId(USER_ID)).thenReturn(0L);
        when(watchlistItemRepo.countByUserId(USER_ID)).thenReturn(0L);
        when(ratingRepo.countByUserId(USER_ID)).thenReturn(15L);

        AchievementService.Summary summary = newService().build(USER_ID);

        assertUnlocked(summary, "first_rating", true);
        assertUnlocked(summary, "rating_5", true);
        assertUnlocked(summary, "rating_15", true);
        assertUnlocked(summary, "rating_30", false);
        assertUnlocked(summary, "rating_100", false);
    }

    @Test
    void confidenceTiersDoNotUnlockFromOnboardingAlone() {
        // 0.35 is ProfileService's ONBOARDING_CONFIDENCE seed — no rating evidence yet.
        when(userTraitRepo.findByUserId(USER_ID)).thenReturn(profileAtConfidence(0.35));
        when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());
        when(followRepo.countByFollowerId(USER_ID)).thenReturn(0L);
        when(followRepo.countByFolloweeId(USER_ID)).thenReturn(0L);
        when(watchlistItemRepo.countByUserId(USER_ID)).thenReturn(0L);
        when(ratingRepo.countByUserId(USER_ID)).thenReturn(0L);

        AchievementService.Summary summary = newService().build(USER_ID);

        assertUnlocked(summary, "conf_40", false);
        assertUnlocked(summary, "conf_60", false);
    }

    @Test
    void confidenceTierUnlocksOnceRealEvidenceCrossesIt() {
        when(userTraitRepo.findByUserId(USER_ID)).thenReturn(profileAtConfidence(0.65));
        when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());
        when(followRepo.countByFollowerId(USER_ID)).thenReturn(0L);
        when(followRepo.countByFolloweeId(USER_ID)).thenReturn(0L);
        when(watchlistItemRepo.countByUserId(USER_ID)).thenReturn(0L);
        when(ratingRepo.countByUserId(USER_ID)).thenReturn(12L);

        AchievementService.Summary summary = newService().build(USER_ID);

        assertUnlocked(summary, "conf_40", true);
        assertUnlocked(summary, "conf_60", true);
        assertUnlocked(summary, "conf_80", false);
    }

    @Test
    void socialAndDealbreakerBadgesReadRealUserState() {
        when(userTraitRepo.findByUserId(USER_ID)).thenReturn(List.of());
        User user = new User();
        user.setDealbreakers("excessive gore,sad ending");
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
        when(followRepo.countByFollowerId(USER_ID)).thenReturn(1L);
        when(followRepo.countByFolloweeId(USER_ID)).thenReturn(0L);
        when(watchlistItemRepo.countByUserId(USER_ID)).thenReturn(3L);
        when(ratingRepo.countByUserId(USER_ID)).thenReturn(0L);

        AchievementService.Summary summary = newService().build(USER_ID);

        assertUnlocked(summary, "first_follow", true);
        assertUnlocked(summary, "first_follower", false);
        assertUnlocked(summary, "watchlist_5", false);
        assertUnlocked(summary, "dealbreakers_set", true);
    }

    private void assertUnlocked(AchievementService.Summary summary, String key, boolean expected) {
        AchievementService.Achievement match = summary.achievements().stream()
                .filter(a -> a.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no achievement with key " + key));
        if (expected) {
            assertTrue(match.unlocked(), key + " should be unlocked");
        } else {
            assertFalse(match.unlocked(), key + " should be locked");
        }
    }
}

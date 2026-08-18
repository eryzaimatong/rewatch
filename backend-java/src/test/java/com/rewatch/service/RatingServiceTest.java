package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.dto.RatingDTO;
import com.rewatch.model.Notification;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitVector;
import com.rewatch.repository.FollowRepository;
import com.rewatch.repository.NotificationRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.TraitEventRepository;
import com.rewatch.repository.UserRepository;
import com.rewatch.repository.UserTraitRepository;
import com.rewatch.repository.WatchStatusRepository;
import com.rewatch.repository.WatchlistItemRepository;

/**
 * Covers the achievement-unlock notification wired into RatingService.submit:
 * it must fire exactly once, on the rating that actually crosses a threshold —
 * not on every rating, and not twice for the same badge.
 */
@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock private RatingRepository ratingRepo;
    @Mock private TitleRepository titleRepo;
    @Mock private UserTraitRepository userTraitRepo;
    @Mock private TraitEventRepository traitEventRepo;
    @Mock private UserRepository userRepo;
    @Mock private FollowRepository followRepo;
    @Mock private WatchlistItemRepository watchlistItemRepo;
    @Mock private NotificationRepository notificationRepo;
    @Mock private WatchStatusRepository watchStatusRepo;

    private static final Long USER_ID = 7L;

    private RatingService newService() {
        ProfileService profileService = new ProfileService(
                ratingRepo, titleRepo, userTraitRepo, traitEventRepo, userRepo, new VectorEngine());
        AchievementService achievementService = new AchievementService(
                ratingRepo, followRepo, watchlistItemRepo, userRepo, profileService, new StreakService(ratingRepo));
        NotificationService notificationService = new NotificationService(notificationRepo, null, null, null, null);
        WatchStatusService watchStatusService = new WatchStatusService(watchStatusRepo, ratingRepo);
        return new RatingService(ratingRepo, titleRepo, null, null, profileService,
                notificationService, achievementService, watchStatusService);
    }

    private Title titleWith(long id) {
        Title t = new Title();
        t.setId(id);
        double[] vals = new double[Trait.count()];
        java.util.Arrays.fill(vals, 0.9);
        t.setTraitVector(TraitVector.of(vals));
        t.setFeatureConfidence(0.8);
        return t;
    }

    private RatingDTO ratingFor(long titleId) {
        RatingDTO dto = new RatingDTO();
        dto.setUserId(USER_ID);
        dto.setTitleId(titleId);
        dto.setOverall(5);
        dto.setChars(5);
        dto.setEnding(5);
        dto.setVisuals(5);
        dto.setStory(5);
        dto.setRewatch(5);
        return dto;
    }

    /** The rating log replay() reads once, after this rating is persisted. */
    private void stubRatingLog(int count) {
        List<com.rewatch.model.Rating> log = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            com.rewatch.model.Rating r = new com.rewatch.model.Rating();
            r.setId((long) i);
            r.setUserId(USER_ID);
            r.setTitleId(1L);
            r.setOverall(5);
            r.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i));
            log.add(r);
        }
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(USER_ID)).thenReturn(log);
    }

    @Test
    void firstRatingEverNotifiesTheFirstRatingBadgeOnce() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());
        when(userTraitRepo.findByUserId(USER_ID)).thenReturn(List.of());
        org.mockito.Mockito.doNothing().when(traitEventRepo).deleteByUserId(USER_ID);
        when(titleRepo.findById(1L)).thenReturn(Optional.of(titleWith(1L)));
        when(titleRepo.findAllById(any())).thenReturn(List.of(titleWith(1L)));
        when(ratingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // achievementService.unlockedTitles is snapshotted before the write (count
        // still 0) and after replay (count now 1) — sequential stubbing mirrors that.
        when(ratingRepo.countByUserId(USER_ID)).thenReturn(0L, 1L);
        stubRatingLog(1);

        newService().submit(ratingFor(1L));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo, times(1)).save(captor.capture());
        Notification saved = captor.getValue();
        assertEquals(Notification.Type.ACHIEVEMENT_UNLOCKED, saved.getType());
        assertEquals("Achievement unlocked: Opening Night", saved.getMessage());
    }

    @Test
    void ratingThatDoesNotCrossAnyThresholdNotifiesNothing() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());
        when(userTraitRepo.findByUserId(USER_ID)).thenReturn(List.of());
        org.mockito.Mockito.doNothing().when(traitEventRepo).deleteByUserId(USER_ID);
        when(titleRepo.findById(1L)).thenReturn(Optional.of(titleWith(1L)));
        when(titleRepo.findAllById(any())).thenReturn(List.of(titleWith(1L)));
        when(ratingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Both before and after this submit, the count is already 2 — simulates a
        // rating that was already accounted for, crossing no new tier.
        when(ratingRepo.countByUserId(USER_ID)).thenReturn(2L);
        stubRatingLog(2);

        newService().submit(ratingFor(1L));

        verify(notificationRepo, times(0)).save(any());
    }

    @Test
    void firstRatingEverIsNotARewatch() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());
        when(userTraitRepo.findByUserId(USER_ID)).thenReturn(List.of());
        org.mockito.Mockito.doNothing().when(traitEventRepo).deleteByUserId(USER_ID);
        when(titleRepo.findById(1L)).thenReturn(Optional.of(titleWith(1L)));
        when(titleRepo.findAllById(any())).thenReturn(List.of(titleWith(1L)));
        when(ratingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ratingRepo.countByUserId(USER_ID)).thenReturn(0L, 1L);
        when(ratingRepo.countByUserIdAndTitleId(USER_ID, 1L)).thenReturn(0L);
        stubRatingLog(1);

        RatingService.Result result = newService().submit(ratingFor(1L));

        assertFalse(result.isRewatch());
        assertEquals(1, result.watchNumber());
        assertEquals(null, result.previousOverall());
    }

    @Test
    void secondRatingOfTheSameTitleIsARewatchWithThePriorScore() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.empty());
        when(userTraitRepo.findByUserId(USER_ID)).thenReturn(List.of());
        org.mockito.Mockito.doNothing().when(traitEventRepo).deleteByUserId(USER_ID);
        when(titleRepo.findById(1L)).thenReturn(Optional.of(titleWith(1L)));
        when(titleRepo.findAllById(any())).thenReturn(List.of(titleWith(1L)));
        when(ratingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ratingRepo.countByUserId(USER_ID)).thenReturn(2L);
        when(ratingRepo.countByUserIdAndTitleId(USER_ID, 1L)).thenReturn(1L);
        com.rewatch.model.Rating priorRating = new com.rewatch.model.Rating();
        priorRating.setOverall(3);
        when(ratingRepo.findFirstByUserIdAndTitleIdOrderByCreatedAtDescIdDesc(USER_ID, 1L))
                .thenReturn(Optional.of(priorRating));
        stubRatingLog(2);

        RatingService.Result result = newService().submit(ratingFor(1L));

        assertTrue(result.isRewatch());
        assertEquals(2, result.watchNumber());
        assertEquals(3, result.previousOverall());
        verify(watchStatusRepo, times(1)).deleteByUserIdAndTitleId(USER_ID, 1L);
    }
}

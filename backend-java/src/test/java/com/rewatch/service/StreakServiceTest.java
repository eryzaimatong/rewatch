package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.Rating;
import com.rewatch.repository.RatingRepository;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    @Mock private RatingRepository ratingRepo;

    private static final Long USER_ID = 1L;

    private StreakService newService() {
        return new StreakService(ratingRepo);
    }

    private Rating ratingDaysAgo(int daysAgo) {
        Rating r = new Rating();
        r.setCreatedAt(Instant.now().atZone(ZoneOffset.UTC).toLocalDate()
                .minusDays(daysAgo).atStartOfDay(ZoneOffset.UTC).toInstant());
        return r;
    }

    @Test
    void noRatingsIsAZeroStreak() {
        when(ratingRepo.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of());
        StreakService.Streak streak = newService().compute(USER_ID);
        assertEquals(0, streak.current());
        assertEquals(0, streak.longest());
        assertFalse(streak.activeToday());
    }

    @Test
    void aRatingTodayIsAOneDayActiveStreak() {
        when(ratingRepo.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(ratingDaysAgo(0)));
        StreakService.Streak streak = newService().compute(USER_ID);
        assertEquals(1, streak.current());
        assertTrue(streak.activeToday());
    }

    @Test
    void ratingYesterdayButNotTodayStillCountsAsAnActiveStreak() {
        // A streak isn't broken just because today hasn't happened yet — it's
        // "at risk," not zero, until a full day passes with nothing rated.
        when(ratingRepo.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(ratingDaysAgo(1)));
        StreakService.Streak streak = newService().compute(USER_ID);
        assertEquals(1, streak.current());
        assertFalse(streak.activeToday());
    }

    @Test
    void aGapOfTwoOrMoreDaysBreaksTheCurrentStreak() {
        when(ratingRepo.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(ratingDaysAgo(2)));
        StreakService.Streak streak = newService().compute(USER_ID);
        assertEquals(0, streak.current());
    }

    @Test
    void consecutiveDaysCountTowardBothCurrentAndLongest() {
        when(ratingRepo.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(ratingDaysAgo(0), ratingDaysAgo(1), ratingDaysAgo(2), ratingDaysAgo(3)));
        StreakService.Streak streak = newService().compute(USER_ID);
        assertEquals(4, streak.current());
        assertEquals(4, streak.longest());
    }

    @Test
    void multipleRatingsTheSameDayOnlyCountOnce() {
        Rating a = ratingDaysAgo(0);
        Rating b = ratingDaysAgo(0);
        when(ratingRepo.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(a, b));
        StreakService.Streak streak = newService().compute(USER_ID);
        assertEquals(1, streak.current());
    }

    @Test
    void longestStreakSurvivesAfterTheCurrentOneBreaks() {
        // An old 5-day run, then a gap, then a fresh single day active today.
        when(ratingRepo.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(
                ratingDaysAgo(0),
                ratingDaysAgo(10), ratingDaysAgo(11), ratingDaysAgo(12), ratingDaysAgo(13), ratingDaysAgo(14)));
        StreakService.Streak streak = newService().compute(USER_ID);
        assertEquals(1, streak.current());
        assertEquals(5, streak.longest());
    }
}

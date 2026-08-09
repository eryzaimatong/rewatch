package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.model.WatchStatus;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.WatchStatusRepository;

/**
 * "Watched" and "Plan to watch" are deliberately not stored here — see
 * WatchStatus's javadoc. statusesFor()'s merge logic (explicit row wins over
 * the rating-derived "WATCHED" fallback) is the one non-trivial piece of
 * logic in this service, so it gets direct coverage.
 */
@ExtendWith(MockitoExtension.class)
class WatchStatusServiceTest {

    @Mock private WatchStatusRepository watchStatusRepo;
    @Mock private RatingRepository ratingRepo;

    private WatchStatusService newService() {
        return new WatchStatusService(watchStatusRepo, ratingRepo);
    }

    private static final Long USER_ID = 1L;
    private static final Long TITLE_ID = 42L;

    @Test
    void setStatusRejectsAnUnknownStatus() {
        assertThrows(IllegalArgumentException.class, () -> newService().setStatus(USER_ID, TITLE_ID, "BINGED"));
    }

    @Test
    void setStatusUpsertsAValidStatus() {
        when(watchStatusRepo.findByUserIdAndTitleId(USER_ID, TITLE_ID)).thenReturn(Optional.empty());
        when(watchStatusRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        newService().setStatus(USER_ID, TITLE_ID, "watching");

        org.mockito.ArgumentCaptor<WatchStatus> captor = org.mockito.ArgumentCaptor.forClass(WatchStatus.class);
        verify(watchStatusRepo).save(captor.capture());
        assertEquals("WATCHING", captor.getValue().getStatus());
    }

    @Test
    void setStatusWithBlankStatusClearsInstead() {
        newService().setStatus(USER_ID, TITLE_ID, "  ");
        verify(watchStatusRepo).deleteByUserIdAndTitleId(USER_ID, TITLE_ID);
        verify(watchStatusRepo, never()).save(any());
    }

    @Test
    void clearStatusDeletesTheRow() {
        newService().clearStatus(USER_ID, TITLE_ID);
        verify(watchStatusRepo, times(1)).deleteByUserIdAndTitleId(USER_ID, TITLE_ID);
    }

    @Test
    void statusesForMergesExplicitRowsWithRatingDerivedWatched() {
        WatchStatus watching = new WatchStatus();
        watching.setTitleId(10L);
        watching.setStatus("WATCHING");
        when(watchStatusRepo.findByUserId(USER_ID)).thenReturn(List.of(watching));
        // Title 10 also has a rating, but the explicit WATCHING row must win;
        // title 20 has only a rating, so it falls back to WATCHED.
        when(ratingRepo.findDistinctTitleIdsByUserId(USER_ID)).thenReturn(List.of(10L, 20L));

        Map<Long, String> statuses = newService().statusesFor(USER_ID);

        assertEquals("WATCHING", statuses.get(10L));
        assertEquals("WATCHED", statuses.get(20L));
        assertEquals(2, statuses.size());
    }

    @Test
    void statusesForIsEmptyWithNoRowsAndNoRatings() {
        when(watchStatusRepo.findByUserId(USER_ID)).thenReturn(List.of());
        when(ratingRepo.findDistinctTitleIdsByUserId(USER_ID)).thenReturn(List.of());

        assertTrue(newService().statusesFor(USER_ID).isEmpty());
    }
}

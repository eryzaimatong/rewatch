package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.TraitEventRepository;

/**
 * Every sentence must trace to a real number from WrappedSummary — this locks
 * down the two cases that matter: a month with a real shift names it, and a
 * quiet month says so honestly instead of inventing drama.
 */
@ExtendWith(MockitoExtension.class)
class TasteJourneyServiceTest {

    @Mock private RatingRepository ratingRepo;
    @Mock private TraitEventRepository traitEventRepo;
    @Mock private TitleRepository titleRepo;

    private TasteJourneyService newService() {
        WrappedService wrappedService = new WrappedService(traitEventRepo, ratingRepo, titleRepo, new ArchetypeService());
        return new TasteJourneyService(ratingRepo, wrappedService);
    }

    private WrappedService.WrappedSummary summaryWith(int ratingCount, String archetype,
                                                       List<WrappedService.TraitShift> shifts) {
        Instant now = Instant.now();
        return new WrappedService.WrappedSummary(
                "August 2026", now, now, true, ratingCount, archetype, "blurb", shifts, List.of());
    }

    @Test
    void namesNotableShiftsAndTheArchetype() {
        List<WrappedService.TraitShift> shifts = List.of(
                new WrappedService.TraitShift("bitter", "Bittersweet", 0.5, 0.62, 0.12),
                new WrappedService.TraitShift("comfort", "Comfort", 0.6, 0.54, -0.06));

        String sentence = newService().sentenceFor(summaryWith(5, "Bittersweet Realist", shifts));

        assertTrue(sentence.contains("5 titles rated."), sentence);
        assertTrue(sentence.contains("Bittersweet rose 12pts"), sentence);
        assertTrue(sentence.contains("Comfort eased 6pts"), sentence);
        assertTrue(sentence.contains("Reading as Bittersweet Realist."), sentence);
    }

    @Test
    void quietMonthDoesNotInventDrama() {
        List<WrappedService.TraitShift> shifts = List.of(
                new WrappedService.TraitShift("bitter", "Bittersweet", 0.50, 0.51, 0.01),
                new WrappedService.TraitShift("comfort", "Comfort", 0.60, 0.59, -0.01));

        String sentence = newService().sentenceFor(summaryWith(1, "Emotional Storyteller", shifts));

        assertEquals("1 title rated. Taste held steady.", sentence);
    }

    @Test
    void buildSkipsMonthsWithNoRatingsAndReturnsEmptyForNoHistory() {
        when(ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of());

        List<TasteJourneyService.JourneyEntry> entries = newService().build(1L);

        assertTrue(entries.isEmpty());
    }
}

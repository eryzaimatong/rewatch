package com.rewatch.service;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import com.rewatch.model.Rating;
import com.rewatch.repository.RatingRepository;

/**
 * Turns WrappedService's real per-month trait shifts into a plain-language
 * timeline — one sentence per month that had ratings, e.g. "5 titles rated.
 * Bittersweet rose 12pts, Comfort eased 6pts. Reading as a Bittersweet
 * Realist." Not a new data pipeline: every number here is exactly what
 * TasteDNAController's /wrapped/{id} already returns for that month, just
 * strung across the calendar instead of one month at a time.
 *
 * Deliberately does NOT infer genre/story-content claims ("you watched more
 * coming-of-age stories") — that would need signal this app doesn't have
 * (per-title genre isn't attributed to a trait shift). Every sentence stays
 * traceable to a real TraitShift or rating count.
 */
@Service
public class TasteJourneyService {

    /** A shift smaller than this reads as noise, not a real movement. */
    private static final double NOTABLE_DELTA = 0.03;

    /** At most this many shifts named per month, to keep sentences short. */
    private static final int MAX_SHIFTS_NAMED = 2;

    public record JourneyEntry(String period, int ratingCount, String sentence) {}

    private final RatingRepository ratingRepo;
    private final WrappedService wrappedService;

    public TasteJourneyService(RatingRepository ratingRepo, WrappedService wrappedService) {
        this.ratingRepo = ratingRepo;
        this.wrappedService = wrappedService;
    }

    public List<JourneyEntry> build(Long userId) {
        List<Rating> ratings = ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId);
        if (ratings.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<YearMonth> months = new LinkedHashSet<>();
        for (Rating r : ratings) {
            months.add(YearMonth.from(r.getCreatedAt().atZone(ZoneOffset.UTC)));
        }

        List<JourneyEntry> out = new ArrayList<>();
        for (YearMonth month : months) {
            WrappedService.WrappedSummary summary = wrappedService.build(userId, month);
            if (!summary.hasData()) {
                continue;
            }
            out.add(new JourneyEntry(summary.period(), summary.ratingCount(), sentenceFor(summary)));
        }
        return out;
    }

    /** Package-private so tests can hit the sentence logic without faking a full rating history. */
    String sentenceFor(WrappedService.WrappedSummary summary) {
        StringBuilder sb = new StringBuilder();
        sb.append(summary.ratingCount())
          .append(summary.ratingCount() == 1 ? " title rated." : " titles rated.");

        List<WrappedService.TraitShift> notable = summary.topShifts().stream()
                .filter(s -> Math.abs(s.delta()) >= NOTABLE_DELTA)
                .limit(MAX_SHIFTS_NAMED)
                .toList();

        if (notable.isEmpty()) {
            sb.append(" Taste held steady.");
        } else {
            List<String> parts = notable.stream()
                    .map(s -> String.format(java.util.Locale.ROOT, "%s %s %dpts",
                            s.label(), s.delta() >= 0 ? "rose" : "eased", Math.round(Math.abs(s.delta()) * 100)))
                    .toList();
            sb.append(' ').append(String.join(", ", parts)).append('.');
            sb.append(" Reading as ").append(summary.archetype()).append('.');
        }
        return sb.toString();
    }
}

package com.rewatch.service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.rewatch.model.Rating;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitEvent;
import com.rewatch.model.TraitNode;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.TraitEventRepository;

/**
 * Builds a "your month" recap from data that already exists for other views:
 * per-trait movement comes from the same {@link TraitEvent} log that powers
 * {@link HistoryService}'s timeline, and the archetype label reuses
 * {@link ArchetypeService} — Wrapped isn't a new data pipeline, just a
 * calendar-scoped read over the existing one.
 *
 * A trait's start/end value for the period is read off the surrounding
 * TraitEvents rather than stored anywhere: startVal is the `valueBefore` of
 * the first event in the window (or the `valueAfter` of the last event
 * before it, if the trait didn't move this period), and endVal is the
 * `valueAfter` of the last event in (or before) the window. This works
 * because ProfileService.replay() guarantees each event's valueAfter is the
 * authoritative profile value at that instant — see its class comment.
 */
@Service
public class WrappedService {

    public record TraitShift(String key, String label, double startVal, double endVal, double delta) {}

    public record TopRating(Long ratingId, Long titleId, String title, String poster,
                            Integer overall, String moment, Instant createdAt) {}

    public record WrappedSummary(
            String period,
            Instant periodStart,
            Instant periodEnd,
            boolean hasData,
            int ratingCount,
            String archetype,
            String archetypeBlurb,
            List<TraitShift> topShifts,
            List<TopRating> topRatings) {}

    private static final int TOP_SHIFT_COUNT = 3;
    private static final int TOP_RATING_COUNT = 5;

    private final TraitEventRepository traitEventRepo;
    private final RatingRepository ratingRepo;
    private final TitleRepository titleRepo;
    private final ArchetypeService archetypeService;

    public WrappedService(TraitEventRepository traitEventRepo, RatingRepository ratingRepo,
                          TitleRepository titleRepo, ArchetypeService archetypeService) {
        this.traitEventRepo = traitEventRepo;
        this.ratingRepo = ratingRepo;
        this.titleRepo = titleRepo;
        this.archetypeService = archetypeService;
    }

    public WrappedSummary build(Long userId, YearMonth month) {
        Instant periodStart = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant periodEnd = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        String label = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();

        List<TraitEvent> allEvents = traitEventRepo.findByUserIdOrderByOccurredAtAscIdAsc(userId);
        Map<Trait, TraitNode> endProfile = new EnumMap<>(Trait.class);
        List<TraitShift> shifts = new ArrayList<>(Trait.count());

        for (Trait t : Trait.values()) {
            List<TraitEvent> forTrait = allEvents.stream().filter(e -> e.getTrait() == t).toList();

            TraitEvent lastBefore = null;
            List<TraitEvent> inWindow = new ArrayList<>();
            for (TraitEvent e : forTrait) {
                if (e.getOccurredAt().isBefore(periodStart)) {
                    lastBefore = e;
                } else if (e.getOccurredAt().isBefore(periodEnd)) {
                    inWindow.add(e);
                }
            }

            double carryIn = lastBefore == null ? 0.5 : lastBefore.getValueAfter();
            double startVal = inWindow.isEmpty() ? carryIn : inWindow.get(0).getValueBefore();
            double endVal = inWindow.isEmpty() ? carryIn : inWindow.get(inWindow.size() - 1).getValueAfter();

            shifts.add(new TraitShift(t.key(), t.label(), startVal, endVal, endVal - startVal));
            endProfile.put(t, new TraitNode(endVal, 0, 0, 0, periodEnd));
        }

        List<TraitShift> topShifts = shifts.stream()
                .sorted(Comparator.comparingDouble((TraitShift s) -> Math.abs(s.delta())).reversed())
                .limit(TOP_SHIFT_COUNT)
                .toList();

        List<Rating> ratingsInPeriod = ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .filter(r -> !r.getCreatedAt().isBefore(periodStart) && r.getCreatedAt().isBefore(periodEnd))
                .toList();

        Map<Long, Title> titles = titleRepo.findAllById(
                ratingsInPeriod.stream().map(Rating::getTitleId).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Title::getId, t -> t));

        List<TopRating> topRatings = ratingsInPeriod.stream()
                .sorted(Comparator.comparing((Rating r) -> r.getOverall() == null ? 0 : r.getOverall())
                        .reversed()
                        .thenComparing(Comparator.comparing(Rating::getCreatedAt).reversed()))
                .limit(TOP_RATING_COUNT)
                .map(r -> {
                    Title t = titles.get(r.getTitleId());
                    return new TopRating(r.getId(), r.getTitleId(),
                            t == null ? "Unknown title" : t.getTitle(),
                            t == null ? null : t.getPoster(),
                            r.getOverall(), r.getMoment(), r.getCreatedAt());
                })
                .toList();

        boolean hasData = !ratingsInPeriod.isEmpty();
        ArchetypeService.Result archetype = archetypeService.classify(endProfile);

        return new WrappedSummary(label, periodStart, periodEnd, hasData, ratingsInPeriod.size(),
                archetype.archetype(), archetype.blurb(),
                hasData ? topShifts : List.of(), topRatings);
    }
}

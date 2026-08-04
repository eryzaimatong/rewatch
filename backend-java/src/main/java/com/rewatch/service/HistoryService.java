package com.rewatch.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.rewatch.model.Rating;
import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.model.TraitEvent;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.TraitEventRepository;

/**
 * Turns the append-only {@link TraitEvent} log into a chart-ready timeline.
 *
 * Bucketing happens here, at query time, rather than in a stored rollup table —
 * ten events per rating is a trivial row count (roughly 500 rows for a 50-rating
 * user), so there is no volume problem to solve, and doing it at query time means
 * the chart's granularity can change with a query parameter instead of a
 * migration and a backfill job.
 */
@Service
public class HistoryService {

    public enum Granularity { RATING, DAY, WEEK }

    public record Point(String t, double val, double conf) {}
    public record Series(String trait, String label, List<Point> points) {}
    public record Milestone(String t, Long ratingId, Long titleId, String title,
                            Integer stars, List<Map<String, Object>> topShifts) {}
    public record Timeline(Granularity granularity, List<Series> series, List<Milestone> milestones) {}

    private final TraitEventRepository traitEventRepo;
    private final RatingRepository ratingRepo;
    private final TitleRepository titleRepo;

    public HistoryService(TraitEventRepository traitEventRepo, RatingRepository ratingRepo,
                          TitleRepository titleRepo) {
        this.traitEventRepo = traitEventRepo;
        this.ratingRepo = ratingRepo;
        this.titleRepo = titleRepo;
    }

    public Timeline build(Long userId, Granularity granularity, List<Trait> traitsFilter) {
        List<TraitEvent> events = traitEventRepo.findByUserIdOrderByOccurredAtAscIdAsc(userId);

        List<Trait> traits = (traitsFilter == null || traitsFilter.isEmpty())
                ? List.of(Trait.values())
                : traitsFilter;

        List<Series> series = new ArrayList<>();
        for (Trait t : traits) {
            List<TraitEvent> forTrait = events.stream().filter(e -> e.getTrait() == t).toList();
            series.add(new Series(t.key(), t.label(), bucket(forTrait, granularity)));
        }

        List<Milestone> milestones = buildMilestones(userId, events);

        return new Timeline(granularity, series, milestones);
    }

    private List<Point> bucket(List<TraitEvent> events, Granularity g) {
        if (g == Granularity.RATING) {
            return events.stream()
                    .map(e -> new Point(e.getOccurredAt().toString(), e.getValueAfter(), e.getConfidenceAfter()))
                    .toList();
        }

        // last-value-forward-fill per bucket key
        Map<String, TraitEvent> lastInBucket = new TreeMap<>();
        for (TraitEvent e : events) {
            lastInBucket.put(bucketKey(e.getOccurredAt(), g), e);
        }
        List<Point> out = new ArrayList<>(lastInBucket.size());
        for (Map.Entry<String, TraitEvent> entry : lastInBucket.entrySet()) {
            TraitEvent e = entry.getValue();
            out.add(new Point(entry.getKey(), e.getValueAfter(), e.getConfidenceAfter()));
        }
        return out;
    }

    private String bucketKey(Instant at, Granularity g) {
        LocalDate date = at.atZone(ZoneOffset.UTC).toLocalDate();
        if (g == Granularity.WEEK) {
            int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
            int year = date.get(WeekFields.ISO.weekBasedYear());
            return String.format(Locale.ROOT, "%04d-W%02d", year, week);
        }
        return date.toString();
    }

    /**
     * One entry per rating, naming what moved most — the payload that turns the
     * chart from "a line went up" into "you rated Parasite 5* and Bittersweet rose".
     */
    private List<Milestone> buildMilestones(Long userId, List<TraitEvent> events) {
        Map<Long, List<TraitEvent>> byRating = new LinkedHashMap<>();
        for (TraitEvent e : events) {
            if (e.getSourceType() != TraitEvent.Source.RATING || e.getSourceRatingId() == null) {
                continue;
            }
            byRating.computeIfAbsent(e.getSourceRatingId(), k -> new ArrayList<>()).add(e);
        }
        if (byRating.isEmpty()) {
            return List.of();
        }

        Map<Long, Rating> ratings = new LinkedHashMap<>();
        for (Rating r : ratingRepo.findByUserIdOrderByCreatedAtAscIdAsc(userId)) {
            ratings.put(r.getId(), r);
        }
        Map<Long, Title> titles = new LinkedHashMap<>();
        for (Title t : titleRepo.findAllById(ratings.values().stream().map(Rating::getTitleId).distinct().toList())) {
            titles.put(t.getId(), t);
        }

        List<Milestone> out = new ArrayList<>();
        for (Map.Entry<Long, List<TraitEvent>> entry : byRating.entrySet()) {
            Rating r = ratings.get(entry.getKey());
            if (r == null) {
                continue;
            }
            Title title = titles.get(r.getTitleId());

            List<TraitEvent> shifts = entry.getValue();
            List<Map<String, Object>> top = shifts.stream()
                    .sorted((a, b) -> Double.compare(Math.abs(b.getDelta()), Math.abs(a.getDelta())))
                    .limit(2)
                    .map(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("trait", e.getTrait().key());
                        m.put("label", e.getTrait().label());
                        m.put("delta", e.getDelta());
                        return m;
                    })
                    .toList();

            out.add(new Milestone(
                    r.getCreatedAt().toString(),
                    r.getId(),
                    r.getTitleId(),
                    title == null ? "Unknown title" : title.getTitle(),
                    r.getOverall(),
                    top));
        }
        return out;
    }
}

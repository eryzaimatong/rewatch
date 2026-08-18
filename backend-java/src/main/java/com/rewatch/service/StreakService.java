package com.rewatch.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.rewatch.model.Rating;
import com.rewatch.repository.RatingRepository;

/**
 * A "watch streak" — consecutive calendar days with at least one rating —
 * computed fresh from Rating.createdAt on every call rather than a
 * persisted counter. No per-user timezone is stored anywhere in this app,
 * so streaks bucket by UTC calendar day; this is a real, documented
 * tradeoff, not an oversight — a user near a UTC day boundary could see
 * their streak roll over at a time that doesn't match their local
 * midnight. Computing it fresh (rather than incrementing/resetting a
 * stored counter on each rating) means it can never drift from the actual
 * rating history and self-heals if ratings are ever deleted or backfilled.
 */
@Service
public class StreakService {

    public record Streak(int current, int longest, boolean activeToday) {}

    private final RatingRepository ratingRepo;

    public StreakService(RatingRepository ratingRepo) {
        this.ratingRepo = ratingRepo;
    }

    public Streak compute(Long userId) {
        List<Rating> ratings = ratingRepo.findByUserIdOrderByCreatedAtDescIdDesc(userId);
        if (ratings.isEmpty()) {
            return new Streak(0, 0, false);
        }

        // TreeSet: distinct calendar days (a rewatch or a second rating the same
        // day shouldn't count twice), sorted ascending for the longest-streak scan.
        TreeSet<LocalDate> days = new TreeSet<>();
        for (Rating r : ratings) {
            days.add(toUtcDate(r.getCreatedAt()));
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate mostRecent = days.last();

        boolean activeToday = mostRecent.equals(today);
        // A streak survives one day of grace (rated yesterday, nothing yet
        // today) — the count itself doesn't include today until it actually
        // has a rating, but the streak isn't considered broken just because
        // today hasn't happened yet.
        int current = 0;
        if (!mostRecent.isBefore(today.minusDays(1))) {
            current = 1;
            LocalDate cursor = mostRecent;
            while (days.contains(cursor.minusDays(1))) {
                current++;
                cursor = cursor.minusDays(1);
            }
        }

        int longest = 0;
        int running = 0;
        LocalDate prev = null;
        for (LocalDate day : days) {
            running = (prev != null && prev.plusDays(1).equals(day)) ? running + 1 : 1;
            longest = Math.max(longest, running);
            prev = day;
        }

        return new Streak(current, longest, activeToday);
    }

    private LocalDate toUtcDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}

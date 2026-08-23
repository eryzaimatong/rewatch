package com.rewatch.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.rewatch.dto.MovieDTO;
import com.rewatch.model.DailyGuess;
import com.rewatch.model.Title;
import com.rewatch.repository.DailyGuessRepository;
import com.rewatch.repository.TitleRepository;

/**
 * "Daily Double Feature" — the Wordle-style daily ritual. Two titles, the
 * same for everyone on a given UTC calendar day (seeded from the date
 * itself, not stored anywhere until someone actually plays — see
 * puzzleFor()), and a simple question: which one is YOUR real better match
 * tonight? Unlike Wordle, the *answer* is personal — it's whichever title
 * actually scores higher against your own TasteDNA, via the same
 * Recommender.scoreForUser every match card and Discovery row already uses
 * — so guessing right is a genuine, real signal about how well you know
 * your own taste, not a shared trivia answer.
 */
@Service
public class DailyChallengeService {

    private static final int MIN_VOTE_COUNT = 150;

    private final TitleRepository titleRepo;
    private final DailyGuessRepository dailyGuessRepo;
    private final Recommender recommender;

    public DailyChallengeService(TitleRepository titleRepo, DailyGuessRepository dailyGuessRepo,
                                 Recommender recommender) {
        this.titleRepo = titleRepo;
        this.dailyGuessRepo = dailyGuessRepo;
        this.recommender = recommender;
    }

    public record Puzzle(Title titleA, Title titleB, LocalDate date) {}
    public record TodayView(Title titleA, Title titleB, LocalDate date,
                             boolean alreadyPlayed, DailyGuess previousGuess,
                             int currentStreak, int longestStreak) {}
    public record GuessOutcome(boolean correct, double titleAScore, double titleBScore,
                                int currentStreak, int longestStreak) {}

    /**
     * Deterministic from the date alone — same two titles for every user
     * calling this on the same UTC calendar day, no persistence needed for
     * the puzzle itself. The candidate pool is sorted by id (not popularity
     * or any other order that could quietly change between calls) so the
     * seeded pick is genuinely stable within a day.
     */
    public Puzzle puzzleFor(LocalDate date) {
        List<Title> pool = titleRepo.findAll().stream()
                .filter(t -> t.getSynopsis() != null && !t.getSynopsis().isBlank())
                .filter(t -> t.getPoster() != null && !t.getPoster().isBlank())
                .filter(t -> t.getVoteCount() != null && t.getVoteCount() >= MIN_VOTE_COUNT)
                .sorted(Comparator.comparing(Title::getId))
                .toList();
        if (pool.size() < 2) {
            return null;
        }

        Random rnd = new Random(date.toEpochDay());
        int idxA = rnd.nextInt(pool.size());
        int idxB;
        do {
            idxB = rnd.nextInt(pool.size());
        } while (idxB == idxA);

        return new Puzzle(pool.get(idxA), pool.get(idxB), date);
    }

    public TodayView today(Long userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Puzzle puzzle = puzzleFor(today);
        if (puzzle == null) {
            return null;
        }

        DailyGuess previous = dailyGuessRepo.findByUserIdAndPlayDate(userId, today).orElse(null);
        Streak streak = computeStreak(userId);

        return new TodayView(puzzle.titleA(), puzzle.titleB(), today,
                previous != null, previous, streak.current(), streak.longest());
    }

    /** @return null if the user already played today, or chosenTitleId isn't one of today's two titles. */
    public GuessOutcome guess(Long userId, Long chosenTitleId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (dailyGuessRepo.findByUserIdAndPlayDate(userId, today).isPresent()) {
            return null;
        }

        Puzzle puzzle = puzzleFor(today);
        if (puzzle == null
                || (!puzzle.titleA().getId().equals(chosenTitleId) && !puzzle.titleB().getId().equals(chosenTitleId))) {
            return null;
        }

        MovieDTO scoredA = recommender.scoreForUser(puzzle.titleA(), userId);
        MovieDTO scoredB = recommender.scoreForUser(puzzle.titleB(), userId);
        boolean chosenIsA = puzzle.titleA().getId().equals(chosenTitleId);
        double chosenScore = chosenIsA ? scoredA.getMatchScore() : scoredB.getMatchScore();
        double otherScore = chosenIsA ? scoredB.getMatchScore() : scoredA.getMatchScore();
        boolean correct = chosenScore >= otherScore;

        DailyGuess row = new DailyGuess();
        row.setUserId(userId);
        row.setPlayDate(today);
        row.setTitleAId(puzzle.titleA().getId());
        row.setTitleBId(puzzle.titleB().getId());
        row.setChosenTitleId(chosenTitleId);
        row.setCorrect(correct);
        row.setCreatedAt(Instant.now());
        dailyGuessRepo.save(row);

        Streak streak = computeStreak(userId);
        return new GuessOutcome(correct, scoredA.getMatchScore(), scoredB.getMatchScore(),
                streak.current(), streak.longest());
    }

    private record Streak(int current, int longest) {}

    /**
     * Same "compute fresh from history, never a stored counter" philosophy as
     * StreakService — a played (not necessarily *correct*) day keeps the
     * streak alive, mirroring "showing up counts." Bucketed by UTC calendar
     * day for the same documented reason StreakService is.
     */
    private Streak computeStreak(Long userId) {
        List<DailyGuess> guesses = dailyGuessRepo.findByUserIdOrderByPlayDateDesc(userId);
        if (guesses.isEmpty()) {
            return new Streak(0, 0);
        }

        TreeSet<LocalDate> days = new TreeSet<>();
        for (DailyGuess g : guesses) {
            days.add(g.getPlayDate());
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate mostRecent = days.last();

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

        return new Streak(current, longest);
    }
}

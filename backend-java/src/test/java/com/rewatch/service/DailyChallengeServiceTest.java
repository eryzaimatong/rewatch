package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rewatch.dto.MovieDTO;
import com.rewatch.model.DailyGuess;
import com.rewatch.model.Title;
import com.rewatch.repository.DailyGuessRepository;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.repository.UserRepository;

/**
 * Covers DailyChallengeService — the "Daily Double Feature" ritual.
 * Recommender is subclassed (Mockito can't mock it here — see other tests
 * in this package) with scoreForUser overridden to hand back controllable
 * scores, since exercising the real scorer needs ProfileService/
 * ScoringService this test has no reason to stand up.
 */
@ExtendWith(MockitoExtension.class)
class DailyChallengeServiceTest {

    @Mock private TitleRepository titleRepo;
    @Mock private DailyGuessRepository dailyGuessRepo;
    @Mock private RatingRepository ratingRepo;
    @Mock private UserRepository userRepo;

    private static class FakeRecommender extends Recommender {
        private final Map<Long, Double> scoresByTitleId;

        FakeRecommender(TitleRepository titleRepo, RatingRepository ratingRepo, UserRepository userRepo,
                        Map<Long, Double> scoresByTitleId) {
            super(titleRepo, ratingRepo, userRepo, null, null);
            this.scoresByTitleId = scoresByTitleId;
        }

        @Override
        public MovieDTO scoreForUser(Title title, Long userId) {
            MovieDTO dto = new MovieDTO();
            dto.setTitleId(title.getId());
            dto.setMatchScore(scoresByTitleId.getOrDefault(title.getId(), 50.0));
            return dto;
        }
    }

    private DailyChallengeService newService(Map<Long, Double> scores) {
        return new DailyChallengeService(titleRepo, dailyGuessRepo,
                new FakeRecommender(titleRepo, ratingRepo, userRepo, scores));
    }

    private long nextId = 1;

    private Title title(int voteCount) {
        Title t = new Title();
        t.setId(nextId++);
        t.setTitle("Title " + t.getId());
        t.setSynopsis("A real synopsis.");
        t.setPoster("/poster.jpg");
        t.setVoteCount(voteCount);
        return t;
    }

    private List<Title> pool(int size) {
        List<Title> titles = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            titles.add(title(500));
        }
        return titles;
    }

    @Test
    void puzzleForIsDeterministicForTheSameDate() {
        when(titleRepo.findAll()).thenReturn(pool(30));
        DailyChallengeService service = newService(Map.of());
        LocalDate date = LocalDate.of(2026, 6, 15);

        DailyChallengeService.Puzzle first = service.puzzleFor(date);
        DailyChallengeService.Puzzle second = service.puzzleFor(date);

        assertEquals(first.titleA().getId(), second.titleA().getId());
        assertEquals(first.titleB().getId(), second.titleB().getId());
        assertNotEquals(first.titleA().getId(), first.titleB().getId());
    }

    @Test
    void puzzleExcludesTitlesBelowTheVoteFloor() {
        List<Title> titles = pool(10);
        Title lowVotes = title(3);
        titles.add(lowVotes);
        when(titleRepo.findAll()).thenReturn(titles);

        DailyChallengeService.Puzzle puzzle = newService(Map.of()).puzzleFor(LocalDate.of(2026, 1, 1));

        assertNotEquals(lowVotes.getId(), puzzle.titleA().getId());
        assertNotEquals(lowVotes.getId(), puzzle.titleB().getId());
    }

    @Test
    void nullPuzzleWhenPoolIsTooSmall() {
        when(titleRepo.findAll()).thenReturn(pool(1));

        assertNull(newService(Map.of()).puzzleFor(LocalDate.of(2026, 1, 1)));
    }

    @Test
    void todayReflectsNoPreviousPlay() {
        when(titleRepo.findAll()).thenReturn(pool(10));
        when(dailyGuessRepo.findByUserIdAndPlayDate(1L, LocalDate.now(ZoneOffset.UTC))).thenReturn(Optional.empty());
        when(dailyGuessRepo.findByUserIdOrderByPlayDateDesc(1L)).thenReturn(List.of());

        DailyChallengeService.TodayView view = newService(Map.of()).today(1L);

        assertFalse(view.alreadyPlayed());
        assertEquals(0, view.currentStreak());
    }

    @Test
    void guessRejectsATitleIdNotInTodaysPuzzle() {
        when(titleRepo.findAll()).thenReturn(pool(10));
        when(dailyGuessRepo.findByUserIdAndPlayDate(1L, LocalDate.now(ZoneOffset.UTC))).thenReturn(Optional.empty());

        DailyChallengeService.GuessOutcome outcome = newService(Map.of()).guess(1L, 99999L);

        assertNull(outcome);
    }

    @Test
    void guessRejectsASecondPlayTheSameDay() {
        when(dailyGuessRepo.findByUserIdAndPlayDate(1L, LocalDate.now(ZoneOffset.UTC)))
                .thenReturn(Optional.of(new DailyGuess()));

        DailyChallengeService.GuessOutcome outcome = newService(Map.of()).guess(1L, 1L);

        assertNull(outcome);
    }

    @Test
    void guessIsCorrectWhenChosenTitleScoresHigher() {
        List<Title> titles = pool(10);
        when(titleRepo.findAll()).thenReturn(titles);
        when(dailyGuessRepo.findByUserIdAndPlayDate(1L, LocalDate.now(ZoneOffset.UTC))).thenReturn(Optional.empty());
        when(dailyGuessRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(dailyGuessRepo.findByUserIdOrderByPlayDateDesc(1L)).thenReturn(List.of());

        DailyChallengeService.Puzzle puzzle = newService(Map.of()).puzzleFor(LocalDate.now(ZoneOffset.UTC));
        Map<Long, Double> scores = Map.of(puzzle.titleA().getId(), 80.0, puzzle.titleB().getId(), 20.0);

        DailyChallengeService.GuessOutcome outcome = newService(scores).guess(1L, puzzle.titleA().getId());

        assertTrue(outcome.correct());
    }

    @Test
    void guessIsIncorrectWhenChosenTitleScoresLower() {
        List<Title> titles = pool(10);
        when(titleRepo.findAll()).thenReturn(titles);
        when(dailyGuessRepo.findByUserIdAndPlayDate(1L, LocalDate.now(ZoneOffset.UTC))).thenReturn(Optional.empty());
        when(dailyGuessRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(dailyGuessRepo.findByUserIdOrderByPlayDateDesc(1L)).thenReturn(List.of());

        DailyChallengeService.Puzzle puzzle = newService(Map.of()).puzzleFor(LocalDate.now(ZoneOffset.UTC));
        Map<Long, Double> scores = Map.of(puzzle.titleA().getId(), 20.0, puzzle.titleB().getId(), 80.0);

        DailyChallengeService.GuessOutcome outcome = newService(scores).guess(1L, puzzle.titleA().getId());

        assertFalse(outcome.correct());
    }

    @Test
    void streakCountsConsecutiveCalendarDaysIncludingToday() {
        when(titleRepo.findAll()).thenReturn(pool(10));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(dailyGuessRepo.findByUserIdAndPlayDate(1L, today)).thenReturn(Optional.empty());

        List<DailyGuess> history = new ArrayList<>();
        history.add(playedOn(today.minusDays(1)));
        history.add(playedOn(today.minusDays(2)));
        // A gap before this — shouldn't extend the current streak, but does count toward longest.
        history.add(playedOn(today.minusDays(10)));
        when(dailyGuessRepo.findByUserIdOrderByPlayDateDesc(1L)).thenReturn(history);

        DailyChallengeService.TodayView view = newService(Map.of()).today(1L);

        assertEquals(2, view.currentStreak(), "yesterday + the day before, today not played yet");
        assertEquals(2, view.longestStreak());
    }

    private DailyGuess playedOn(LocalDate date) {
        DailyGuess g = new DailyGuess();
        g.setUserId(1L);
        g.setPlayDate(date);
        g.setCreatedAt(Instant.now());
        return g;
    }
}

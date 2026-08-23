package com.rewatch.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.dto.DailyGuessRequest;
import com.rewatch.model.DailyGuess;
import com.rewatch.model.Title;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.DailyChallengeService;

/** "Daily Double Feature" — see DailyChallengeService's own doc comment for the mechanic. */
@RestController
@RequestMapping("/api/daily")
public class DailyChallengeController {

    private final DailyChallengeService dailyChallengeService;

    public DailyChallengeController(DailyChallengeService dailyChallengeService) {
        this.dailyChallengeService = dailyChallengeService;
    }

    private Map<String, Object> titleCard(Title t) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("titleId", t.getId());
        m.put("title", t.getTitle());
        m.put("year", t.getYear());
        m.put("posterPath", t.getPoster());
        return m;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> today(@PathVariable Long userId, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        DailyChallengeService.TodayView view = dailyChallengeService.today(userId);
        if (view == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "error", "message", "Not enough of the catalog is ready for today's pick yet."));
        }

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("date", view.date().toString());
        body.put("titleA", titleCard(view.titleA()));
        body.put("titleB", titleCard(view.titleB()));
        body.put("alreadyPlayed", view.alreadyPlayed());
        body.put("currentStreak", view.currentStreak());
        body.put("longestStreak", view.longestStreak());
        if (view.previousGuess() != null) {
            DailyGuess g = view.previousGuess();
            body.put("previousGuess", Map.of(
                    "chosenTitleId", g.getChosenTitleId(),
                    "correct", g.isCorrect()));
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/guess")
    public ResponseEntity<?> guess(@Valid @RequestBody DailyGuessRequest req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        DailyChallengeService.GuessOutcome outcome = dailyChallengeService.guess(req.getUserId(), req.getTitleId());
        if (outcome == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "error",
                    "message", "Already played today, or that title isn't one of today's two."));
        }
        return ResponseEntity.ok(Map.of(
                "correct", outcome.correct(),
                "titleAScore", outcome.titleAScore(),
                "titleBScore", outcome.titleBScore(),
                "currentStreak", outcome.currentStreak(),
                "longestStreak", outcome.longestStreak()));
    }
}

package com.rewatch.controller;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.dto.CompatibilityRequests.CheckCompatibility;
import com.rewatch.model.Title;
import com.rewatch.security.RateLimiterService;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.CompatibilityService;
import com.rewatch.service.CompatibilityService.QuizResponse;

/**
 * The no-signup half of the social loop: "how compatible is your taste with
 * a friend's" for a visitor who doesn't have (and doesn't need) an account.
 * Both routes are public on purpose — SecurityConfig permits them alongside
 * the other logged-out-browsing GETs — so the whole flow is one shared link,
 * zero friction. Rate-limited by IP since /check is real, unauthenticated
 * compute work.
 */
@RestController
@RequestMapping("/api/compatibility")
public class CompatibilityController {

    // This is the share loop's entire mechanic — a link opened in a group
    // chat can produce hundreds of checks from one carrier-NAT gateway in an
    // hour. A cosine-similarity computation over ~10 traits is cheap to
    // serve; raised from 20 so the limiter bounds a scripted single-IP
    // abuser without ever being what kills a real viral moment.
    private static final int MAX_CHECKS_PER_HOUR = 500;

    private final CompatibilityService compatibilityService;
    private final RateLimiterService rateLimiter;

    public CompatibilityController(CompatibilityService compatibilityService, RateLimiterService rateLimiter) {
        this.compatibilityService = compatibilityService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/quiz")
    public List<Map<String, Object>> quiz() {
        return compatibilityService.quiz().stream().map(this::quizCard).toList();
    }

    private Map<String, Object> quizCard(Title t) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("titleId", t.getId());
        m.put("title", t.getTitle());
        m.put("year", t.getYear());
        m.put("posterPath", t.getPoster());
        return m;
    }

    @PostMapping("/check")
    public ResponseEntity<?> check(@Valid @RequestBody CheckCompatibility req, HttpServletRequest request) {
        String key = "compatibility:" + SecurityUtil.clientIp(request);
        if (!rateLimiter.allow(key, MAX_CHECKS_PER_HOUR, Duration.ofHours(1))) {
            return rateLimiter.tooManyRequests(key, Duration.ofHours(1), "Too many compatibility checks from your network.");
        }

        List<QuizResponse> responses = req.getResponses().stream()
                .map(a -> new QuizResponse(a.getTitleId(), a.getOverall()))
                .toList();

        CompatibilityService.CompatibilityResult result = compatibilityService.check(req.getTargetUsername(), responses);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", "error",
                    "message", "Can't compare with that profile — it may not exist, may be private, "
                            + "or may not have enough ratings of its own yet."));
        }
        return ResponseEntity.ok(result);
    }
}

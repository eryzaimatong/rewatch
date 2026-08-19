package com.rewatch.controller;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.rewatch.dto.MovieDTO;
import com.rewatch.dto.OnboardingRequest;
import com.rewatch.dto.RatingDTO;
import com.rewatch.features.KeywordLexicon;
import com.rewatch.model.Title;
import com.rewatch.model.TraitVector;
import com.rewatch.repository.TitleRepository;
import com.rewatch.security.RateLimiterService;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.AchievementService;
import com.rewatch.service.NotificationService;
import com.rewatch.service.OnboardingService;
import com.rewatch.service.ProfileService;
import com.rewatch.service.RatingService;
import com.rewatch.service.TmdbService;

@RestController
@RequestMapping("/api/movies")
public class TmdbController {

    private final TmdbService tmdbservice;
    private final OnboardingService onboardingService;
    private final ProfileService profileService;
    private final RatingService ratingService;
    private final TitleRepository titleRepo;
    private final AchievementService achievementService;
    private final NotificationService notificationService;
    private final RateLimiterService rateLimiter;

    // These GET routes are permitAll in SecurityConfig (anonymous browsing needs
    // to work), which means they're this controller's only unauthenticated
    // surface — worth bounding per-IP so a script can't hammer the local catalog
    // with unbounded requests just because no login is required. Generous enough
    // that a real browsing/typing session never gets near it.
    private static final int MAX_PUBLIC_REQUESTS_PER_MINUTE = 120;

    public TmdbController(TmdbService tmdbservice, OnboardingService onboardingService,
                          ProfileService profileService, RatingService ratingService,
                          TitleRepository titleRepo, AchievementService achievementService,
                          NotificationService notificationService, RateLimiterService rateLimiter) {
        this.tmdbservice = tmdbservice;
        this.onboardingService = onboardingService;
        this.profileService = profileService;
        this.ratingService = ratingService;
        this.titleRepo = titleRepo;
        this.achievementService = achievementService;
        this.notificationService = notificationService;
        this.rateLimiter = rateLimiter;
    }

    private void checkPublicRateLimit(HttpServletRequest request) {
        String key = "movies-public:" + SecurityUtil.clientIp(request);
        if (!rateLimiter.allow(key, MAX_PUBLIC_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down.");
        }
    }

    /**
     * The main feed. Being logged in personalizes it; anonymous requests get a
     * neutral, quality-weighted view. The personalizing id comes from the JWT, not
     * a client-supplied query param — a permitAll route that trusted `?userId=`
     * would let anyone read anyone's personalized feed without ever logging in.
     * Reads only the local catalog — see TmdbService for why.
     */
    @GetMapping("/popular")
    public List<MovieDTO> getpopular(@RequestParam(required = false) String genre,
                                     @RequestParam(required = false) String language,
                                     @RequestParam(required = false) String vibe,
                                     Authentication authentication, HttpServletRequest request) {
        checkPublicRateLimit(request);
        return tmdbservice.getpopular(SecurityUtil.currentUserId(authentication), genre, language, vibe);
    }

    @GetMapping("/search")
    public List<MovieDTO> search(@RequestParam("query") String query, Authentication authentication, HttpServletRequest request) {
        checkPublicRateLimit(request);
        return tmdbservice.searchmovies(query, SecurityUtil.currentUserId(authentication));
    }

    @GetMapping("/nlp-search")
    public List<MovieDTO> nlpsearch(@RequestParam("query") String query, Authentication authentication, HttpServletRequest request) {
        checkPublicRateLimit(request);
        return tmdbservice.nlpsearch(query, SecurityUtil.currentUserId(authentication));
    }

    /** Additive — doesn't change nlp-search's existing response shape. See TmdbService.understand. */
    @GetMapping("/nlp-search/understand")
    public Map<String, Object> understand(@RequestParam("query") String query, HttpServletRequest request) {
        checkPublicRateLimit(request);
        return Map.of("understood", tmdbservice.understand(query));
    }

    /**
     * Autocomplete suggestions — a mix of mood phrases (drawn from the same
     * lexicon that derives movie vectors, so any suggestion the user picks is
     * guaranteed to mean something to the scorer) and matching title names.
     * Deliberately not labelled "AI": it's a local phrase lexicon, no LLM call.
     */
    @GetMapping("/search-suggestions")
    public List<Map<String, String>> searchSuggestions(@RequestParam("query") String query, HttpServletRequest request) {
        checkPublicRateLimit(request);
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        String needle = query.trim().toLowerCase();

        List<Map<String, String>> out = new java.util.ArrayList<>();
        for (String phrase : KeywordLexicon.suggestPhrases(needle, 4)) {
            out.add(Map.of("type", "mood", "text", phrase));
        }
        for (Title t : titleRepo.findAll()) {
            if (out.size() >= 8) break;
            if (t.getTitle() != null && t.getTitle().toLowerCase().contains(needle)) {
                out.add(Map.of("type", "title", "text", t.getTitle()));
            }
        }
        return out;
    }

    @GetMapping("/trending")
    public List<MovieDTO> gettrending(Authentication authentication, HttpServletRequest request) {
        checkPublicRateLimit(request);
        return tmdbservice.getTrending(SecurityUtil.currentUserId(authentication));
    }

    @GetMapping("/top-rated")
    public List<MovieDTO> gettoprated(Authentication authentication, HttpServletRequest request) {
        checkPublicRateLimit(request);
        return tmdbservice.getTopRated(SecurityUtil.currentUserId(authentication));
    }

    @PostMapping("/onboard")
    public ResponseEntity<?> onboard(@Valid @RequestBody OnboardingRequest req, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, req.getUserId());
        Map<String, String> unlockedBefore = achievementService.unlockedTitles(req.getUserId());
        TraitVector seed = onboardingService.deriveSeed(req);
        profileService.seedFromOnboarding(req.getUserId(), seed, req.getAvoid());
        achievementService.unlockedTitles(req.getUserId()).forEach((key, title) -> {
            if (!unlockedBefore.containsKey(key)) {
                notificationService.notifyAchievementUnlocked(req.getUserId(), title);
            }
        });

        Map<String, Object> res = new HashMap<>();
        res.put("status", "success");
        res.put("message", "Taste profile seeded from your favourites and preferences.");
        res.put("seededVector", seed.toKeyedMap());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/rate")
    public ResponseEntity<?> rate(@Valid @RequestBody RatingDTO dto, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, dto.getUserId());
        try {
            RatingService.Result result = ratingService.submit(dto);

            List<Map<String, Object>> shifts = result.shifts().stream()
                    .map(s -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("trait", s.trait().key());
                        m.put("label", s.trait().label());
                        m.put("before", s.before());
                        m.put("after", s.after());
                        m.put("delta", s.delta());
                        m.put("confidence", s.confidence());
                        return m;
                    })
                    .toList();

            var topShift = result.shifts().stream()
                    .max((a, b) -> Double.compare(Math.abs(a.delta()), Math.abs(b.delta())))
                    .orElse(null);

            Map<String, Object> res = new HashMap<>();
            res.put("status", "success");
            res.put("ratingId", result.rating().getId());
            res.put("titleId", result.rating().getTitleId());
            res.put("shifts", shifts);
            res.put("isRewatch", result.isRewatch());
            res.put("watchNumber", result.watchNumber());
            if (result.previousOverall() != null) {
                res.put("previousOverall", result.previousOverall());
            }
            if (!result.newlyUnlockedAchievements().isEmpty()) {
                res.put("newlyUnlockedAchievements", result.newlyUnlockedAchievements());
            }
            if (topShift != null) {
                Map<String, Object> top = new HashMap<>();
                top.put("trait", topShift.trait().key());
                top.put("label", topShift.trait().label());
                top.put("delta", topShift.delta());
                res.put("topShift", top);
                res.put("message", String.format("%s %+.0f%% points",
                        topShift.trait().label(), topShift.delta() * 100));
            } else {
                res.put("message", "Rating saved.");
            }
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * There was previously no way to remove a rating at all — see
     * RatingService.deleteRating's comment. userId comes from a query param
     * (DELETE requests don't carry a body here, same pattern as
     * WatchlistController.removeItem/deleteFolder) but is only ever trusted
     * once requireSelf confirms it matches the authenticated caller.
     */
    @DeleteMapping("/ratings/{ratingId}")
    public ResponseEntity<?> deleteRating(@PathVariable Long ratingId, @RequestParam Long userId,
                                          Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        boolean removed = ratingService.deleteRating(userId, ratingId);
        if (!removed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Rating not found"));
        }
        return ResponseEntity.ok(Map.of("status", "success"));
    }
}

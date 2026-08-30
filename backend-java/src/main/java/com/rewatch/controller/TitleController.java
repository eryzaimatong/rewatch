package com.rewatch.controller;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.rewatch.dto.MovieDTO;
import com.rewatch.dto.TitlePickerDTO;
import com.rewatch.model.Title;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;
import com.rewatch.security.RateLimiterService;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.Recommender;
import com.rewatch.service.TmdbClient;

@RestController
@RequestMapping("/api/titles")
public class TitleController {

    /** Same audience-signal floor as Recommender.candidatePool, applied here per-type. */
    private static final int MIN_VOTE_COUNT = 150;

    /**
     * A type bucket only gets biased toward well-known titles if enough of them
     * exist to still be worth browsing — anime/kdrama are much thinner than
     * movies, and a threshold tuned for a 1000+ title catalog would otherwise
     * wipe out a 22-title one. Same graceful-fallback shape as candidatePool.
     */
    private static final int MIN_WELLKNOWN_PER_TYPE = 10;

    /** GET /api/titles is the one permitAll route on this controller — same anonymous-abuse reasoning as TmdbController's public routes. */
    private static final int MAX_PUBLIC_REQUESTS_PER_MINUTE = 120;

    /** Must match onboardingUtils.js's FAV_PAGE_SIZE — a page here is exactly a page there. */
    private static final int FAV_PAGE_SIZE = 60;

    private final TitleRepository titleRepository;
    private final Recommender recommender;
    private final TmdbClient tmdbClient;
    private final RateLimiterService rateLimiter;
    private final RatingRepository ratingRepository;

    public TitleController(TitleRepository titleRepository, Recommender recommender, TmdbClient tmdbClient,
                           RateLimiterService rateLimiter, RatingRepository ratingRepository) {
        this.titleRepository = titleRepository;
        this.recommender = recommender;
        this.tmdbClient = tmdbClient;
        this.rateLimiter = rateLimiter;
        this.ratingRepository = ratingRepository;
    }

    /**
     * The full browsable catalog — onboarding's favourites picker and search's
     * no-personalization fallback both read this raw. A title with no synopsis
     * or poster is broken data regardless of context, dropped unconditionally;
     * near-zero-vote titles (an ingestion side effect of broad keyword sweeps —
     * e.g. two dozen unrelated "Avatar"-titled documentaries and fan works
     * alongside the three real Avatar films) are biased out per-type, never at
     * the cost of emptying a genuinely thin type bucket.
     */
    @GetMapping
    public List<Title> getAllTitles(HttpServletRequest request) {
        String key = "titles-public:" + SecurityUtil.clientIp(request);
        if (!rateLimiter.allow(key, MAX_PUBLIC_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down.");
        }
        Map<String, List<Title>> byType = titleRepository.findAll().stream()
                .filter(this::hasRenderableData)
                .collect(Collectors.groupingBy(Title::getType));

        List<Title> out = new ArrayList<>();
        byType.forEach((type, titles) -> {
            List<Title> wellKnown = titles.stream()
                    .filter(t -> t.getVoteCount() != null && t.getVoteCount() >= MIN_VOTE_COUNT)
                    .toList();
            out.addAll(wellKnown.size() >= MIN_WELLKNOWN_PER_TYPE ? wellKnown : titles);
        });
        return out;
    }

    private boolean hasRenderableData(Title t) {
        return t.getSynopsis() != null && !t.getSynopsis().isBlank()
                && t.getPoster() != null && !t.getPoster().isBlank();
    }

    /**
     * Onboarding's favourites picker, specifically — not a general-purpose
     * replacement for GET /api/titles (MovieFeed's no-personalization
     * fallback still needs that one's richer fields). Same renderable-data
     * and well-known-per-bucket-with-thin-bucket-fallback filtering as
     * getAllTitles above, but pushed into the query and paged to
     * FAV_PAGE_SIZE instead of shipping all ~6,000 rows for the frontend to
     * filter down to 60 (see onboardingUtils.js's own FAV_PAGE_SIZE, which
     * this mirrors so a page here is exactly a page there).
     *
     * `selected` — titles the caller already has picked — are always
     * included in the response even if the page/search would otherwise cut
     * them off, matching computeVisibleFavTitles' pickedButCutOff behaviour
     * on the client.
     */
    @GetMapping("/picker")
    public List<TitlePickerDTO> picker(@RequestParam String bucket,
                                       @RequestParam(defaultValue = "") String search,
                                       @RequestParam(defaultValue = "") List<String> selected) {
        String q = search.trim().toLowerCase();
        PageRequest page = PageRequest.of(0, FAV_PAGE_SIZE);

        Page<Title> wellKnown = titleRepository.findWellKnownForPicker(bucket, MIN_VOTE_COUNT, q, page);
        List<Title> results = new ArrayList<>(
                wellKnown.getTotalElements() >= MIN_WELLKNOWN_PER_TYPE
                        ? wellKnown.getContent()
                        : titleRepository.findAllForPicker(bucket, q, page).getContent());

        if (!selected.isEmpty()) {
            Set<String> present = results.stream().map(Title::getTitle).collect(Collectors.toSet());
            List<String> missing = selected.stream().filter(t -> !present.contains(t)).toList();
            if (!missing.isEmpty()) {
                results.addAll(titleRepository.findByTitleIn(missing));
            }
        }

        return results.stream().map(TitlePickerDTO::from).toList();
    }

    /**
     * The explanation block for one title, on demand. This is what MovieModal
     * should call instead of the old `movie.matchScore || 85` fallback.
     */
    @GetMapping("/{id}/match")
    public ResponseEntity<?> match(@PathVariable Long id, @RequestParam Long userId,
                                   Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        Title title = titleRepository.findById(id).orElse(null);
        if (title == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Unknown title id " + id));
        }
        MovieDTO dto = recommender.scoreForUser(title, userId);
        // Only looked up here, the single-title detail path — see
        // MovieDTO.ratingId's comment for why this isn't in the shared
        // scorer that every list-of-titles endpoint also goes through.
        ratingRepository.findFirstByUserIdAndTitleIdOrderByCreatedAtDescIdDesc(userId, id)
                .ifPresent(r -> {
                    dto.setRated(true);
                    dto.setRatingId(r.getId());
                });
        return ResponseEntity.ok(dto);
    }

    /**
     * Cast, director, trailer, and where-to-stream — fetched from TMDB on demand
     * (not stored/enriched into the catalog; see TmdbClient.details). Degrades to
     * empty fields rather than an error when TMDB is unreachable or the title has
     * no tmdbId, so a slow/unset upstream just means an emptier movie page, not a
     * broken one.
     */
    @GetMapping("/{id}/details")
    public ResponseEntity<?> details(@PathVariable Long id) {
        Title title = titleRepository.findById(id).orElse(null);
        if (title == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Unknown title id " + id));
        }
        if (title.getTmdbId() == null) {
            return ResponseEntity.ok(new TmdbClient.TmdbDetails(List.of(), null, null, List.of(), null, null));
        }
        boolean isTv = "series".equals(title.getType());
        TmdbClient.TmdbDetails details = tmdbClient.details(title.getTmdbId(), isTv);
        return ResponseEntity.ok(details == null
                ? new TmdbClient.TmdbDetails(List.of(), null, null, List.of(), null, null)
                : details);
    }
}

package com.rewatch.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.dto.RatingDTO;
import com.rewatch.model.Rating;
import com.rewatch.model.Title;
import com.rewatch.repository.RatingRepository;
import com.rewatch.repository.TitleRepository;

/**
 * Accepts a rating, persists it, and triggers a profile replay.
 *
 * This replaces the old POST /api/movies/rate, which never read its request body
 * and returned "rating saved and taste vector evolved via ema" while doing
 * nothing — no Rating entity existed to write to.
 */
@Service
public class RatingService {

    private final RatingRepository ratingRepo;
    private final TitleRepository titleRepo;
    private final TmdbClient tmdb;
    private final EnrichmentService enrichmentService;
    private final ProfileService profileService;

    public RatingService(RatingRepository ratingRepo, TitleRepository titleRepo,
                         TmdbClient tmdb, EnrichmentService enrichmentService,
                         ProfileService profileService) {
        this.ratingRepo = ratingRepo;
        this.titleRepo = titleRepo;
        this.tmdb = tmdb;
        this.enrichmentService = enrichmentService;
        this.profileService = profileService;
    }

    public record Result(Rating rating, List<ProfileService.Shift> shifts) {}

    @Transactional
    public Result submit(RatingDTO dto) {
        Title title = resolveTitle(dto);

        Rating r = new Rating();
        r.setUserId(dto.getUserId());
        r.setTitleId(title.getId());
        r.setOverall(dto.getOverall());
        r.setChars(dto.getChars());
        r.setEnding(dto.getEnding());
        r.setVisuals(dto.getVisuals());
        r.setStory(dto.getStory());
        r.setRewatch(dto.getRewatch());
        r.setMoment(dto.getMoment());
        r.setCreatedAt(Instant.now());
        r = ratingRepo.save(r);

        List<ProfileService.Shift> shifts = profileService.replay(dto.getUserId());
        return new Result(r, shifts);
    }

    /**
     * Resolves the title being rated, enriching it on the spot if it has never
     * been seen before. This is the one place a rating-time TMDB call is worth
     * paying for: we need this movie's vector regardless, and it happens once ever
     * per title.
     */
    private Title resolveTitle(RatingDTO dto) {
        if (dto.getTitleId() != null) {
            return titleRepo.findById(dto.getTitleId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown titleId " + dto.getTitleId()));
        }
        if (dto.getTmdbId() == null) {
            throw new IllegalArgumentException("Either titleId or tmdbId is required");
        }

        return titleRepo.findByTmdbId(dto.getTmdbId()).orElseGet(() -> {
            Title created = new Title();
            created.setTmdbId(dto.getTmdbId());
            created.setTitle(dto.getTitle() != null ? dto.getTitle() : "Untitled");
            Title saved = titleRepo.save(created);
            if (tmdb.isConfigured()) {
                enrichmentService.enrichOne(saved);
            }
            return saved;
        });
    }
}

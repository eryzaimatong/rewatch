package com.rewatch.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
    private final NotificationService notificationService;
    private final AchievementService achievementService;
    private final WatchStatusService watchStatusService;

    /** |delta| a single rating must move a trait to count as a "milestone." */
    private static final double MILESTONE_THRESHOLD = 0.08;

    public RatingService(RatingRepository ratingRepo, TitleRepository titleRepo,
                         TmdbClient tmdb, EnrichmentService enrichmentService,
                         ProfileService profileService, NotificationService notificationService,
                         AchievementService achievementService, WatchStatusService watchStatusService) {
        this.ratingRepo = ratingRepo;
        this.titleRepo = titleRepo;
        this.tmdb = tmdb;
        this.enrichmentService = enrichmentService;
        this.profileService = profileService;
        this.notificationService = notificationService;
        this.achievementService = achievementService;
        this.watchStatusService = watchStatusService;
    }

    public record Result(Rating rating, List<ProfileService.Shift> shifts,
                          boolean isRewatch, int watchNumber, Integer previousOverall,
                          List<String> newlyUnlockedAchievements) {}

    @Transactional
    public Result submit(RatingDTO dto) {
        Map<String, String> unlockedBefore = achievementService.unlockedTitles(dto.getUserId());

        Title title = resolveTitle(dto);

        // Read prior-watch state before this rating is inserted, or it would
        // count itself as its own "prior" watch.
        long priorCount = ratingRepo.countByUserIdAndTitleId(dto.getUserId(), title.getId());
        boolean isRewatch = priorCount > 0;
        Integer previousOverall = isRewatch
                ? ratingRepo.findFirstByUserIdAndTitleIdOrderByCreatedAtDescIdDesc(dto.getUserId(), title.getId())
                        .map(Rating::getOverall)
                        .orElse(null)
                : null;

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

        // A rating means the title is no longer "currently watching" — clears
        // any explicit WatchStatus row (a no-op if there wasn't one).
        watchStatusService.clearStatus(dto.getUserId(), title.getId());

        // replay() is also called from TasteDNAController's manual recompute and
        // from onboarding seeding — neither of those is "a new rating just
        // happened," so the milestone notification is fired here, not inside
        // ProfileService.replay() itself.
        List<ProfileService.Shift> shifts = profileService.replay(dto.getUserId());
        shifts.stream()
                .filter(s -> Math.abs(s.delta()) >= MILESTONE_THRESHOLD)
                .max(Comparator.comparingDouble(s -> Math.abs(s.delta())))
                .ifPresent(s -> notificationService.notifyTasteMilestone(dto.getUserId(), s.trait(), s.delta()));

        // Collected (not just notified) so the caller can react in the same
        // request/response round-trip — a bell-icon badge someone might not
        // check for hours is a weak payoff for the exact moment something
        // was actually unlocked.
        List<String> newlyUnlocked = new java.util.ArrayList<>();
        achievementService.unlockedTitles(dto.getUserId()).forEach((key, achievementTitle) -> {
            if (!unlockedBefore.containsKey(key)) {
                notificationService.notifyAchievementUnlocked(dto.getUserId(), achievementTitle);
                newlyUnlocked.add(achievementTitle);
            }
        });

        return new Result(r, shifts, isRewatch, (int) priorCount + 1, previousOverall, newlyUnlocked);
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

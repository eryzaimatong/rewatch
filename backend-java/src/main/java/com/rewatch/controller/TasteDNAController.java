package com.rewatch.controller;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;
import com.rewatch.repository.RatingRepository;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.ArchetypeService;
import com.rewatch.service.HistoryService;
import com.rewatch.service.ProfileService;
import com.rewatch.service.WrappedService;

/**
 * Every value returned here traces to a real, persisted rating. The old version
 * of this controller returned ten hardcoded TraitNode literals identical for
 * every user id; this one reads (and can force-recompute, via /replay) the actual
 * per-user profile built by ProfileService.
 */
@RestController
@RequestMapping("/api/tastedna")
public class TasteDNAController {

    private final ProfileService profileService;
    private final ArchetypeService archetypeService;
    private final HistoryService historyService;
    private final RatingRepository ratingRepo;
    private final WrappedService wrappedService;

    public TasteDNAController(ProfileService profileService, ArchetypeService archetypeService,
                              HistoryService historyService, RatingRepository ratingRepo,
                              WrappedService wrappedService) {
        this.profileService = profileService;
        this.archetypeService = archetypeService;
        this.historyService = historyService;
        this.ratingRepo = ratingRepo;
        this.wrappedService = wrappedService;
    }

    @GetMapping("/profile/{id}")
    public Map<String, Object> getprofile(@PathVariable("id") Long id, Authentication authentication) {
        if (id == null) {
            return Map.of();
        }
        SecurityUtil.requireSelf(authentication, id);

        Map<Trait, TraitNode> profile = profileService.currentProfile(id);
        long ratingCount = ratingRepo.countByUserId(id);
        ArchetypeService.Result archetype = archetypeService.classify(profile);

        Map<String, Object> traits = new LinkedHashMap<>();
        for (Trait t : Trait.values()) {
            TraitNode n = profile.get(t);
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("val", n.getVal());
            node.put("conf", n.getConf());
            node.put("trend", n.getTrend());
            node.put("evid", n.getEvid());
            node.put("updated", n.getUpdated());
            node.put("label", t.label());
            traits.put(t.key(), node);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("userId", id);
        res.put("personalized", ratingCount > 0);
        res.put("ratingCount", ratingCount);
        res.put("meanConfidence", profileService.meanConfidence(profile));
        res.put("archetype", archetype.archetype());
        res.put("archetypeBlurb", archetype.blurb());
        res.put("updatedAt", Instant.now());
        res.put("traits", traits);
        return res;
    }

    @GetMapping("/history/{id}")
    public HistoryService.Timeline getHistory(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) List<String> traits,
            Authentication authentication) {

        SecurityUtil.requireSelf(authentication, id);

        HistoryService.Granularity g;
        try {
            g = HistoryService.Granularity.valueOf(granularity.toUpperCase());
        } catch (IllegalArgumentException e) {
            g = HistoryService.Granularity.DAY;
        }

        List<Trait> filter = traits == null ? null : traits.stream()
                .map(Trait::fromKey)
                .filter(java.util.Objects::nonNull)
                .toList();

        return historyService.build(id, g, filter);
    }

    /**
     * "Your month" recap. `month` is `YYYY-MM`; defaults to the current calendar
     * month (UTC) when omitted. Everything in the response is a read over data
     * that already exists for the profile/history endpoints — see WrappedService.
     */
    @GetMapping("/wrapped/{id}")
    public WrappedService.WrappedSummary wrapped(
            @PathVariable("id") Long id,
            @RequestParam(required = false) String month,
            Authentication authentication) {

        SecurityUtil.requireSelf(authentication, id);

        YearMonth ym;
        try {
            ym = month == null || month.isBlank()
                    ? YearMonth.now(ZoneOffset.UTC)
                    : YearMonth.parse(month);
        } catch (java.time.format.DateTimeParseException e) {
            ym = YearMonth.now(ZoneOffset.UTC);
        }

        return wrappedService.build(id, ym);
    }

    /** Forces a full replay from the rating log. Useful after a lexicon change. */
    @PostMapping("/replay/{id}")
    public Map<String, Object> replay(@PathVariable("id") Long id, Authentication authentication) {
        SecurityUtil.requireSelf(authentication, id);
        profileService.replay(id);
        return Map.of("status", "success", "message", "Profile recomputed from rating history.");
    }
}

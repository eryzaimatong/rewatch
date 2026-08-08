package com.rewatch.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.model.Title;
import com.rewatch.model.Trait;
import com.rewatch.repository.TitleRepository;
import com.rewatch.service.EnrichmentService;
import com.rewatch.service.ScoringService;

/**
 * Tuning instruments for the trait lexicon and the scoring calibration.
 *
 * /feature-stats in particular is the answer to "how do I know the lexicon isn't
 * producing a flat, undifferentiated catalog": a flat axis (stdev under ~0.08) is
 * a lexicon bug on that axis, not something to paper over with normalization.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final TitleRepository titleRepo;
    private final EnrichmentService enrichmentService;
    private final ScoringService scoringService;

    public AdminController(TitleRepository titleRepo, EnrichmentService enrichmentService,
                           ScoringService scoringService) {
        this.titleRepo = titleRepo;
        this.enrichmentService = enrichmentService;
        this.scoringService = scoringService;
    }

    /** Re-derives every title's vector from CACHED keywords. Zero TMDB calls. */
    @PostMapping("/recompute-features")
    public Map<String, Object> recomputeFeatures() {
        int n = enrichmentService.recomputeAll();
        return Map.of("status", "success", "titlesRecomputed", n);
    }

    /** Ingests fresh TMDB lists and enriches up to `limit` unenriched titles now. */
    @PostMapping("/enrich")
    public Map<String, Object> enrich(@RequestParam(defaultValue = "50") int limit) {
        int ingested = enrichmentService.ingestStandardLists();
        List<Title> batch = titleRepo.findUnenriched(
                org.springframework.data.domain.PageRequest.of(0, limit));
        int enriched = 0;
        for (Title t : batch) {
            if (enrichmentService.enrichOne(t)) {
                enriched++;
            }
        }
        return Map.of("status", "success", "ingested", ingested, "enriched", enriched);
    }

    /**
     * A deliberate, explicitly-triggered, one-time breadth expansion — pulls
     * across many TMDB genres/sorts (not just popularity) so the catalog
     * stops being thin enough that a mismatched title can win a themed
     * collection by default. Real wall-clock time (rate-limited TMDB calls);
     * not something that runs on every boot. Follow with /enrich to
     * keyword-enrich a batch of the newly-ingested titles.
     */
    @PostMapping("/expand-catalog")
    public Map<String, Object> expandCatalog(@RequestParam(defaultValue = "1500") int target) {
        return enrichmentService.bulkExpand(target);
    }

    /** Per-axis mean/stdev/min/max across the catalog — the calibration instrument. */
    @GetMapping("/feature-stats")
    public Map<String, Object> featureStats() {
        List<Title> titles = titleRepo.findAll();
        Map<String, Object> perTrait = new LinkedHashMap<>();

        for (Trait t : Trait.values()) {
            double[] vals = titles.stream()
                    .mapToDouble(title -> title.traitVector().get(t))
                    .toArray();

            double mean = java.util.Arrays.stream(vals).average().orElse(0.5);
            double variance = java.util.Arrays.stream(vals)
                    .map(v -> (v - mean) * (v - mean))
                    .average().orElse(0.0);
            double stdev = Math.sqrt(variance);
            double min = java.util.Arrays.stream(vals).min().orElse(0.5);
            double max = java.util.Arrays.stream(vals).max().orElse(0.5);

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("mean", mean);
            stats.put("stdev", stdev);
            stats.put("min", min);
            stats.put("max", max);
            stats.put("flat", stdev < 0.08);
            perTrait.put(t.key(), stats);
        }

        long enrichedCount = titleRepo.countByEnrichedAtIsNotNull();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("titleCount", titles.size());
        res.put("enrichedCount", enrichedCount);
        res.put("scoringAnchor", scoringService.getAnchor());
        res.put("scoringSlope", scoringService.getSlope());
        res.put("traits", perTrait);
        return res;
    }
}

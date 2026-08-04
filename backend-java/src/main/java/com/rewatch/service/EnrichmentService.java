package com.rewatch.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.features.FeatureDeriver;
import com.rewatch.features.FeaturesSource;
import com.rewatch.features.KeywordLexicon;
import com.rewatch.model.Title;
import com.rewatch.repository.TitleRepository;

/**
 * Brings titles into the catalog and derives their trait vectors.
 *
 * Two-speed by design:
 *
 *   INGEST (synchronous, free) — a TMDB list response already carries genre_ids,
 *   so a title can be upserted with a real genre-derived vector the moment it is
 *   first seen, with zero extra API calls. The feed is never blocked on TMDB.
 *
 *   ENRICH (background, one call per title) — a scheduled sweep upgrades titles to
 *   keyword-derived vectors a few at a time. Raw keywords are cached on the row, so
 *   re-tuning the lexicon later costs no TMDB requests at all.
 */
@Service
public class EnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);

    /** Small enough to stay polite, large enough to finish a catalog in a minute. */
    private static final int BATCH_SIZE = 15;

    private final TitleRepository titleRepo;
    private final TmdbClient tmdb;
    private final FeatureDeriver deriver;

    public EnrichmentService(TitleRepository titleRepo, TmdbClient tmdb, FeatureDeriver deriver) {
        this.titleRepo = titleRepo;
        this.tmdb = tmdb;
        this.deriver = deriver;
    }

    /**
     * Upserts a TMDB list result into the catalog, deriving a genre-only vector.
     * Never overwrites a better (keyword-derived) vector that already exists.
     */
    @Transactional
    public Title ingest(TmdbClient.TmdbMovie m, String type) {
        if (m.tmdbId() == null) {
            return null;
        }
        Title t = titleRepo.findByTmdbId(m.tmdbId()).orElseGet(Title::new);

        t.setTmdbId(m.tmdbId());
        t.setTitle(m.title() == null ? "Untitled" : m.title());
        t.setSynopsis(truncate(m.overview(), 1000));
        t.setPoster(m.posterPath());
        t.setOriginalLanguage(m.originalLanguage());
        t.setVoteAverage(m.voteAverage());
        t.setVoteCount(m.voteCount());
        if (m.popularity() != null) {
            t.setPopularity(m.popularity());
        }
        if (type != null && t.getType() == null) {
            t.setType(type);
        }
        if (m.releaseDate() != null && m.releaseDate().length() >= 4) {
            try {
                t.setYear(Integer.parseInt(m.releaseDate().substring(0, 4)));
            } catch (NumberFormatException ignored) {
                // leave year unset rather than failing the ingest
            }
        }
        if (m.genreIds() != null && !m.genreIds().isEmpty()) {
            t.setGenreIds(join(m.genreIds()));
        }

        // Derive from what we have now. Skipped if the title already carries a
        // keyword-derived vector, which is strictly better.
        boolean alreadyEnriched = FeaturesSource.TMDB_KEYWORDS.name().equals(t.getFeaturesSource());
        if (!alreadyEnriched) {
            applyDerivation(t, m.genreIds(), List.of(), m.overview(), m.originalLanguage());
        }

        return titleRepo.save(t);
    }

    /** Background sweep: upgrade titles that have never been keyword-enriched. */
    @Scheduled(initialDelayString = "PT15S", fixedDelayString = "PT20S")
    public void enrichBatch() {
        if (!tmdb.isConfigured()) {
            return;
        }
        List<Title> batch = titleRepo.findUnenriched(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) {
            return;
        }
        int ok = 0;
        for (Title t : batch) {
            if (enrichOne(t)) {
                ok++;
            }
        }
        log.info("Enriched {}/{} titles with TMDB keywords", ok, batch.size());
    }

    @Transactional
    public boolean enrichOne(Title t) {
        if (t.getTmdbId() == null) {
            // Nothing to fetch. Mark it done so it leaves the queue, using whatever
            // genre signal it already has.
            t.setEnrichedAt(Instant.now());
            titleRepo.save(t);
            return false;
        }

        List<String> keywords = tmdb.keywords(t.getTmdbId());
        t.setKeywordNames(truncate(String.join(" | ", keywords), 2000));

        applyDerivation(t,
                FeatureDeriver.parseGenreIds(t.getGenreIds()),
                keywords,
                t.getSynopsis(),
                t.getOriginalLanguage());

        t.setEnrichedAt(Instant.now());
        titleRepo.save(t);
        return !keywords.isEmpty();
    }

    /**
     * Re-derives every title's vector from CACHED keywords — no TMDB calls.
     * This is what makes lexicon tuning cheap enough to actually do.
     */
    @Transactional
    public int recomputeAll() {
        List<Title> all = titleRepo.findAll();
        for (Title t : all) {
            applyDerivation(t,
                    FeatureDeriver.parseGenreIds(t.getGenreIds()),
                    FeatureDeriver.parseKeywords(t.getKeywordNames()),
                    t.getSynopsis(),
                    t.getOriginalLanguage());
        }
        titleRepo.saveAll(all);
        log.info("Recomputed trait vectors for {} titles at lexicon v{}", all.size(), KeywordLexicon.VERSION);
        return all.size();
    }

    private void applyDerivation(Title t, List<Integer> genreIds, List<String> keywords,
                                 String overview, String lang) {
        FeatureDeriver.Derived d = deriver.derive(genreIds, keywords, overview, lang);
        t.setTraitVector(d.vector());
        t.setFeatureConfidence(d.confidence());
        t.setFeaturesSource(d.source().name());
        t.setLexiconVersion(KeywordLexicon.VERSION);
    }

    private static String join(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Ingests all the standard TMDB lists. Returns how many titles were touched. */
    @Transactional
    public int ingestStandardLists() {
        if (!tmdb.isConfigured()) {
            return 0;
        }
        List<Title> touched = new ArrayList<>();
        tmdb.discoverAnime().forEach(m -> touched.add(ingest(m, "anime")));
        tmdb.discoverKorean().forEach(m -> touched.add(ingest(m, "kdrama")));
        tmdb.popular().forEach(m -> touched.add(ingest(m, "movie")));
        tmdb.trending().forEach(m -> touched.add(ingest(m, "movie")));
        return (int) touched.stream().filter(java.util.Objects::nonNull).count();
    }
}

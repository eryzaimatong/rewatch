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

    // TMDB's primary movie genre ids. Pulling per-genre (not just
    // popularity-sorted) is what keeps the catalog from being 1,500 more
    // copies of the same dozen blockbusters — a popularity-only pull
    // reproduces exactly the thin-catalog symptom this expansion exists to
    // fix (see the "Slow Cinema" collection mis-serving example).
    private static final int[] MOVIE_GENRES = {
        28, 12, 16, 35, 80, 99, 18, 10751, 14, 36, 27, 10402, 9648, 10749, 878, 53, 10752, 37
    };
    // TMDB's TV genre ids are a DIFFERENT id space than movie genres.
    private static final int[] TV_GENRES = {10759, 16, 35, 80, 99, 18, 10751, 9648, 10765, 10768, 37};

    private static final int PAGES_PER_GENRE = 6;
    private static final int PAGES_GENERAL = 8;
    // Hard safety cap on TMDB calls regardless of how close to target we are
    // — this is a live operation against a real API key, not something that
    // should be able to loop unbounded from a bad response shape.
    private static final int MAX_PAGE_CALLS = 400;
    private static final long RATE_LIMIT_DELAY_MS = 300;

    /**
     * One-time (explicitly triggered, not scheduled) catalog-breadth
     * expansion. Not @Transactional at this level — a run this size takes
     * real wall-clock time (rate-limit delays between hundreds of TMDB
     * calls), and holding one long-lived DB transaction open for that whole
     * span would be a real risk, not a nice-to-have to avoid. Each ingest()
     * call below already carries its own short transaction.
     */
    public java.util.Map<String, Object> bulkExpand(int targetCount) {
        int before = (int) titleRepo.count();
        int pageCalls = 0;
        int ingested = 0;

        pageCalls = pullGenres(MOVIE_GENRES, "movie", PAGES_PER_GENRE, targetCount, pageCalls);
        pageCalls = pullGenres(TV_GENRES, "series", PAGES_PER_GENRE, targetCount, pageCalls);
        // Anime previously came only from ingestStandardLists' single-page,
        // movie-only discoverAnime() call — ~20 titles, ever, and none of them
        // TV series, which is most of what "anime" actually means on TMDB.
        // Same genre-16 + Japanese-language signal as discoverAnime(), but
        // paginated across both movie and TV, the way every other genre above
        // already is.
        pageCalls = pullAnime(PAGES_PER_GENRE, targetCount, pageCalls);

        for (int page = 1; page <= PAGES_GENERAL && titleRepo.count() < targetCount && pageCalls < MAX_PAGE_CALLS; page++) {
            pageCalls++;
            sleep();
            for (TmdbClient.TmdbMovie m : tmdb.discoverMoviesPage(page, java.util.Map.of("sort_by", "popularity.desc"))) {
                if (ingest(m, "movie") != null) {
                    ingested++;
                }
            }
        }
        for (int page = 1; page <= PAGES_GENERAL && titleRepo.count() < targetCount && pageCalls < MAX_PAGE_CALLS; page++) {
            pageCalls++;
            sleep();
            for (TmdbClient.TmdbMovie m : tmdb.discoverMoviesPage(page,
                    java.util.Map.of("sort_by", "vote_average.desc", "vote_count.gte", "200"))) {
                if (ingest(m, "movie") != null) {
                    ingested++;
                }
            }
        }

        int after = (int) titleRepo.count();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("titlesBefore", before);
        result.put("titlesAfter", after);
        result.put("titlesAdded", after - before);
        result.put("pageCallsMade", pageCalls);
        return result;
    }

    private int pullGenres(int[] genreIds, String type, int pagesPerGenre, int targetCount, int pageCalls) {
        for (int genreId : genreIds) {
            for (int page = 1; page <= pagesPerGenre; page++) {
                if (titleRepo.count() >= targetCount || pageCalls >= MAX_PAGE_CALLS) {
                    return pageCalls;
                }
                pageCalls++;
                sleep();
                List<TmdbClient.TmdbMovie> results = "series".equals(type)
                        ? tmdb.discoverTvPage(page, java.util.Map.of("with_genres", String.valueOf(genreId), "sort_by", "popularity.desc"))
                        : tmdb.discoverMoviesPage(page, java.util.Map.of("with_genres", String.valueOf(genreId), "sort_by", "popularity.desc"));
                for (TmdbClient.TmdbMovie m : results) {
                    ingest(m, type);
                }
            }
        }
        return pageCalls;
    }

    /** Same shape as pullGenres, but a fixed with_original_language=ja + with_genres=16 filter across both movie and TV, instead of one genre id at a time. */
    private int pullAnime(int pagesPerType, int targetCount, int pageCalls) {
        java.util.Map<String, String> animeParams = java.util.Map.of(
                "with_original_language", "ja",
                "with_genres", "16",
                "sort_by", "popularity.desc");
        for (int page = 1; page <= pagesPerType; page++) {
            if (titleRepo.count() >= targetCount || pageCalls >= MAX_PAGE_CALLS) {
                return pageCalls;
            }
            pageCalls++;
            sleep();
            for (TmdbClient.TmdbMovie m : tmdb.discoverMoviesPage(page, animeParams)) {
                ingest(m, "anime");
            }
        }
        for (int page = 1; page <= pagesPerType; page++) {
            if (titleRepo.count() >= targetCount || pageCalls >= MAX_PAGE_CALLS) {
                return pageCalls;
            }
            pageCalls++;
            sleep();
            for (TmdbClient.TmdbMovie m : tmdb.discoverTvPage(page, animeParams)) {
                ingest(m, "anime");
            }
        }
        return pageCalls;
    }

    private void sleep() {
        try {
            Thread.sleep(RATE_LIMIT_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

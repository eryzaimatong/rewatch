package com.rewatch.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rewatch.dto.MovieDTO;
import com.rewatch.model.Title;
import com.rewatch.repository.TitleRepository;

/**
 * Orchestrates the "browse TMDB" surface. Delegates HTTP to {@link TmdbClient},
 * vector derivation to {@link EnrichmentService}, and scoring to {@link Recommender}
 * — this class only decides which titles to show and in what order.
 *
 * The old version of this class computed a "match score" from a title's array
 * index (`75 + (i*3%20)`), which meant reordering the response changed the score
 * and the same movie could score differently in two different lists. Everything
 * here now goes through one Recommender, so a title's score is the same wherever
 * it appears.
 */
@Service
public class TmdbService {

    /** A neutral placeholder profile for anonymous / logged-out browsing. */
    private static final long GUEST_USER_ID = 0L;

    private final TmdbClient tmdb;
    private final EnrichmentService enrichmentService;
    private final Recommender recommender;
    private final TitleRepository titleRepo;
    private final NlpQueryParser nlpQueryParser;

    public TmdbService(TmdbClient tmdb, EnrichmentService enrichmentService,
                       Recommender recommender, TitleRepository titleRepo,
                       NlpQueryParser nlpQueryParser) {
        this.tmdb = tmdb;
        this.enrichmentService = enrichmentService;
        this.recommender = recommender;
        this.titleRepo = titleRepo;
        this.nlpQueryParser = nlpQueryParser;
    }

    /**
     * The main feed. Reads only the local catalog — TMDB is never called on this
     * path, so the feed's latency and availability don't depend on it. Freshness
     * comes from {@link EnrichmentService}'s background ingestion instead.
     */
    public List<MovieDTO> getpopular(Long userId) {
        long effectiveUserId = userId == null ? GUEST_USER_ID : userId;
        return recommender.recommend(effectiveUserId, 30, false, true);
    }

    @Transactional
    public List<MovieDTO> searchmovies(String query, Long userId) {
        if (query == null || query.trim().isEmpty()) {
            return getpopular(userId);
        }
        List<Title> titles = ingestLive(tmdb.search(query), "movie");
        return scoreAndSort(titles, userId, 30);
    }

    /**
     * Free-text mood search. Parses the query into a target trait vector (see
     * {@link NlpQueryParser}) and scores the LOCAL catalog against it directly,
     * rather than against the user's profile — so "cozy anime" surfaces cozy anime
     * regardless of what the logged-in user's taste looks like.
     *
     * This is the honestly-labelled "semantic search" tier: a real keyword/phrase
     * lexicon with negation and comparative handling, not an LLM. Full comparative
     * queries ("like Your Name but happier") are handled by {@link NlpQueryParser}.
     */
    @Transactional
    public List<MovieDTO> nlpsearch(String query, Long userId) {
        if (query == null || query.trim().isEmpty()) {
            return getpopular(userId);
        }

        NlpQueryParser.ParsedQuery parsed = nlpQueryParser.parse(query, titleRepo.findAll());
        List<Title> candidates = titleRepo.findAll();

        List<MovieDTO> scored = new ArrayList<>(candidates.size());
        for (Title t : candidates) {
            MovieDTO dto = recommender.scoreAgainstVector(t, parsed.targetVector(), parsed.confidence());
            dto.getReasons().add(0, "Matches your search: \"" + query.trim() + "\"");
            scored.add(dto);
        }
        scored.sort(Comparator.comparingDouble(MovieDTO::getMatchScore).reversed());
        return scored.subList(0, Math.min(30, scored.size()));
    }

    @Transactional
    public List<MovieDTO> getTrending(Long userId) {
        List<Title> titles = ingestLive(tmdb.trending(), "movie");
        return scoreKeepingOrder(titles, userId);
    }

    @Transactional
    public List<MovieDTO> getTopRated(Long userId) {
        List<Title> titles = ingestLive(tmdb.topRated(), "movie");
        return scoreKeepingOrder(titles, userId);
    }

    private List<Title> ingestLive(List<TmdbClient.TmdbMovie> movies, String type) {
        List<Title> out = new ArrayList<>(movies.size());
        for (TmdbClient.TmdbMovie m : movies) {
            Title t = enrichmentService.ingest(m, type);
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }

    private List<MovieDTO> scoreAndSort(List<Title> titles, Long userId, int limit) {
        long effectiveUserId = userId == null ? GUEST_USER_ID : userId;
        List<MovieDTO> scored = new ArrayList<>(titles.size());
        for (Title t : titles) {
            scored.add(recommender.scoreForUser(t, effectiveUserId));
        }
        scored.sort(Comparator.comparingDouble(MovieDTO::getMatchScore).reversed());
        return scored.subList(0, Math.min(limit, scored.size()));
    }

    /** Scores each title but preserves TMDB's own ordering (trending/top-rated rank). */
    private List<MovieDTO> scoreKeepingOrder(List<Title> titles, Long userId) {
        long effectiveUserId = userId == null ? GUEST_USER_ID : userId;
        List<MovieDTO> scored = new ArrayList<>(titles.size());
        for (Title t : titles) {
            scored.add(recommender.scoreForUser(t, effectiveUserId));
        }
        return scored;
    }
}

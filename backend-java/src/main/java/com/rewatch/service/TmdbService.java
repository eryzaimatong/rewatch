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
        return getpopular(userId, null, null, null);
    }

    /**
     * genre/language/vibe filter the FULL candidate pool server-side (see
     * Recommender.recommend's filtered overload) rather than the frontend
     * filtering only the already-ranked top-30 titles this returns when
     * unfiltered — that was the actual cause of "every filter combo returns
     * zero," not a catalog-size problem (the catalog has 6,000+ titles;
     * the unfiltered feed only ever surfaced 30 of them to filter further).
     */
    public List<MovieDTO> getpopular(Long userId, String genre, String language, String vibe) {
        long effectiveUserId = userId == null ? GUEST_USER_ID : userId;
        return recommender.recommend(effectiveUserId, 30, false, true, genre, language, vibe);
    }

    @Transactional
    public List<MovieDTO> searchmovies(String query, Long userId) {
        if (query == null || query.trim().isEmpty()) {
            return getpopular(userId);
        }
        // Movies and TV are separate TMDB endpoints with separate JSON shapes
        // (see TmdbClient.searchTv's comment) — without both, a title search
        // could never find a TV show, full stop, regardless of how exact the
        // match was.
        List<Title> titles = new ArrayList<>(ingestLive(tmdb.search(query), "movie"));
        titles.addAll(ingestLive(tmdb.searchTv(query), "series"));

        // Deliberately NOT scoreAndSort (rank by taste-match score) — for a
        // literal title search, personal-taste fit is the wrong ranking
        // signal entirely. Found live: searching "Breaking Bad" surfaced an
        // obscure, unrelated movie ("Breaking in Badly") above the actual
        // show, because that decoy happened to score better against the
        // searching user's taste profile — text relevance to what was typed
        // never factored in at all. Ranked by relevance to the query first
        // (exact title match, then starts-with, then contains), then by
        // voteCount — not voteAverage, which small-sample-biases toward a
        // near-nobody title with a single 10/10 vote — as the tiebreaker
        // within a relevance tier. Each title's real matchScore is still
        // attached per-card for the match-ring badge, same as before; only
        // the sort key changes.
        String needle = query.trim().toLowerCase();
        titles.sort(Comparator
                .comparingInt((Title t) -> titleRelevanceRank(t.getTitle(), needle))
                .thenComparing(Comparator.comparing(Title::getVoteCount,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed()));

        long effectiveUserId = userId == null ? GUEST_USER_ID : userId;
        List<MovieDTO> scored = new ArrayList<>(titles.size());
        for (Title t : titles.subList(0, Math.min(30, titles.size()))) {
            scored.add(recommender.scoreForUser(t, effectiveUserId));
        }
        return scored;
    }

    /** Lower is more relevant. 0 = exact title match, 1 = title starts with the query, 2 = query appears anywhere, 3 = matched on something other than the title (a TMDB result whose overview/keywords matched, not its name). */
    private int titleRelevanceRank(String title, String needle) {
        if (title == null) {
            return 3;
        }
        String t = title.trim().toLowerCase();
        if (t.equals(needle)) {
            return 0;
        }
        if (t.startsWith(needle)) {
            return 1;
        }
        if (t.contains(needle)) {
            return 2;
        }
        return 3;
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
    /**
     * What the parser actually understood from a query, without scoring
     * anything — lets the frontend show real "we understood: Comfort,
     * Hopeful Ending" chips instead of a hardcoded default that never
     * changed no matter what was searched.
     */
    @Transactional
    public java.util.Map<String, Double> understand(String query) {
        if (query == null || query.trim().isEmpty()) {
            return java.util.Map.of();
        }
        NlpQueryParser.ParsedQuery parsed = nlpQueryParser.parse(query, titleRepo.findAll());
        return parsed.targetVector().toKeyedMap();
    }

    @Transactional
    public List<MovieDTO> nlpsearch(String query, Long userId) {
        if (query == null || query.trim().isEmpty()) {
            return getpopular(userId);
        }

        NlpQueryParser.ParsedQuery parsed = nlpQueryParser.parse(query, titleRepo.findAll());
        // Unlike recommend()'s candidate pool, this used to be the raw, entirely
        // unfiltered catalog — including titles with a single stray vote and a
        // two-word TMDB overview (bulkExpand ingests broadly, and not every
        // TMDB entry is a real, well-documented release). A mood query has no
        // per-user profile to gate against — see this method's own doc comment
        // — so a coincidentally-decent score against a target vector was
        // enough for one of those to become someone's top, or even featured,
        // "match" for what they typed. Same quality bar recommend() already
        // applies, reused here rather than re-derived.
        List<Title> candidates = recommender.candidatePool(30);

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

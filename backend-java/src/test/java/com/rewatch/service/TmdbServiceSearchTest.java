package com.rewatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.rewatch.dto.MovieDTO;
import com.rewatch.model.Title;

/**
 * searchmovies() used to call TmdbClient.search() alone, which only ever hits
 * TMDB's /search/movie endpoint — a TV show could never appear in a title
 * search result no matter how exact the match (searching "Breaking Bad"
 * turned up an unrelated movie, since the actual show was never even in the
 * candidate set). This locks in that both search() and searchTv() get called
 * and their results actually merged, not just one silently dropped.
 *
 * Every collaborator here is a hand-written subclass, not @Mock — this
 * JDK/Mockito combination can't byte-buddy-instrument concrete classes at
 * all (same constraint noted in AccountServiceTest re: AchievementService),
 * and TmdbService's dependencies are all concrete classes with no
 * interfaces to mock instead. Constructor args these subclasses never
 * exercise are passed as null, same as the existing pattern elsewhere
 * (WatchlistServiceTest's `new WatchlistService(..., null, null, null, ...)`).
 */
class TmdbServiceSearchTest {

    private static class FakeTmdbClient extends TmdbClient {
        List<TmdbMovie> movieResults = List.of();
        List<TmdbMovie> tvResults = List.of();

        FakeTmdbClient() { super(null); }

        @Override
        public List<TmdbMovie> search(String query) { return movieResults; }

        @Override
        public List<TmdbMovie> searchTv(String query) { return tvResults; }
    }

    private static class FakeEnrichmentService extends EnrichmentService {
        final Map<TmdbClient.TmdbMovie, Title> byRawMovie = new HashMap<>();

        FakeEnrichmentService() { super(null, null, null); }

        @Override
        public Title ingest(TmdbClient.TmdbMovie m, String type) {
            return byRawMovie.get(m);
        }
    }

    private static class FakeRecommender extends Recommender {
        final Map<Title, MovieDTO> byTitle = new HashMap<>();

        FakeRecommender() { super(null, null, null, null, null); }

        @Override
        public MovieDTO scoreForUser(Title title, Long userId) {
            return byTitle.get(title);
        }
    }

    private final FakeTmdbClient tmdb = new FakeTmdbClient();
    private final FakeEnrichmentService enrichmentService = new FakeEnrichmentService();
    private final FakeRecommender recommender = new FakeRecommender();

    private TmdbService newService() {
        return new TmdbService(tmdb, enrichmentService, recommender, null, null);
    }

    private Title titleWith(long id, String name) {
        Title t = new Title();
        t.setId(id);
        t.setTitle(name);
        return t;
    }

    private MovieDTO dtoFor(Title t, double score) {
        MovieDTO dto = new MovieDTO();
        dto.setId(t.getId());
        dto.setTitle(t.getTitle());
        dto.setMatchScore(score);
        return dto;
    }

    @Test
    void searchMergesMovieAndTvResultsInsteadOfMovieOnly() {
        TmdbClient.TmdbMovie rawMovie = new TmdbClient.TmdbMovie(
                1, "Some Movie", "overview", null, null, "2020-01-01", List.of(), "en", 7.0, 100, 5.0);
        TmdbClient.TmdbMovie rawShow = new TmdbClient.TmdbMovie(
                2, "Breaking Bad", "overview", null, null, "2008-01-20", List.of(), "en", 9.0, 500, 50.0);

        tmdb.movieResults = List.of(rawMovie);
        tmdb.tvResults = List.of(rawShow);

        Title movieTitle = titleWith(101L, "Some Movie");
        Title showTitle = titleWith(102L, "Breaking Bad");
        enrichmentService.byRawMovie.put(rawMovie, movieTitle);
        enrichmentService.byRawMovie.put(rawShow, showTitle);

        recommender.byTitle.put(movieTitle, dtoFor(movieTitle, 40.0));
        recommender.byTitle.put(showTitle, dtoFor(showTitle, 90.0));

        List<MovieDTO> result = newService().searchmovies("Breaking Bad", 1L);

        Set<String> titles = result.stream().map(MovieDTO::getTitle).collect(java.util.stream.Collectors.toSet());
        assertTrue(titles.contains("Breaking Bad"), "the actual TV show must be in the results, not just the movie");
        assertTrue(titles.contains("Some Movie"));
        assertEquals("Breaking Bad", result.get(0).getTitle());
    }

    @Test
    void exactTitleMatchRanksFirstEvenWithALowerTasteMatchScore() {
        // The precise shape of the live bug: an exact title match with a LOW
        // taste-match score, against an unrelated decoy with a HIGH one.
        // Ranking by matchScore put the decoy first; text relevance to the
        // query must win regardless of which one the searching user's taste
        // profile happens to favor.
        TmdbClient.TmdbMovie rawExact = new TmdbClient.TmdbMovie(
                1, "Breaking Bad", "overview", null, null, "2008-01-20", List.of(), "en", 8.9, 18410, 150.0);
        TmdbClient.TmdbMovie rawDecoy = new TmdbClient.TmdbMovie(
                2, "Breaking in Badly", "overview", null, null, "2024-01-01", List.of(), "en", 6.0, 3, 1.0);

        tmdb.movieResults = List.of(rawDecoy);
        tmdb.tvResults = List.of(rawExact);

        Title exactTitle = titleWith(101L, "Breaking Bad");
        Title decoyTitle = titleWith(102L, "Breaking in Badly");
        enrichmentService.byRawMovie.put(rawExact, exactTitle);
        enrichmentService.byRawMovie.put(rawDecoy, decoyTitle);

        // Deliberately inverted: the decoy scores HIGHER against this user's
        // taste profile than the exact match does.
        recommender.byTitle.put(exactTitle, dtoFor(exactTitle, 20.0));
        recommender.byTitle.put(decoyTitle, dtoFor(decoyTitle, 95.0));

        List<MovieDTO> result = newService().searchmovies("Breaking Bad", 1L);

        assertEquals("Breaking Bad", result.get(0).getTitle(),
                "an exact title match must rank first regardless of taste-match score");
    }
}

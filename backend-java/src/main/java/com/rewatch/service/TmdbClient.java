package com.rewatch.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Thin, failure-tolerant HTTP client for TMDB. Parsing only — no scoring, no
 * business logic.
 *
 * Deliberately captures the fields the old parser discarded: genre_ids,
 * original_language, vote_average and vote_count. genre_ids in particular arrives
 * free on every list response and is enough to derive a real trait vector without
 * a single extra request.
 *
 * Every call degrades to an empty result rather than throwing, so an unreachable
 * TMDB or an unset API key leaves the app serving its local catalog instead of
 * returning 500s.
 */
@Service
public class TmdbClient {

    private static final Logger log = LoggerFactory.getLogger(TmdbClient.class);

    @Value("${tmdb.api.url}")
    private String baseUrl;

    @Value("${tmdb.api.key}")
    private String apiKey;

    private final RestTemplate rest;

    public TmdbClient(RestTemplate rest) {
        this.rest = rest;
    }

    /** One movie as TMDB describes it, before any interpretation. */
    public record TmdbMovie(
            Integer tmdbId,
            String title,
            String overview,
            String posterPath,
            String backdropPath,
            String releaseDate,
            List<Integer> genreIds,
            String originalLanguage,
            Double voteAverage,
            Integer voteCount,
            Double popularity) {}

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Cacheable(value = "tmdbDiscover", unless = "#result.isEmpty()")
    public List<TmdbMovie> discoverAnime() {
        return list("/discover/movie", Map.of(
                "with_original_language", "ja",
                "with_genres", "16",
                "sort_by", "popularity.desc"));
    }

    @Cacheable(value = "tmdbDiscover", unless = "#result.isEmpty()")
    public List<TmdbMovie> discoverKorean() {
        return list("/discover/movie", Map.of(
                "with_original_language", "ko",
                "sort_by", "popularity.desc"));
    }

    @Cacheable(value = "tmdbPopular", unless = "#result.isEmpty()")
    public List<TmdbMovie> popular() {
        return list("/movie/popular", Map.of());
    }

    @Cacheable(value = "tmdbTrending", unless = "#result.isEmpty()")
    public List<TmdbMovie> trending() {
        return list("/trending/movie/week", Map.of());
    }

    @Cacheable(value = "tmdbTopRated", unless = "#result.isEmpty()")
    public List<TmdbMovie> topRated() {
        return list("/movie/top_rated", Map.of());
    }

    public List<TmdbMovie> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return list("/search/movie", Map.of("query", query.trim()));
    }

    /**
     * Keywords for one title.
     *
     * Uses append_to_response so details and keywords cost ONE request rather than
     * two. Credits are deliberately not requested: a large payload for negligible
     * emotional signal.
     */
    @SuppressWarnings("unchecked")
    public List<String> keywords(int tmdbId) {
        if (!isConfigured()) {
            return List.of();
        }
        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/movie/" + tmdbId)
                    .queryParam("api_key", apiKey)
                    .queryParam("append_to_response", "keywords")
                    .toUriString();

            Map<String, Object> res = rest.getForObject(url, Map.class);
            if (res == null) {
                return List.of();
            }
            Map<String, Object> kwWrapper = (Map<String, Object>) res.get("keywords");
            if (kwWrapper == null) {
                return List.of();
            }
            List<Map<String, Object>> kws = (List<Map<String, Object>>) kwWrapper.get("keywords");
            if (kws == null) {
                return List.of();
            }
            List<String> out = new ArrayList<>(kws.size());
            for (Map<String, Object> kw : kws) {
                Object name = kw.get("name");
                if (name != null) {
                    out.add(name.toString());
                }
            }
            return out;
        } catch (RestClientException | ClassCastException e) {
            log.warn("TMDB keyword lookup failed for {}: {}", tmdbId, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<TmdbMovie> list(String path, Map<String, String> params) {
        if (!isConfigured()) {
            log.debug("TMDB api key not configured; skipping {}", path);
            return List.of();
        }
        try {
            UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + path)
                    .queryParam("api_key", apiKey);
            params.forEach(b::queryParam);

            Map<String, Object> res = rest.getForObject(b.toUriString(), Map.class);
            if (res == null || !res.containsKey("results")) {
                return List.of();
            }

            List<Map<String, Object>> raw = (List<Map<String, Object>>) res.get("results");
            List<TmdbMovie> out = new ArrayList<>(raw.size());
            for (Map<String, Object> item : raw) {
                TmdbMovie m = parse(item);
                if (m != null) {
                    out.add(m);
                }
            }
            return out;
        } catch (RestClientException | ClassCastException e) {
            log.warn("TMDB request to {} failed: {}", path, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private TmdbMovie parse(Map<String, Object> item) {
        Object idObj = item.get("id");
        if (idObj == null) {
            return null;
        }
        List<Integer> genres = Collections.emptyList();
        Object g = item.get("genre_ids");
        if (g instanceof List<?> raw) {
            genres = new ArrayList<>(raw.size());
            for (Object o : raw) {
                if (o instanceof Number num) {
                    genres.add(num.intValue());
                }
            }
        }

        return new TmdbMovie(
                asInt(idObj),
                str(item.get("title")),
                str(item.get("overview")),
                str(item.get("poster_path")),
                str(item.get("backdrop_path")),
                str(item.get("release_date")),
                genres,
                str(item.get("original_language")),
                asDouble(item.get("vote_average")),
                asInt(item.get("vote_count")),
                asDouble(item.get("popularity")));
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Integer asInt(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private static Double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }
}

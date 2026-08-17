package com.rewatch.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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
     * Paginated, uncached discover — used for the one-time catalog-expansion
     * operation (AdminController#expandCatalog). Not @Cacheable: caching
     * would only ever serve page 1 back for every subsequent page requested,
     * which is exactly wrong for a "give me the next 20" call.
     */
    public List<TmdbMovie> discoverMoviesPage(int page, Map<String, String> extraParams) {
        Map<String, String> params = new java.util.HashMap<>(extraParams);
        params.put("page", String.valueOf(page));
        return list("/discover/movie", params);
    }

    /**
     * TV/show discover — TMDB's TV JSON shape differs from movies (name not
     * title, first_air_date not release_date), so this goes through
     * listTv()/parseTv() rather than the movie-shaped list()/parse().
     */
    public List<TmdbMovie> discoverTvPage(int page, Map<String, String> extraParams) {
        Map<String, String> params = new java.util.HashMap<>(extraParams);
        params.put("page", String.valueOf(page));
        return listTv("/discover/tv", params);
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
            logTmdbFailure("/movie/" + tmdbId + " (keywords)", e);
            return List.of();
        }
    }

    public record CastMember(String name, String character, String profilePath) {}

    /** Supplementary movie-page data — cast/director/trailer/where-to-watch/runtime/certification. */
    public record TmdbDetails(
            List<CastMember> cast,
            String director,
            String trailerUrl,
            List<String> watchProviders,
            Integer runtimeMinutes,
            String certification) {}

    private static final int CAST_LIMIT = 6;
    private static final String WATCH_REGION = "US";

    /**
     * One combined request via append_to_response — the same "details cost one
     * request, not four" approach as keywords(). Not stored on Title/enriched into
     * the catalog: this is supplementary movie-page data fetched on demand when a
     * user opens a title, not something the scoring engine needs for every one of
     * 1,500+ titles.
     */
    @SuppressWarnings("unchecked")
    @Cacheable(value = "tmdbDetails", unless = "#result == null")
    public TmdbDetails details(int tmdbId, boolean isTv) {
        if (!isConfigured()) {
            return null;
        }
        try {
            String path = (isTv ? "/tv/" : "/movie/") + tmdbId;
            // release_dates (certification) is movie-only, content_ratings is
            // TV-only — TMDB's append_to_response silently drops values that
            // don't apply to the endpoint, but requesting only the relevant
            // one keeps this explicit rather than relying on that leniency.
            String extras = "credits,videos,watch/providers," + (isTv ? "content_ratings" : "release_dates");
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + path)
                    .queryParam("api_key", apiKey)
                    .queryParam("append_to_response", extras)
                    .toUriString();

            Map<String, Object> res = rest.getForObject(url, Map.class);
            if (res == null) {
                return null;
            }

            return new TmdbDetails(
                    extractCast(res),
                    extractDirector(res, isTv),
                    extractTrailer(res),
                    extractWatchProviders(res),
                    extractRuntimeMinutes(res, isTv),
                    extractCertification(res, isTv));
        } catch (RestClientException | ClassCastException e) {
            logTmdbFailure((isTv ? "/tv/" : "/movie/") + tmdbId + " (details)", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<CastMember> extractCast(Map<String, Object> res) {
        Map<String, Object> credits = (Map<String, Object>) res.get("credits");
        if (credits == null) {
            return List.of();
        }
        List<Map<String, Object>> cast = (List<Map<String, Object>>) credits.get("cast");
        if (cast == null) {
            return List.of();
        }
        List<CastMember> out = new ArrayList<>();
        for (Map<String, Object> member : cast) {
            if (out.size() >= CAST_LIMIT) {
                break;
            }
            String name = str(member.get("name"));
            if (name != null) {
                out.add(new CastMember(name, str(member.get("character")), str(member.get("profile_path"))));
            }
        }
        return out;
    }

    /**
     * Movies: the crew member with job=="Director". TV: TMDB puts showrunners in
     * top-level created_by rather than credits.crew, so that's checked first.
     */
    @SuppressWarnings("unchecked")
    private String extractDirector(Map<String, Object> res, boolean isTv) {
        if (isTv) {
            List<Map<String, Object>> createdBy = (List<Map<String, Object>>) res.get("created_by");
            if (createdBy != null && !createdBy.isEmpty()) {
                return str(createdBy.get(0).get("name"));
            }
        }
        Map<String, Object> credits = (Map<String, Object>) res.get("credits");
        if (credits == null) {
            return null;
        }
        List<Map<String, Object>> crew = (List<Map<String, Object>>) credits.get("crew");
        if (crew == null) {
            return null;
        }
        for (Map<String, Object> member : crew) {
            if ("Director".equals(member.get("job"))) {
                return str(member.get("name"));
            }
        }
        return null;
    }

    /** First official YouTube trailer, falling back to the first YouTube trailer of any kind. */
    @SuppressWarnings("unchecked")
    private String extractTrailer(Map<String, Object> res) {
        Map<String, Object> videos = (Map<String, Object>) res.get("videos");
        if (videos == null) {
            return null;
        }
        List<Map<String, Object>> results = (List<Map<String, Object>>) videos.get("results");
        if (results == null) {
            return null;
        }
        String fallback = null;
        for (Map<String, Object> v : results) {
            if (!"YouTube".equals(v.get("site")) || !"Trailer".equals(v.get("type"))) {
                continue;
            }
            String key = str(v.get("key"));
            if (key == null) {
                continue;
            }
            if (Boolean.TRUE.equals(v.get("official"))) {
                return "https://www.youtube.com/watch?v=" + key;
            }
            if (fallback == null) {
                fallback = "https://www.youtube.com/watch?v=" + key;
            }
        }
        return fallback;
    }

    /** Streaming ("flatrate") provider names for WATCH_REGION — not rent/buy, just what's included in a subscription. */
    @SuppressWarnings("unchecked")
    private List<String> extractWatchProviders(Map<String, Object> res) {
        Map<String, Object> wrapper = (Map<String, Object>) res.get("watch/providers");
        if (wrapper == null) {
            return List.of();
        }
        Map<String, Object> byRegion = (Map<String, Object>) wrapper.get("results");
        if (byRegion == null) {
            return List.of();
        }
        Map<String, Object> region = (Map<String, Object>) byRegion.get(WATCH_REGION);
        if (region == null) {
            return List.of();
        }
        List<Map<String, Object>> flatrate = (List<Map<String, Object>>) region.get("flatrate");
        if (flatrate == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Map<String, Object> provider : flatrate) {
            String name = str(provider.get("provider_name"));
            if (name != null) {
                out.add(name);
            }
        }
        return out;
    }

    /** Movies carry a flat `runtime`; TV carries `episode_run_time` (an array — one episode's length, not the series total). */
    @SuppressWarnings("unchecked")
    private Integer extractRuntimeMinutes(Map<String, Object> res, boolean isTv) {
        if (!isTv) {
            return asInt(res.get("runtime"));
        }
        Object raw = res.get("episode_run_time");
        if (raw instanceof List<?> times && !times.isEmpty()) {
            return asInt(times.get(0));
        }
        return null;
    }

    /** US content rating/certification — "PG-13", "TV-MA", etc. Movies and TV use differently-shaped TMDB payloads. */
    @SuppressWarnings("unchecked")
    private String extractCertification(Map<String, Object> res, boolean isTv) {
        if (isTv) {
            Map<String, Object> wrapper = (Map<String, Object>) res.get("content_ratings");
            if (wrapper == null) {
                return null;
            }
            List<Map<String, Object>> results = (List<Map<String, Object>>) wrapper.get("results");
            if (results == null) {
                return null;
            }
            for (Map<String, Object> entry : results) {
                if (WATCH_REGION.equals(entry.get("iso_3166_1"))) {
                    return str(entry.get("rating"));
                }
            }
            return null;
        }

        Map<String, Object> wrapper = (Map<String, Object>) res.get("release_dates");
        if (wrapper == null) {
            return null;
        }
        List<Map<String, Object>> results = (List<Map<String, Object>>) wrapper.get("results");
        if (results == null) {
            return null;
        }
        for (Map<String, Object> entry : results) {
            if (!WATCH_REGION.equals(entry.get("iso_3166_1"))) {
                continue;
            }
            List<Map<String, Object>> dates = (List<Map<String, Object>>) entry.get("release_dates");
            if (dates == null) {
                continue;
            }
            for (Map<String, Object> date : dates) {
                String cert = str(date.get("certification"));
                if (cert != null && !cert.isBlank()) {
                    return cert;
                }
            }
        }
        return null;
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
            logTmdbFailure(path, e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<TmdbMovie> listTv(String path, Map<String, String> params) {
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
                TmdbMovie m = parseTv(item);
                if (m != null) {
                    out.add(m);
                }
            }
            return out;
        } catch (RestClientException | ClassCastException e) {
            logTmdbFailure(path, e);
            return List.of();
        }
    }

    /** Same shape as parse(), but TV list items use "name"/"first_air_date" instead of "title"/"release_date". */
    @SuppressWarnings("unchecked")
    private TmdbMovie parseTv(Map<String, Object> item) {
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
                str(item.get("name")),
                str(item.get("overview")),
                str(item.get("poster_path")),
                str(item.get("backdrop_path")),
                str(item.get("first_air_date")),
                genres,
                str(item.get("original_language")),
                asDouble(item.get("vote_average")),
                asInt(item.get("vote_count")),
                asDouble(item.get("popularity")));
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

    /**
     * A 429 (rate-limited) and a genuinely empty/unavailable result both degrade
     * to an empty list to the caller — by design, see the class doc — but they
     * shouldn't look identical in the logs. Without this, a traffic spike that
     * trips TMDB's rate limit silently blanks out search/discovery with no
     * signal that it's rate-limiting rather than "nothing found."
     */
    private void logTmdbFailure(String path, Exception e) {
        if (e instanceof HttpClientErrorException httpEx && httpEx.getStatusCode() == HttpStatusCode.valueOf(429)) {
            log.warn("TMDB rate-limited (429) request to {} — serving empty/stale result until the next successful call", path);
        } else {
            log.warn("TMDB request to {} failed: {}", path, e.getMessage());
        }
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

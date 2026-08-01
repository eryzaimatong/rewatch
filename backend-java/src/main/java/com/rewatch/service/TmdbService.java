package com.rewatch.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.rewatch.dto.MovieDTO;

@Service
public class TmdbService {

    @Value("${tmdb.api.url}")
    private String baseurl;

    @Value("${tmdb.api.key}")
    private String apikey;

    private final RestTemplate rest = new RestTemplate();

    public List<MovieDTO> searchmovies(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String url = baseurl + "/search/movie?api_key=" + apikey + "&query=" + query;
        Map res = rest.getForObject(url, Map.class);

        if (res == null || !res.containsKey("results")) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> rawlist = (List<Map<String, Object>>) res.get("results");
        List<MovieDTO> found = new ArrayList<>();

        for (int i = 0; i < rawlist.size(); i++) {
            Map<String, Object> item = rawlist.get(i);
            MovieDTO m = new MovieDTO();

            Object idobj = item.get("id");
            if (idobj != null) {
                m.setId(Long.valueOf(idobj.toString()));
            }

            Object titleobj = item.get("title");
            if (titleobj != null) {
                m.setTitle(titleobj.toString());
            } else {
                m.setTitle("Untitled");
            }

            Object overobj = item.get("overview");
            if (overobj != null) {
                m.setOverview(overobj.toString());
            } else {
                m.setOverview("No storyline available yet.");
            }

            Object posterobj = item.get("poster_path");
            if (posterobj != null) {
                m.setPosterPath(posterobj.toString());
            }

            Object backobj = item.get("backdrop_path");
            if (backobj != null) {
                m.setBackdropPath(backobj.toString());
            }

            Object dateobj = item.get("release_date");
            if (dateobj != null) {
                m.setReleaseDate(dateobj.toString());
            }

            m.setMatchScore(85.0 + (i % 10));
            m.setReasons(Arrays.asList("Balanced storytelling fit", "Matches emotional tone"));

            found.add(m);
        }

        return found;
    }

    public List<MovieDTO> getpopular() {
        String url = baseurl + "/movie/popular?api_key=" + apikey;
        Map res = rest.getForObject(url, Map.class);

        if (res == null || !res.containsKey("results")) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> rawlist = (List<Map<String, Object>>) res.get("results");
        List<MovieDTO> poplist = new ArrayList<>();

        for (int i = 0; i < rawlist.size(); i++) {
            Map<String, Object> item = rawlist.get(i);
            MovieDTO m = new MovieDTO();

            Object idobj = item.get("id");
            if (idobj != null) {
                m.setId(Long.valueOf(idobj.toString()));
            }

            Object titleobj = item.get("title");
            if (titleobj != null) {
                m.setTitle(titleobj.toString());
            } else {
                m.setTitle("Untitled");
            }

            Object overobj = item.get("overview");
            if (overobj != null) {
                m.setOverview(overobj.toString());
            }

            Object posterobj = item.get("poster_path");
            if (posterobj != null) {
                m.setPosterPath(posterobj.toString());
            }

            Object backobj = item.get("backdrop_path");
            if (backobj != null) {
                m.setBackdropPath(backobj.toString());
            }

            Object dateobj = item.get("release_date");
            if (dateobj != null) {
                m.setReleaseDate(dateobj.toString());
            }

            m.setMatchScore(88.0);
            m.setReasons(Arrays.asList("Trending masterpiece", "High emotional payoff"));

            poplist.add(m);
        }

        return poplist;
    }
}
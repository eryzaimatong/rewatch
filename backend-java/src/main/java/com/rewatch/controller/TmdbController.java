package com.rewatch.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.dto.MovieDTO;
import com.rewatch.service.TmdbService;

@RestController
@RequestMapping("/api/movies")
@CrossOrigin(origins = "*")
public class TmdbController {

    @Autowired
    private TmdbService tmdbService;

    @GetMapping("/search")
    public List<MovieDTO> searchMovies(@RequestParam(value = "query", required = false) String query) {
        if (query == null || query.trim().isEmpty()) {
            return tmdbService.getpopular();
        }
        return tmdbService.searchmovies(query);
    }

    @GetMapping("/popular")
    public List<MovieDTO> getPopularMovies() {
        return tmdbService.getpopular();
    }
}
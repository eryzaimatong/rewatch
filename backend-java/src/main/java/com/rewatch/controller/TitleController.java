package com.rewatch.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.dto.MovieDTO;
import com.rewatch.model.Title;
import com.rewatch.repository.TitleRepository;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.Recommender;

@RestController
@RequestMapping("/api/titles")
public class TitleController {

    private final TitleRepository titleRepository;
    private final Recommender recommender;

    public TitleController(TitleRepository titleRepository, Recommender recommender) {
        this.titleRepository = titleRepository;
        this.recommender = recommender;
    }

    @GetMapping
    public List<Title> getAllTitles() {
        return titleRepository.findAll();
    }

    /**
     * The explanation block for one title, on demand. This is what MovieModal
     * should call instead of the old `movie.matchScore || 85` fallback.
     */
    @GetMapping("/{id}/match")
    public ResponseEntity<?> match(@PathVariable Long id, @RequestParam Long userId,
                                   Authentication authentication) {
        SecurityUtil.requireSelf(authentication, userId);
        Title title = titleRepository.findById(id).orElse(null);
        if (title == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Unknown title id " + id));
        }
        MovieDTO dto = recommender.scoreForUser(title, userId);
        return ResponseEntity.ok(dto);
    }
}

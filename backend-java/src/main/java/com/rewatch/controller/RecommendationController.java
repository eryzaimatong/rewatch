package com.rewatch.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.dto.MovieDTO;
import com.rewatch.security.SecurityUtil;
import com.rewatch.service.Recommender;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final Recommender recommender;

    public RecommendationController(Recommender recommender) {
        this.recommender = recommender;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getrecs(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "24") int limit,
            @RequestParam(defaultValue = "true") boolean excludeRated,
            @RequestParam(defaultValue = "true") boolean diversity,
            Authentication authentication) {

        SecurityUtil.requireSelf(authentication, userId);
        List<MovieDTO> recs = recommender.recommend(userId, limit, excludeRated, diversity);
        if (recs.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No recommendations available yet.");
        }
        return ResponseEntity.ok(recs);
    }
}

package com.rewatch.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.model.Recommendation;
import com.rewatch.service.Recommender;

@RestController
@RequestMapping("/api/recommendations")
@CrossOrigin(origins = "*")
public class RecommendationController {

    private final Recommender recommender;

    public RecommendationController(Recommender recommender) {
        this.recommender = recommender;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getrecs(@PathVariable Long userId) {
        List<Recommendation> recs = recommender.getrecs(userId);
        if (recs.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("no recs found bro");
        }
        return ResponseEntity.ok(recs);
    }
}
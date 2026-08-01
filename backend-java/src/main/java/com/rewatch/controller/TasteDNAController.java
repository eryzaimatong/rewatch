package com.rewatch.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rewatch.model.TasteDNA;
import com.rewatch.repository.TasteDNARepository;

@RestController
@RequestMapping("/api/taste")
@CrossOrigin(origins = "*")
public class TasteDNAController {

    private final TasteDNARepository tasteRepo;

    public TasteDNAController(TasteDNARepository tasteRepo) {
        this.tasteRepo = tasteRepo;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getDna(@PathVariable Long userId) {
        TasteDNA dna = tasteRepo.findByUserId(userId);
        if (dna == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("TasteDNA profile not found");
        }
        return ResponseEntity.ok(dna);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<?> updateDna(@PathVariable Long userId, @RequestBody TasteDNA body) {
        TasteDNA dna = tasteRepo.findByUserId(userId);
        if (dna == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("TasteDNA profile not found");
        }

        dna.setComfort(body.getComfort());
        dna.setRomance(body.getRomance());
        dna.setSlowBurn(body.getSlowBurn());
        dna.setNostalgia(body.getNostalgia());
        dna.setSadness(body.getSadness());

        TasteDNA saved = tasteRepo.save(dna);
        return ResponseEntity.ok(saved);
    }
}
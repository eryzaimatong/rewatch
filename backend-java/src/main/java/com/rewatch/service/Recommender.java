package com.rewatch.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.rewatch.model.Recommendation;
import com.rewatch.model.TasteDNA;
import com.rewatch.model.Title;
import com.rewatch.repository.TasteDNARepository;
import com.rewatch.repository.TitleRepository;

@Service
public class Recommender {

    private final TitleRepository titlerepo;
    private final TasteDNARepository tasterepo;

    public Recommender(TitleRepository titlerepo, TasteDNARepository tasterepo) {
        this.titlerepo = titlerepo;
        this.tasterepo = tasterepo;
    }

    public List<Recommendation> getrecs(Long id) {
        TasteDNA dna = tasterepo.findByUserId(id);
        List<Recommendation> recs = new ArrayList<>();
        if (dna == null) {
            return recs;
        }

        List<Title> movies = titlerepo.findAll();
        for (Title m : movies) {
            double d1 = Math.abs(dna.getComfort() - m.getComfort());
            double d2 = Math.abs(dna.getRomance() - m.getRomance());
            double d3 = Math.abs(dna.getSlowBurn() - m.getSlowBurn());
            double d4 = Math.abs(dna.getNostalgia() - m.getNostalgia());
            double d5 = Math.abs(dna.getSadness() - m.getSadness());

            double total = d1 + d2 + d3 + d4 + d5;
            int score = (int) Math.max(10, 100 - (total / 5));

            List<String> reasons = new ArrayList<>();
            if (d1 <= 20) {
                reasons.add("comfort vibe matches");
            }
            if (d2 <= 20) {
                reasons.add("romance chemistry fits");
            }
            if (d3 <= 20) {
                reasons.add("slow burn pacing matches");
            }
            if (d4 <= 20) {
                reasons.add("nostalgia hits right");
            }
            if (d5 <= 20) {
                reasons.add("emotional tone fits");
            }
            if (reasons.isEmpty()) {
                reasons.add("balanced storytelling fit");
            }

            Recommendation r = new Recommendation();
            r.setTitle(m);
            r.setMatchScore(score);
            r.setReasons(reasons);
            recs.add(r);
        }

        recs.sort((a, b) -> b.getMatchScore() - a.getMatchScore());
        return recs;
    }
}
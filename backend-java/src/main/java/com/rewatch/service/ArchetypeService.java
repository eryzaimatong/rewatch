package com.rewatch.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;

/**
 * Names the user's taste profile in a sentence, for the profile page and share
 * card. Thresholds mirror the ones TasteProfile.jsx used to compute client-side,
 * moved server-side so the label is consistent wherever the profile is rendered.
 */
@Service
public class ArchetypeService {

    public record Result(String archetype, String blurb) {}

    public Result classify(Map<Trait, TraitNode> profile) {
        double comfort = val(profile, Trait.COMFORT);
        double nostalgia = val(profile, Trait.NOSTALGIA);
        double family = val(profile, Trait.FAMILY);
        double bitter = val(profile, Trait.BITTER);

        String archetype = "Emotional Storyteller";
        if (comfort >= 0.80 && nostalgia >= 0.80) {
            archetype = "Cozy Nostalgic";
        }
        if (bitter >= 0.75 && comfort <= 0.70) {
            archetype = "Bittersweet Realist";
        }
        if (family >= 0.85 && nostalgia >= 0.85) {
            archetype = "Found-Family Idealist";
        }

        List<String> lines = new ArrayList<>();
        if (comfort >= 0.75) {
            lines.add("You gravitate toward warmth and low-anxiety worlds.");
        }
        if (family >= 0.80) {
            lines.add("You care deeply about camaraderie and chosen-family bonds.");
        }
        if (bitter >= 0.70) {
            lines.add("You appreciate bittersweet endings that leave something unresolved.");
        }
        if (nostalgia >= 0.80) {
            lines.add("You love stories grounded in memory and looking back at simpler times.");
        }
        if (lines.isEmpty()) {
            lines.add("You enjoy balanced storytelling across multiple emotional tones.");
        }

        return new Result(archetype, String.join(" ", lines));
    }

    private double val(Map<Trait, TraitNode> profile, Trait t) {
        TraitNode n = profile.get(t);
        return n == null ? 0.5 : n.getVal();
    }
}

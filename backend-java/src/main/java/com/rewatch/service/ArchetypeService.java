package com.rewatch.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.rewatch.model.Trait;
import com.rewatch.model.TraitNode;

/**
 * Names the user's taste profile in a sentence, for the profile page and share
 * card — the single most-screenshotted surface in the app, so the name itself
 * is the product here, not a label on top of one.
 *
 * Originally four fixed combos plus one static fallback string, reading only
 * 4 of the 10 traits (comfort/nostalgia/family/bitter) — growth, pacing,
 * humor, romance, intensity, and hope never factored in at all, and most
 * profiles that didn't happen to clear one of those four specific,
 * fairly-high thresholds landed on the same generic "Emotional Storyteller"
 * regardless of what actually made their taste distinctive. Expanded to a
 * larger set of two-trait combos covering the full vocabulary, plus a
 * single-highest-trait fallback tier (one label per Trait) instead of one
 * catch-all string, so a "balanced" profile still gets something specific to
 * whichever axis actually stands out furthest, rather than nothing at all.
 *
 * Order matters: checked top to bottom, first match wins, most-specific
 * (highest thresholds / most traits) combos first — same reasoning as the
 * original's if-cascade, just with substantially more coverage.
 */
@Service
public class ArchetypeService {

    public record Result(String archetype, String blurb) {}

    private record Combo(String archetype, String blurb, java.util.function.Predicate<Vals> matches) {}

    private record Vals(double comfort, double nostalgia, double family, double bitter,
                        double growth, double pacing, double humor, double romance,
                        double intensity, double hope) {}

    private static final List<Combo> COMBOS = List.of(
            new Combo("Cozy Nostalgic",
                    "You gravitate toward warmth, memory, and low-anxiety worlds.",
                    v -> v.comfort() >= 0.80 && v.nostalgia() >= 0.80),
            new Combo("Found-Family Idealist",
                    "Ensemble bonds and chosen-family warmth are what you're really watching for.",
                    v -> v.family() >= 0.85 && v.nostalgia() >= 0.85),
            new Combo("Golden Retriever Energy",
                    "Hopeful, gentle, and easy to delight — you want a story that's rooting for everyone.",
                    v -> v.hope() >= 0.75 && v.comfort() >= 0.75 && v.intensity() <= 0.50),
            new Combo("Doom Scroller",
                    "Bleak endings don't scare you off — if anything, you're here for exactly that.",
                    v -> v.intensity() >= 0.75 && v.hope() <= 0.45),
            new Combo("Bittersweet Realist",
                    "You appreciate endings that leave something unresolved over a tidy bow.",
                    v -> v.bitter() >= 0.75 && v.comfort() <= 0.70),
            new Combo("Heartbreak Connoisseur",
                    "A romance that actually hurts a little is doing its job, as far as you're concerned.",
                    v -> v.romance() >= 0.70 && v.bitter() >= 0.65),
            new Combo("Serotonin Chaser",
                    "Light, funny, and romantic — you watch to feel good, on purpose.",
                    v -> v.humor() >= 0.75 && v.romance() >= 0.70),
            new Combo("Main Character in Crisis",
                    "You're here for the transformation arc, and you want it to cost the character something.",
                    v -> v.growth() >= 0.75 && v.intensity() >= 0.65),
            new Combo("Slow-Burn Believer",
                    "You'll wait for it — patient pacing and a real character arc beat a quick payoff every time.",
                    v -> v.pacing() >= 0.75 && v.growth() >= 0.65),
            new Combo("Cliffhanger Addict",
                    "Quick pacing and real stakes — you want the next episode queued up before this one ends.",
                    v -> v.pacing() <= 0.35 && v.intensity() >= 0.65),
            new Combo("Vibes Over Plot",
                    "Low stakes, high charm — you're not here to be devastated, you're here to have a good time.",
                    v -> v.humor() >= 0.70 && v.bitter() <= 0.45 && v.intensity() <= 0.50),
            new Combo("Nostalgia Rerun Enjoyer",
                    "Familiar and funny is a winning combo for you — comfort food with jokes.",
                    v -> v.nostalgia() >= 0.70 && v.humor() >= 0.65));

    /** One label per Trait, used when nothing above matched — see class javadoc. */
    private static final Map<Trait, String> SOLO_ARCHETYPE = Map.ofEntries(
            Map.entry(Trait.FAMILY, "Found-Family Softie"),
            Map.entry(Trait.NOSTALGIA, "Memory Lane Regular"),
            Map.entry(Trait.GROWTH, "Character Arc Believer"),
            Map.entry(Trait.PACING, "Slow Cinema Loyalist"),
            Map.entry(Trait.HUMOR, "Comic Relief Seeker"),
            Map.entry(Trait.ROMANCE, "Hopeless Romantic"),
            Map.entry(Trait.INTENSITY, "Thrill Chaser"),
            Map.entry(Trait.HOPE, "Eternal Optimist"),
            Map.entry(Trait.BITTER, "Bittersweet Truther"),
            Map.entry(Trait.COMFORT, "Comfort-Watch Curator"));

    /** How far above neutral (0.5) the single highest trait must sit before the solo fallback bothers naming it — below this, the profile reads as genuinely flat rather than quietly favoring one axis. */
    private static final double SOLO_THRESHOLD = 0.62;

    public Result classify(Map<Trait, TraitNode> profile) {
        Vals v = new Vals(
                val(profile, Trait.COMFORT), val(profile, Trait.NOSTALGIA), val(profile, Trait.FAMILY),
                val(profile, Trait.BITTER), val(profile, Trait.GROWTH), val(profile, Trait.PACING),
                val(profile, Trait.HUMOR), val(profile, Trait.ROMANCE), val(profile, Trait.INTENSITY),
                val(profile, Trait.HOPE));

        for (Combo combo : COMBOS) {
            if (combo.matches().test(v)) {
                return new Result(combo.archetype(), combo.blurb());
            }
        }

        Trait topTrait = null;
        double topVal = SOLO_THRESHOLD;
        for (Trait t : Trait.values()) {
            double val = val(profile, t);
            if (val > topVal) {
                topVal = val;
                topTrait = t;
            }
        }
        if (topTrait != null) {
            return new Result(SOLO_ARCHETYPE.get(topTrait),
                    "Nothing extreme yet, but " + topTrait.label().toLowerCase() + " is where your taste leans hardest.");
        }

        return new Result("Undecided Cinephile",
                "Your taste is genuinely balanced across the board so far — rate a few more titles and a sharper picture starts to form.");
    }

    private double val(Map<Trait, TraitNode> profile, Trait t) {
        TraitNode n = profile.get(t);
        return n == null ? 0.5 : n.getVal();
    }
}

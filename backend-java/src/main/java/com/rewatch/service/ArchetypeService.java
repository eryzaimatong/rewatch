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

    /** Shown until there is real signal. Public so callers (and tests) never hard-code the string. */
    public static final String UNDECIDED = "The 'You Pick' Friend";

    private static final List<Combo> COMBOS = List.of(
            new Combo("Brain Smooth, Vibes Immaculate",
                    "Warm, familiar, zero anxiety. You rewatch the comfort movie like it owes you nothing and gives you everything.",
                    v -> v.comfort() >= 0.80 && v.nostalgia() >= 0.80),
            new Combo("Found Family or Nothing",
                    "If the group isn't a dysfunctional chosen family who'd die for each other by act three, you're checking your phone.",
                    v -> v.family() >= 0.85 && v.nostalgia() >= 0.85),
            new Combo("Golden Retriever Energy",
                    "Hopeful, gentle, easy to delight. You want a story that's rooting for everyone, and you'll cry at the part where it does.",
                    v -> v.hope() >= 0.75 && v.comfort() >= 0.75 && v.intensity() <= 0.50),
            new Combo("Sleep Paralysis Demon's BFF",
                    "Bleak ending? Cute. You watch the scary thing, ask if there's a sequel, and your nightmares have started following you back.",
                    v -> v.intensity() >= 0.75 && v.hope() <= 0.45),
            new Combo("Wallowing in 4K",
                    "An ending that hurts a little means it did its job. You'd just like the heartbreak in HDR, please.",
                    v -> v.bitter() >= 0.75 && v.comfort() <= 0.70),
            new Combo("Needs a Hug (and Therapy)",
                    "You watch love stories specifically to get your heart broken in a controlled environment. Respect, and also: drink some water.",
                    v -> v.romance() >= 0.70 && v.bitter() >= 0.65),
            new Combo("Enemies-to-Lovers Truther",
                    "They hate each other for two acts, then it's the rain scene. You've seen it a hundred times and you'd watch it a hundred more.",
                    v -> v.romance() >= 0.70 && v.intensity() >= 0.60),
            new Combo("Serotonin Speedrunner",
                    "Funny, flirty, zero homework. You watch to feel good on purpose, and honestly, same.",
                    v -> v.humor() >= 0.75 && v.romance() >= 0.70),
            new Combo("Main Character in Crisis",
                    "You want the transformation arc, and you want it to cost the character everything. Therapy would be cheaper, but go off.",
                    v -> v.growth() >= 0.75 && v.intensity() >= 0.65),
            new Combo("Patience Is a Love Language",
                    "You'll wait forty minutes for one look across a room and call it cinema. You're not wrong.",
                    v -> v.pacing() >= 0.75 && v.growth() >= 0.65),
            new Combo("Final Girl Energy",
                    "Fast, tense, and you're always three steps ahead of the plot. You'd survive the third act and the sequel.",
                    v -> v.pacing() <= 0.35 && v.intensity() >= 0.65),
            new Combo("Vibes Over Plot",
                    "Plot is optional. Charm, aesthetic and a great soundtrack are not. You're here for the mood, not the minutes.",
                    v -> v.humor() >= 0.70 && v.bitter() <= 0.45 && v.intensity() <= 0.50),
            new Combo("Rewatching It for the 47th Time",
                    "Familiar plus funny is your love language. You quote the movie better than the actors do.",
                    v -> v.nostalgia() >= 0.70 && v.humor() >= 0.65),
            new Combo("Lore Architect",
                    "You don't watch stories, you map them. There's a spreadsheet. There are theories. We've seen the corkboard.",
                    v -> v.pacing() >= 0.65 && v.growth() >= 0.65));

    /** One label per Trait, used when nothing above matched — see class javadoc. */
    private static final Map<Trait, Result> SOLO_ARCHETYPE = Map.ofEntries(
            Map.entry(Trait.FAMILY, new Result("Chosen Family Stan",
                    "A ragtag group that learns to love each other? Say less. You're already crying.")),
            Map.entry(Trait.NOSTALGIA, new Result("Back in My Day (Age 19)",
                    "Everything was better in the era you were emotionally formed in. We won't argue.")),
            Map.entry(Trait.GROWTH, new Result("Character Arc Gremlin",
                    "You're here for the glow-up. Redemption arcs are your cardio.")),
            Map.entry(Trait.PACING, new Result("Pretends to Understand Interstellar",
                    "You sat through the three-hour one with the long takes and said \"the cinematography, though.\" We believe you. Mostly.")),
            Map.entry(Trait.HUMOR, new Result("Here for the Bit",
                    "If it isn't at least a little funny, you're mentally elsewhere. Joke density is your love language.")),
            Map.entry(Trait.ROMANCE, new Result("Professional Yearner",
                    "Longing glances, rain, an unsent text. You could do this for a living, and honestly, you kind of do.")),
            Map.entry(Trait.INTENSITY, new Result("Adrenaline Goblin",
                    "Your heart rate is basically a subscription. Stakes high, popcorn forgotten.")),
            Map.entry(Trait.HOPE, new Result("Hope-Core Menace",
                    "You believe in the happy ending the way other people believe in horoscopes. Aggressively.")),
            Map.entry(Trait.BITTER, new Result("Professionally Wistful",
                    "Happy endings are fine. Aching ones are home.")),
            Map.entry(Trait.COMFORT, new Result("Blanket Burrito Cinephile",
                    "Your watchlist is a warm bath. No notes, all snacks.")));

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
            return SOLO_ARCHETYPE.get(topTrait);
        }

        return new Result(UNDECIDED,
                "Your taste is a blank canvas. Rate a few movies so the algorithm can stop sweating.");
    }

    /** Every possible result, for copy review and length checks. */
    List<Result> allResultsForReview() {
        List<Result> all = new java.util.ArrayList<>();
        COMBOS.forEach(c -> all.add(new Result(c.archetype(), c.blurb())));
        all.addAll(SOLO_ARCHETYPE.values());
        all.add(new Result(UNDECIDED, "Your taste is a blank canvas. Rate a few movies so the algorithm can stop sweating."));
        return all;
    }

    private double val(Map<Trait, TraitNode> profile, Trait t) {
        TraitNode n = profile.get(t);
        return n == null ? 0.5 : n.getVal();
    }
}

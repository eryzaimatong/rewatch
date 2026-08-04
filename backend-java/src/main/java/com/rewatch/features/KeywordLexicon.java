package com.rewatch.features;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * TMDB keyword (and free text) -> trait nudge.
 *
 * Two tiers so the long tail is covered without a 5,000-row table:
 *   1. EXACT   — hand-authored entries for high-frequency emotional keywords.
 *   2. PATTERN — regex rules over the keyword string, which also let us scan a
 *                movie's overview text when it has too few usable keywords.
 *
 * Keeping the raw keyword names cached on the Title row means this lexicon can be
 * re-tuned and every vector re-derived with zero TMDB requests.
 */
public final class KeywordLexicon {

    private KeywordLexicon() {}

    /** Bump when the lexicon changes so stale vectors can be found and recomputed. */
    public static final int VERSION = 1;

    private static final Map<String, TraitDelta> EXACT = new HashMap<>();
    private static final List<Rule> PATTERNS = new ArrayList<>();

    private record Rule(Pattern pattern, TraitDelta delta) {}

    private static void exact(String kw, TraitDelta.Builder b) {
        EXACT.put(kw, b.build());
    }

    private static void pattern(String regex, TraitDelta.Builder b) {
        PATTERNS.add(new Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), b.build()));
    }

    static {
        // ---------- Found family / relationships ----------
        exact("friendship",        TraitDelta.of().family(0.55).comfort(0.30).hope(0.20));
        exact("found family",      TraitDelta.of().family(0.75).comfort(0.40).hope(0.25));
        exact("family relationships", TraitDelta.of().family(0.60).growth(0.25));
        exact("brother brother relationship", TraitDelta.of().family(0.55).growth(0.20));
        exact("sibling relationship", TraitDelta.of().family(0.55).growth(0.20));
        exact("father son relationship", TraitDelta.of().family(0.60).growth(0.35).bitter(0.20));
        exact("mother daughter relationship", TraitDelta.of().family(0.60).growth(0.35).bitter(0.20));
        exact("single parent",     TraitDelta.of().family(0.45).bitter(0.30).growth(0.25));
        exact("orphan",            TraitDelta.of().family(0.40).bitter(0.45).hope(0.15));
        exact("adoption",          TraitDelta.of().family(0.60).hope(0.30).bitter(0.20));
        exact("teamwork",          TraitDelta.of().family(0.45).hope(0.25));

        // ---------- Nostalgia / memory ----------
        exact("nostalgia",         TraitDelta.of().nostalgia(0.75).comfort(0.30).bitter(0.20));
        exact("childhood",         TraitDelta.of().nostalgia(0.65).family(0.30).comfort(0.25));
        exact("coming of age",     TraitDelta.of().growth(0.70).nostalgia(0.45).bitter(0.25).romance(0.20));
        exact("memory",            TraitDelta.of().nostalgia(0.55).bitter(0.25).pacing(0.20));
        exact("small town",        TraitDelta.of().nostalgia(0.50).comfort(0.35).pacing(0.30));
        exact("high school",       TraitDelta.of().nostalgia(0.45).growth(0.35).romance(0.25).humor(0.20));
        exact("summer",            TraitDelta.of().nostalgia(0.40).comfort(0.30).hope(0.20));
        exact("time travel",       TraitDelta.of().nostalgia(0.40).intensity(0.20));

        // ---------- Growth / redemption ----------
        exact("redemption",        TraitDelta.of().growth(0.70).hope(0.40).bitter(0.30));
        exact("self discovery",    TraitDelta.of().growth(0.70).pacing(0.30).hope(0.25));
        exact("coming out",        TraitDelta.of().growth(0.55).bitter(0.25).hope(0.25));
        exact("mentor",            TraitDelta.of().growth(0.50).family(0.30).hope(0.25));
        exact("second chance",     TraitDelta.of().growth(0.45).hope(0.45).romance(0.20));
        exact("perseverance",      TraitDelta.of().growth(0.50).hope(0.45));

        // ---------- Grief / bittersweet ----------
        exact("grief",             TraitDelta.of().bitter(0.75).intensity(0.40).comfort(-0.35).humor(-0.30));
        exact("loss of loved one", TraitDelta.of().bitter(0.75).intensity(0.40).comfort(-0.30).humor(-0.25));
        exact("death",             TraitDelta.of().bitter(0.55).intensity(0.35).comfort(-0.30).humor(-0.20));
        exact("terminal illness",  TraitDelta.of().bitter(0.80).intensity(0.45).comfort(-0.30).hope(-0.20));
        exact("suicide",           TraitDelta.of().bitter(0.70).intensity(0.55).comfort(-0.55).hope(-0.40).humor(-0.35));
        exact("depression",        TraitDelta.of().bitter(0.60).intensity(0.40).comfort(-0.40).humor(-0.30));
        exact("loneliness",        TraitDelta.of().bitter(0.60).comfort(-0.30).family(-0.25).pacing(0.25));
        exact("melancholy",        TraitDelta.of().bitter(0.65).pacing(0.35).comfort(-0.20).humor(-0.25));
        exact("sacrifice",         TraitDelta.of().bitter(0.55).intensity(0.40).hope(0.20));
        exact("unrequited love",   TraitDelta.of().romance(0.55).bitter(0.60).hope(-0.25));

        // ---------- Comfort / healing ----------
        // Common single-word aliases users actually type in search, distinct
        // from the multi-word TMDB-keyword phrasing above — autocomplete needs
        // these even though a keyword-matching pass over TMDB data rarely does.
        exact("comfort",           TraitDelta.of().comfort(0.65).hope(0.25).intensity(-0.25));
        exact("cozy",              TraitDelta.of().comfort(0.65).pacing(0.35).intensity(-0.30));
        exact("sad",               TraitDelta.of().bitter(0.55).comfort(-0.25).humor(-0.20));
        exact("happy",             TraitDelta.of().hope(0.55).comfort(0.30).bitter(-0.30));
        exact("funny",             TraitDelta.of().humor(0.65).comfort(0.20).intensity(-0.20));
        exact("scary",             TraitDelta.of().intensity(0.65).comfort(-0.55).hope(-0.20));
        exact("romantic",          TraitDelta.of().romance(0.60).hope(0.20));
        exact("nostalgic",         TraitDelta.of().nostalgia(0.65).comfort(0.20));

        exact("slice of life",     TraitDelta.of().comfort(0.65).pacing(0.55).intensity(-0.35).family(0.25));
        exact("healing",           TraitDelta.of().comfort(0.60).hope(0.50).growth(0.35));
        exact("friendship goals",  TraitDelta.of().family(0.50).comfort(0.35).humor(0.25));
        exact("cooking",           TraitDelta.of().comfort(0.55).family(0.30).pacing(0.25));
        exact("food",              TraitDelta.of().comfort(0.45).family(0.25));
        exact("countryside",       TraitDelta.of().comfort(0.45).pacing(0.40).nostalgia(0.30));
        exact("animal",            TraitDelta.of().comfort(0.40).family(0.25).humor(0.20));
        exact("christmas",         TraitDelta.of().comfort(0.60).family(0.45).nostalgia(0.40).hope(0.30));

        // ---------- Romance ----------
        exact("love",              TraitDelta.of().romance(0.60).hope(0.20).comfort(0.15));
        exact("first love",        TraitDelta.of().romance(0.70).nostalgia(0.50).bitter(0.30).growth(0.25));
        exact("love triangle",     TraitDelta.of().romance(0.65).intensity(0.30).bitter(0.20));
        exact("slow burn romance", TraitDelta.of().romance(0.70).pacing(0.65).comfort(0.20));
        exact("marriage",          TraitDelta.of().romance(0.50).family(0.35).growth(0.20));
        exact("wedding",           TraitDelta.of().romance(0.55).family(0.35).hope(0.30).humor(0.20));
        exact("divorce",           TraitDelta.of().romance(0.30).bitter(0.55).family(0.20).hope(-0.20));

        // ---------- Intensity / darkness ----------
        exact("violence",          TraitDelta.of().intensity(0.65).comfort(-0.55).humor(-0.30).hope(-0.20));
        exact("murder",            TraitDelta.of().intensity(0.60).comfort(-0.55).bitter(0.30).humor(-0.30));
        exact("revenge",           TraitDelta.of().intensity(0.65).bitter(0.45).comfort(-0.45).hope(-0.30));
        exact("torture",           TraitDelta.of().intensity(0.80).comfort(-0.75).hope(-0.45).humor(-0.40));
        exact("gore",              TraitDelta.of().intensity(0.75).comfort(-0.75).humor(-0.35).family(-0.30));
        exact("survival",          TraitDelta.of().intensity(0.60).hope(0.20).comfort(-0.35).pacing(-0.20));
        exact("dystopia",          TraitDelta.of().intensity(0.50).hope(-0.50).comfort(-0.45).bitter(0.35));
        exact("post-apocalyptic",  TraitDelta.of().intensity(0.50).hope(-0.40).comfort(-0.45).bitter(0.35));
        exact("war",               TraitDelta.of().intensity(0.55).bitter(0.55).comfort(-0.50).humor(-0.30));
        exact("psychological thriller", TraitDelta.of().intensity(0.65).pacing(0.30).comfort(-0.55).humor(-0.30));
        exact("class differences", TraitDelta.of().bitter(0.50).intensity(0.35).comfort(-0.30));
        exact("social commentary", TraitDelta.of().bitter(0.40).growth(0.25).intensity(0.25).comfort(-0.20));

        // ---------- Humor ----------
        exact("satire",            TraitDelta.of().humor(0.65).bitter(0.30).intensity(-0.15));
        exact("parody",            TraitDelta.of().humor(0.75).intensity(-0.30).bitter(-0.25));
        exact("dark comedy",       TraitDelta.of().humor(0.60).bitter(0.45).comfort(-0.20));
        exact("slapstick",         TraitDelta.of().humor(0.80).comfort(0.30).intensity(-0.35).bitter(-0.30));
        exact("workplace comedy",  TraitDelta.of().humor(0.65).family(0.35).comfort(0.35));

        // ---------- Hope ----------
        exact("hope",              TraitDelta.of().hope(0.70).comfort(0.25).growth(0.20));
        exact("underdog",          TraitDelta.of().hope(0.60).growth(0.45).family(0.20));
        exact("happy ending",      TraitDelta.of().hope(0.70).comfort(0.40).bitter(-0.40));
        exact("bittersweet ending", TraitDelta.of().bitter(0.75).hope(0.25).comfort(-0.15));
        exact("open ending",       TraitDelta.of().bitter(0.55).pacing(0.25).hope(-0.15));

        // ---------- Pacing ----------
        exact("slow burn",         TraitDelta.of().pacing(0.75).intensity(0.20).comfort(0.15));
        exact("one night",         TraitDelta.of().pacing(-0.35).intensity(0.30));
        exact("nonlinear timeline", TraitDelta.of().pacing(0.35).intensity(0.20));

        // ================= PATTERN FALLBACKS =================
        // Catch the long tail and let us scan overview text for the same signals.
        pattern("nostalg|retro|childhood|flashback|19[5-9]0s|20(0|1)0s|old days",
                TraitDelta.of().nostalgia(0.45).comfort(0.15));
        pattern("\\bfamil|brother|sister|father|mother|\\bson\\b|daughter|parent|grandmother|grandfather",
                TraitDelta.of().family(0.40).growth(0.15));
        pattern("friend|companion|crew|team|bond",
                TraitDelta.of().family(0.35).comfort(0.20));
        pattern("murder|kill|blood|torture|gore|brutal|massacre|horror|nightmare",
                TraitDelta.of().intensity(0.50).comfort(-0.45).humor(-0.20));
        pattern("romance|romantic|lover|crush|dating|falls? in love|affair",
                TraitDelta.of().romance(0.50).hope(0.15));
        pattern("grief|mourn|funeral|dying|dies|died|cancer|illness|orphan",
                TraitDelta.of().bitter(0.50).intensity(0.25).comfort(-0.25));
        pattern("hilarious|comedy|comedic|laugh|funny|humou?r",
                TraitDelta.of().humor(0.50).comfort(0.20).intensity(-0.20));
        pattern("hope|dream|inspire|triumph|overcome|redeem",
                TraitDelta.of().hope(0.45).growth(0.25));
        pattern("coming of age|grow(s|ing)? up|maturity|self-discovery|identity",
                TraitDelta.of().growth(0.50).nostalgia(0.20));
        pattern("quiet|gentle|tender|cozy|warm|heartwarming|wholesome",
                TraitDelta.of().comfort(0.50).pacing(0.25).intensity(-0.25));
        pattern("meditative|contemplative|leisurely|slow",
                TraitDelta.of().pacing(0.45).intensity(-0.15));
        pattern("explosion|chase|fight|battle|war|action-packed",
                TraitDelta.of().intensity(0.40).pacing(-0.35));
    }

    /** Exact hit first, then the first matching pattern. Null when nothing matches. */
    public static TraitDelta lookup(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String k = keyword.trim().toLowerCase(Locale.ROOT);

        TraitDelta hit = EXACT.get(k);
        if (hit != null) {
            return hit;
        }
        for (Rule r : PATTERNS) {
            if (r.pattern().matcher(k).find()) {
                return r.delta();
            }
        }
        return null;
    }

    /**
     * Curated mood phrases whose normalized form contains {@code prefix} — the
     * source for search autocomplete. Reuses the same lexicon that derives movie
     * vectors, so a suggestion the user picks is guaranteed to mean something to
     * the scorer, rather than a hardcoded list that could drift from it.
     */
    public static List<String> suggestPhrases(String prefix, int limit) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        String needle = prefix.trim().toLowerCase(Locale.ROOT);
        return EXACT.keySet().stream()
                .filter(k -> k.contains(needle))
                .sorted(java.util.Comparator.comparingInt(String::length))
                .limit(limit)
                .toList();
    }

    /**
     * Every pattern that fires anywhere in a block of free text. Used as tier 2 of
     * the fallback ladder, when a title has too few usable keywords — the overview
     * is already fetched, so this costs nothing.
     */
    public static List<TraitDelta> scanText(String text) {
        List<TraitDelta> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (Rule r : PATTERNS) {
            if (r.pattern().matcher(text).find()) {
                out.add(r.delta());
            }
        }
        return out;
    }
}

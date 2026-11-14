/**
 * Structured stand-ins shown when the network can't deliver in time. They are
 * deliberately NEUTRAL and always flagged (`isFallback`) so the UI says so:
 * showing an invented "Cozy Nostalgic" profile as if it were the user's own
 * TasteDNA would be a lie, whereas a calm, clearly-labelled starting profile
 * is a trustworthy place to wait from.
 */
export const TRAIT_LABELS = {
  nostalgia: "Nostalgia & Memory",
  family: "Found Family",
  growth: "Character Growth",
  comfort: "Comfort & Warmth",
  pacing: "Slow Burn Pacing",
  hope: "Hopeful Payoff",
  bitter: "Bittersweet Drama",
  humor: "Humor & Lightheartedness",
  romance: "Romance",
  intensity: "Emotional Intensity"
};

export const TASTE_PROFILE_FALLBACK = {
  traits: Object.fromEntries(
    Object.entries(TRAIT_LABELS).map(([k, label]) => [k, { val: 0.5, conf: 0.1, trend: 0, evid: 0, label }])
  ),
  archetype: "Emotional Storyteller",
  archetypeBlurb: "You enjoy balanced storytelling across multiple emotional tones.",
  meanConfidence: 0.1,
  ratingCount: 0,
  personalized: false
};

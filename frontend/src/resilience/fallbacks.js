/**
 * Structured stand-ins shown when the network can't deliver in time. They are
 * deliberately NEUTRAL and always flagged (`isFallback`) so the UI says so:
 * showing an invented "Brain Smooth, Vibes Immaculate" profile as if it were the user's own
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
  archetype: "The 'You Pick' Friend",
  archetypeBlurb: "Your taste is a blank canvas. Rate a few movies so the algorithm can stop sweating.",
  meanConfidence: 0.1,
  ratingCount: 0,
  personalized: false
};

/**
 * Shown, clearly labelled and non-interactive, when the feed can't be
 * fetched in time: well-known films with honest, spoiler-free one-liners.
 * No match scores (a fabricated score is worse than none) and no ids that
 * could be mistaken for catalog rows.
 */
const PREVIEW = [
  ["Spirited Away", 2001, ["Animation", "Fantasy"], "A girl finds her courage in a bathhouse for spirits."],
  ["Paddington 2", 2017, ["Family", "Comedy"], "Kindness, marmalade and a very good heist."],
  ["Past Lives", 2023, ["Drama", "Romance"], "Two people, two countries, and the lives they didn't live."],
  ["The Grand Budapest Hotel", 2014, ["Comedy", "Adventure"], "A concierge, a lobby boy, and a stolen painting."],
  ["Your Name", 2016, ["Animation", "Romance"], "Two strangers swap bodies and chase a connection across time."],
  ["Knives Out", 2019, ["Mystery", "Comedy"], "A famous novelist dies and everyone has a motive."],
  ["Little Miss Sunshine", 2006, ["Comedy", "Drama"], "One road trip, one van, one very hopeful family."],
  ["Arrival", 2016, ["Sci-Fi", "Drama"], "A linguist is asked to find out why they came."]
];

export const FEED_FALLBACK = {
  preview: true,
  notice: "",
  languages: [],
  movies: PREVIEW.map(([title, year, genres, overview], i) => ({
    id: `preview-${i}`,
    title,
    releaseYear: year,
    genres,
    overview,
    isPreview: true
  }))
};

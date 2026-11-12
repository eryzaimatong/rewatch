/**
 * Mood palette from the board's "Same soul. Different shades." page.
 * `ring`/`tint` drive only a soft aura around the chosen card — Ree herself
 * is never recolored, and primary actions stay Teal. `vibe` / `genre` map
 * each feeling onto the filters the recommendation API already understands.
 */
export const MOODS = [
  { key: "happy", name: "Happy", blurb: "For stories that lift you up.", vibe: "Found Family", ring: "#b8e6d5", tint: "69,197,180", chips: ["#b8e6d5", "#45c5b4"] },
  { key: "melancholic", name: "Melancholic", blurb: "For a beautifully bittersweet evening.", vibe: "Bittersweet", ring: "#d6c9f5", tint: "166,164,237", chips: ["#a6a4ed", "#d6c9f5"] },
  { key: "romantic", name: "Romantic", blurb: "For the butterflies and the slow burn.", vibe: "Romance", ring: "#e8bdcf", tint: "232,189,207", chips: ["#e8bdcf", "#d6c9f5"] },
  { key: "mysterious", name: "Mysterious", blurb: "For the thrill of one more clue.", genre: "Mystery", ring: "#9d99d8", tint: "119,115,178", chips: ["#287c82", "#7773b2"] },
  { key: "adventurous", name: "Adventurous", blurb: "For a little courage and a new world.", genre: "Adventure", ring: "#f3d9a5", tint: "243,217,165", chips: ["#45c5b4", "#f3d9a5"] },
  { key: "calm", name: "Calm", blurb: "For softer rhythms and a deep breath.", vibe: "Comfort", ring: "#bfdded", tint: "191,221,237", chips: ["#b8e6d5", "#bfdded"] }
];


// Which of Ree's poses reacts to each mood the visitor picks.
export const MOOD_REE_POSE = {
  happy: "excited",
  melancholic: "sad",
  romantic: "lovestory",
  mysterious: "detective",
  adventurous: "cinema",
  calm: "sleepy"
};

// Ree reacts to what is on screen: the pick's genre decides her pose.
export function poseForGenres(genres = []) {
  const g = genres.join(" ").toLowerCase();
  if (/romance|love/.test(g)) return "lovestory";
  if (/mystery|thriller|crime|noir/.test(g)) return "detective";
  if (/horror/.test(g)) return "surprised";
  if (/comedy|family|animation/.test(g)) return "excited";
  if (/documentary|history/.test(g)) return "thinking";
  if (/drama/.test(g)) return "curious";
  if (/fantasy|science|sci-fi|adventure|action/.test(g)) return "cinema";
  return "default";
}


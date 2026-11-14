import { test as base, expect } from "@playwright/test";

const TRAIT_KEYS = ["comfort", "family", "nostalgia", "hope", "romance", "bitter", "growth", "pacing", "humor", "intensity"];
const traits = (val, conf, evid) =>
  Object.fromEntries(TRAIT_KEYS.map((k) => [k, { val, conf, trend: 0, evid, label: k }]));

/** Brand-new account: zero ratings, nothing learned yet. */
export const BLANK_PROFILE = {
  traits: traits(0.5, 0.1, 0),
  archetype: "The 'You Pick' Friend",
  archetypeBlurb: "Your taste is a blank canvas. Rate a few movies so the algorithm can stop sweating.",
  meanConfidence: 0.1,
  ratingCount: 0,
  personalized: false
};

/** After "Rebuild my TasteDNA" has replayed a rating history. */
export const POPULATED_PROFILE = {
  traits: { ...traits(0.6, 0.8, 12), romance: { val: 0.9, conf: 0.8, trend: 1, evid: 12, label: "romance" } },
  archetype: "Professional Yearner",
  archetypeBlurb: "Longing glances, rain, an unsent text. You could do this for a living, and honestly, you kind of do.",
  meanConfidence: 0.8,
  ratingCount: 24,
  personalized: true
};

const MOVIES = Array.from({ length: 6 }, (_, i) => ({
  id: 100 + i,
  titleId: 100 + i,
  title: `Fixture Film ${i + 1}`,
  year: 2020 + (i % 4),
  synopsis: "A story chosen for how you feel.",
  genres: ["Drama", "Romance"],
  matchScore: 90 - i * 3
}));

/**
 * Replaces the whole backend. `state.requests` records every call so a test can
 * assert what the app asked for; `state.generated` flips once TasteDNA is rebuilt.
 */
export async function mockBackend(page) {
  const state = { requests: [], generated: false };
  await page.route("**/api/**", async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname;
    const json = (body, status = 200) =>
      route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    state.requests.push(`${req.method()} ${path}${url.search}`);

    if (path === "/api/health") return json({ status: "ok", db: "up" });
    if (path === "/api/auth/login") return json({ token: "e2e-token", userId: 42, username: "mara", onboarded: true });
    if (path === "/api/tastedna/profile/42") return json(state.generated ? POPULATED_PROFILE : BLANK_PROFILE);
    if (path === "/api/tastedna/replay/42") {
      state.generated = true;
      return json({ status: "success" });
    }
    if (path === "/api/movies/popular") return json(MOVIES);
    return json([]);
  });
  return state;
}

export const test = base;
export { expect };

import { useEffect, useMemo, useState } from "react";
import { motion } from "framer-motion";
import { authHeaders } from "./auth";
import { BASE, gettitles, gettastedna } from "./api";
import { playChime } from "./sound";
import { FAV_PAGE_SIZE, computeVisibleFavTitles } from "./onboardingUtils";
import "./App.css";

// A staggered reveal for the one moment this whole wizard has been building
// toward — "You are... [Archetype]" was previously static, appearing fully
// formed on the very first frame with zero buildup, on the single highest-
// payoff screen in onboarding. Each line lands a beat after the last so the
// archetype name (the actual reveal) gets its own moment rather than
// competing with everything around it for attention.
const revealParent = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.12, delayChildren: 0.1 } }
};
const revealItem = {
  hidden: { opacity: 0, y: 14 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.45, ease: "easeOut" } }
};
const revealArchetype = {
  hidden: { opacity: 0, scale: 0.82 },
  visible: { opacity: 1, scale: 1, transition: { duration: 0.55, ease: [0.34, 1.56, 0.64, 1] } }
};

const GENRES = [
  "Drama", "Sci-Fi", "Slice of Life", "Romance", "Thriller", "Anime", "Comedy", "Mystery"
];

// Matches OnboardingService.TROPE_SENTIMENT's keys (lowercased server-side).
const TROPES = [
  "Character Growth", "Slow Burn", "Found Family", "Enemies to Lovers",
  "Psychological", "Bittersweet Endings", "Hopeful Payoffs", "Comfort Watch"
];

// Matches OnboardingService.EMOTIONAL_GOAL_SENTIMENT's keys.
const EMOTIONAL_GOALS = ["Laugh", "Cry", "Think", "Escape", "Relax", "Feel Hope"];

// Matches OnboardingService.DEALBREAKER_SENTIMENT's keys — "Poor CGI" is
// deliberately absent, there's no trait axis for production quality.
// "Open Ending" and "Unresolved Ending" route to the identical trait check
// server-side (Recommender.DEALBREAKER_THRESHOLDS: BITTER >= 0.65 either
// way) — shown as one chip so it doesn't read as two distinct dealbreakers,
// but still sends both underlying keys so nothing server-side needs to change.
const BREAKER_CHIPS = [
  { label: "Excessive Gore", keys: ["Excessive Gore"] },
  { label: "Sad Ending", keys: ["Sad Ending"] },
  { label: "Open / Unresolved Ending", keys: ["Open Ending", "Unresolved Ending"] },
  { label: "Love Triangle", keys: ["Love Triangle"] },
  { label: "Jump Scares", keys: ["Jump Scares"] },
  { label: "Slow Start", keys: ["Slow Start"] },
  { label: "Animal Death", keys: ["Animal Death"] },
  { label: "Cheating", keys: ["Cheating"] }
];

const FAV_TABS = [
  { key: "movie", label: "Movies" },
  { key: "show", label: "Shows" },
  { key: "anime", label: "Anime" }
];

const STEP_TITLES = [
  "Your Comfort Movies", "Your Story DNA", "Your Emotional Palette",
  "Your Genre Instincts", "Your Dealbreakers & Pace"
];

const ANALYZING_LINES = [
  "Reading your favorites...",
  "Mapping your Story DNA...",
  "Weighing what you avoid...",
  "Almost there..."
];

const TMDB_POSTER_THUMB_BASE = "https://image.tmdb.org/t/p/w154";
// A plain color swatch, not a placehold.co URL — placehold.co treats a
// whitespace `text=` param as "none given" and falls back to rendering its
// own "154 × 231" dimension label instead of staying blank, which is exactly
// what real, unrelated dimension text was doing here in production. An
// inline SVG data URI has no such fallback behavior and no network
// dependency for something that's just a solid rectangle.
const FALLBACK_POSTER_THUMB =
  "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='154' height='231'%3E%3Crect width='100%25' height='100%25' fill='%23191a21'/%3E%3C/svg%3E";

function posterThumbUrl(t) {
  return t.poster ? `${TMDB_POSTER_THUMB_BASE}${t.poster}` : FALLBACK_POSTER_THUMB;
}

function handlePosterThumbError(event) {
  event.currentTarget.onerror = null;
  event.currentTarget.src = FALLBACK_POSTER_THUMB;
}

function bucketFor(type) {
  if (type === "movie") return "movie";
  if (type === "anime") return "anime";
  return "show";
}

// A separate component so the cycling-text interval's lifecycle matches this
// screen's own mount/unmount, not the whole wizard's.
function AnalyzingScreen() {
  const [lineIdx, setlineIdx] = useState(0);

  useEffect(() => {
    const id = setInterval(() => {
      setlineIdx((i) => (i + 1) % ANALYZING_LINES.length);
    }, 700);
    return () => clearInterval(id);
  }, []);

  return (
    <div className="onboard-analyzing">
      <div className="loading-orb onboard-analyzing-orb" />
      <p className="onboard-analyzing-text">{ANALYZING_LINES[lineIdx]}</p>
    </div>
  );
}

function RevealScreen({ archetype, archetypeBlurb, onContinue }) {
  useEffect(() => {
    // Timed to land roughly when the archetype name itself scales in (see
    // revealArchetype's delay: delayChildren + staggerChildren*2 ≈ 0.34s) —
    // the chime should mark the actual reveal, not the screen just appearing.
    const id = setTimeout(playChime, 340);
    return () => clearTimeout(id);
  }, []);

  return (
    <motion.div className="onboard-reveal" variants={revealParent} initial="hidden" animate="visible">
      <motion.span className="onboard-reveal-mark" variants={revealItem} aria-hidden="true" />
      <motion.p className="onboard-reveal-kicker" variants={revealItem}>You are...</motion.p>
      <motion.h2 className="onboard-reveal-archetype" variants={revealArchetype}>{archetype}</motion.h2>
      <motion.p className="onboard-reveal-blurb" variants={revealItem}>{archetypeBlurb}</motion.p>
      <motion.button type="button" onClick={onContinue} className="btn-primary" variants={revealItem}>
        Continue to Re:Watch
      </motion.button>
    </motion.div>
  );
}

export default function Onboarding({ onFinish }) {
  const [step, setstep] = useState(1);
  const [catalog, setcatalog] = useState([]);
  const [favTab, setfavTab] = useState("movie");
  const [favQuery, setfavQuery] = useState("");
  const [favs, setfavs] = useState([]);
  const [tropes, settropes] = useState([]);
  const [emotionalGoals, setemotionalGoals] = useState([]);
  const [genres, setgenres] = useState({});
  const [avoid, setavoid] = useState([]);
  const [pacing, setpacing] = useState(3);
  const [intensity, setintensity] = useState(4);
  const [err, seterr] = useState("");
  const [submitting, setsubmitting] = useState(false);
  const [analyzing, setanalyzing] = useState(false);
  const [reveal, setreveal] = useState(null);

  const userid = localStorage.getItem("userId") || 1;

  useEffect(() => {
    gettitles().then(setcatalog);
  }, []);

  const favBuckets = useMemo(() => {
    const b = { movie: [], anime: [], show: [] };
    for (const t of catalog) {
      b[bucketFor(t.type)].push(t);
    }
    return b;
  }, [catalog]);

  // See onboardingUtils.js — capped to a page of well-known titles instead
  // of rendering a bucket that can run into the thousands at once.
  const visibleFavTitles = useMemo(
    () => computeVisibleFavTitles(favBuckets[favTab] || [], favQuery, favs),
    [favBuckets, favTab, favQuery, favs]
  );

  function togglefav(title) {
    setfavs((current) =>
      current.includes(title) ? current.filter((x) => x !== title) : [...current, title]
    );
  }

  function toggletrope(t) {
    settropes((current) =>
      current.includes(t) ? current.filter((x) => x !== t) : [...current, t]
    );
  }

  function togglegoal(g) {
    setemotionalGoals((current) =>
      current.includes(g) ? current.filter((x) => x !== g) : [...current, g]
    );
  }

  function setgenrerate(g, val) {
    setgenres((current) => ({ ...current, [g]: val }));
  }

  // A real, countable tally of concrete inputs given so far — not a modeled
  // "confidence" score, which would need the backend to actually compute one
  // mid-onboarding. This is just what's true: how many discrete choices this
  // profile is currently built from.
  const signalsCaptured =
    favs.length + tropes.length + emotionalGoals.length + avoid.length +
    Object.values(genres).filter((v) => v !== "Neutral").length;

  function toggleavoidChip(chip) {
    setavoid((current) => {
      const hasAny = chip.keys.some((k) => current.includes(k));
      if (hasAny) {
        return current.filter((x) => !chip.keys.includes(x));
      }
      return [...current, ...chip.keys.filter((k) => !current.includes(k))];
    });
  }

  async function doSubmit(payload) {
    setsubmitting(true);
    setanalyzing(true);

    const minDelay = new Promise((resolve) => setTimeout(resolve, 1200));
    const [res] = await Promise.all([
      fetch(`${BASE}/api/movies/onboard`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authHeaders() },
        body: JSON.stringify(payload)
      }).catch(() => null),
      minDelay
    ]);

    setsubmitting(false);

    if (!res || !res.ok) {
      setanalyzing(false);
      seterr("Could not save your taste profile. Please try again.");
      return;
    }

    const profile = await gettastedna(userid);
    setanalyzing(false);
    setreveal({
      archetype: profile?.archetype || "Emotional Storyteller",
      archetypeBlurb: profile?.archetypeBlurb || ""
    });
  }

  async function submitall() {
    seterr("");
    if (favs.length < 5) {
      seterr("Pick at least 5 favorites so we can seed your starting profile.");
      setstep(1);
      return;
    }
    await doSubmit({ userId: userid, favs, genres, avoid, tropes, emotionalGoals, pacing, intensity });
  }

  // A blank profile is an already-supported state, not a fabricated one — a
  // fresh account with no rating history and no onboarding answers both fall
  // back to the same neutral (all-0.5) TraitVector server-side (see
  // ProfileService). Skipping just reaches that state immediately instead of
  // forcing five mandatory steps before a first-time visitor can look around.
  async function skiponboarding() {
    seterr("");
    await doSubmit({ userId: userid, favs: [], genres: {}, avoid: [], tropes: [], emotionalGoals: [] });
  }

  if (reveal) {
    return (
      <div className="onboard-shell">
        <div className="page-panel onboard-panel">
          <RevealScreen
            archetype={reveal.archetype}
            archetypeBlurb={reveal.archetypeBlurb}
            onContinue={() => onFinish && onFinish()}
          />
        </div>
      </div>
    );
  }

  if (analyzing) {
    return (
      <div className="onboard-shell">
        <div className="page-panel onboard-panel">
          <AnalyzingScreen />
        </div>
      </div>
    );
  }

  return (
    <div className="onboard-shell">
      <div className="page-panel onboard-panel">
        <div className="onboard-header-row">
          <div>
            <span className="eyebrow">TasteDNA Onboarding</span>
            <h1 className="onboard-title">
              Step {step} of 5: {STEP_TITLES[step - 1]}
            </h1>
          </div>
          {/* A brand-new visitor's very first screen was a mandatory five-step
              wizard with no way out — "just let me look around" wasn't an
              option. Skipping seeds a neutral profile (see skiponboarding's
              comment) rather than blocking entry entirely. */}
          <button type="button" className="onboard-skip-link" onClick={skiponboarding} disabled={submitting}>
            Skip for now →
          </button>
        </div>

        <div className="onboard-progress">
          {[1, 2, 3, 4, 5].map((n) => (
            <div key={n} className={`onboard-progress-segment${n <= step ? " is-complete" : ""}`} />
          ))}
        </div>
        {signalsCaptured > 0 && (
          <p className="onboard-signal-count">
            {signalsCaptured} signal{signalsCaptured === 1 ? "" : "s"} captured so far
          </p>
        )}

        {step === 1 && (
          <div>
            <p className="onboard-intro">
              Pick at least 5 titles across movies, shows, and anime to seed your starting profile.
            </p>

            <div className="pill-row" style={{ marginBottom: "var(--sp-2)" }}>
              {FAV_TABS.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  className={`pill${favTab === tab.key ? " active" : ""}`}
                  onClick={() => setfavTab(tab.key)}
                >
                  {tab.label} ({favBuckets[tab.key]?.length ?? 0})
                </button>
              ))}
            </div>

            <input
              type="text"
              placeholder={`Search ${FAV_TABS.find((t) => t.key === favTab)?.label.toLowerCase()}...`}
              value={favQuery}
              onChange={(e) => setfavQuery(e.target.value)}
              style={{ marginBottom: "6px" }}
            />
            <p style={{ color: "var(--text-faint)", fontSize: "0.76rem", margin: "0 0 var(--sp-2)" }}>
              Showing the most popular {FAV_PAGE_SIZE} — search for anything else.
            </p>

            {catalog.length === 0 ? (
              <p className="onboard-intro">Loading the catalog...</p>
            ) : (
              <div className="onboard-fav-grid">
                {visibleFavTitles.map((t) => {
                  const active = favs.includes(t.title);
                  return (
                    <button
                      key={t.id}
                      type="button"
                      onClick={() => togglefav(t.title)}
                      className={`onboard-fav-chip${active ? " active" : ""}`}
                      aria-pressed={active}
                    >
                      <span className="onboard-fav-poster">
                        <img src={posterThumbUrl(t)} alt="" loading="lazy" onError={handlePosterThumbError} />
                        {active && <span className="onboard-fav-check" aria-hidden="true">✓</span>}
                      </span>
                      <span className="onboard-fav-title">{t.title}</span>
                    </button>
                  );
                })}
                {visibleFavTitles.length === 0 && (
                  <p style={{ color: "var(--text-faint)" }}>No titles match here yet — try another tab or search.</p>
                )}
              </div>
            )}

            <p style={{ color: "var(--text-faint)", fontSize: "0.78rem", marginBottom: "var(--sp-2)" }}>
              {favs.length} selected — {Math.max(0, 5 - favs.length)} more to go
            </p>

            <button
              type="button"
              onClick={() => {
                if (favs.length < 5) {
                  seterr("Pick at least 5 titles first.");
                  return;
                }
                seterr("");
                setstep(2);
              }}
              className="btn-primary"
            >
              Next: Story Tropes →
            </button>
          </div>
        )}

        {step === 2 && (
          <div>
            <p className="onboard-intro">
              Which storytelling tropes pull you in? Pick as many as apply.
            </p>

            <div className="onboard-chip-grid">
              {TROPES.map((t) => {
                const active = tropes.includes(t);
                return (
                  <button
                    key={t}
                    type="button"
                    onClick={() => toggletrope(t)}
                    className={`onboard-chip${active ? " active" : ""}`}
                  >
                    {active ? "✓ " : "+ "} {t}
                  </button>
                );
              })}
            </div>

            <div className="onboard-footer">
              <button type="button" onClick={() => setstep(1)}>
                ← Back
              </button>
              <button type="button" onClick={() => setstep(3)} className="btn-primary">
                Next: Emotional Goals →
              </button>
            </div>
          </div>
        )}

        {step === 3 && (
          <div>
            <p className="onboard-intro">
              I want stories that make me... (pick a few)
            </p>

            <div className="onboard-chip-grid">
              {EMOTIONAL_GOALS.map((g) => {
                const active = emotionalGoals.includes(g);
                return (
                  <button
                    key={g}
                    type="button"
                    onClick={() => togglegoal(g)}
                    className={`onboard-chip${active ? " active" : ""}`}
                  >
                    {active ? "✓ " : "+ "} {g}
                  </button>
                );
              })}
            </div>

            <div className="onboard-footer">
              <button type="button" onClick={() => setstep(2)}>
                ← Back
              </button>
              <button type="button" onClick={() => setstep(4)} className="btn-primary">
                Next: Rate Genres →
              </button>
            </div>
          </div>
        )}

        {step === 4 && (
          <div>
            <p className="onboard-intro">
              Tell us how you feel about each category — this shapes your recommendations.
              This nudges your TasteDNA; it won't hide titles outright. For a hard no, use Dealbreakers below.
            </p>

            <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-1)", marginBottom: "var(--sp-3)" }}>
              {GENRES.map((g) => {
                const current = genres[g] || "Neutral";
                return (
                  <div key={g} className="genre-row">
                    <span>{g}</span>
                    <div className="genre-rate-group">
                      {["Love", "Neutral", "Avoid"].map((rate) => (
                        <button
                          key={rate}
                          type="button"
                          onClick={() => setgenrerate(g, rate)}
                          className={`genre-rate-button${current === rate ? " active" : ""}`}
                        >
                          {rate}
                        </button>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="onboard-footer">
              <button type="button" onClick={() => setstep(3)}>
                ← Back
              </button>
              <button type="button" onClick={() => setstep(5)} className="btn-primary">
                Next: Dealbreakers →
              </button>
            </div>
          </div>
        )}

        {step === 5 && (
          <div>
            <p className="onboard-intro">
              Set pacing, emotional intensity, and hard dealbreakers to finalize your profile.
            </p>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--sp-2)", marginBottom: "var(--sp-3)" }}>
              <div className="slider-field">
                <span className="slider-field-label">
                  Story Pacing ({pacing === 1 ? "Very Slow" : pacing === 2 ? "Slow Burn" : pacing === 3 ? "Balanced" : pacing === 4 ? "Fast" : "Hyper Fast"})
                </span>
                <input
                  type="range"
                  min="1"
                  max="5"
                  value={pacing}
                  onChange={(e) => setpacing(Number(e.target.value))}
                />
              </div>

              <div className="slider-field">
                <span className="slider-field-label">
                  Emotional Intensity ({intensity}/10 — {intensity <= 3 ? "Cozy" : intensity <= 7 ? "Balanced" : "Soul Crushing"})
                </span>
                <input
                  type="range"
                  min="1"
                  max="10"
                  value={intensity}
                  onChange={(e) => setintensity(Number(e.target.value))}
                />
              </div>
            </div>

            <span className="slider-field-label">Things You Avoid (Hard Constraints)</span>
            <div className="onboard-chip-grid">
              {BREAKER_CHIPS.map((chip) => {
                const active = chip.keys.some((k) => avoid.includes(k));
                return (
                  <button
                    key={chip.label}
                    type="button"
                    onClick={() => toggleavoidChip(chip)}
                    className={`onboard-chip avoid${active ? " active" : ""}`}
                  >
                    {active ? "✕ " : "+ "} {chip.label}
                  </button>
                );
              })}
            </div>

            <div className="onboard-footer" style={{ marginTop: "var(--sp-3)" }}>
              <button type="button" onClick={() => setstep(4)}>
                ← Back
              </button>
              <button type="button" onClick={submitall} className="btn-primary" disabled={submitting}>
                {submitting ? "Generating..." : "Generate TasteDNA"}
              </button>
            </div>
          </div>
        )}

        {err && <p className="status-message status-message--error" style={{ marginTop: "var(--sp-2)" }}>{err}</p>}
      </div>
    </div>
  );
}

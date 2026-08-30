import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { authHeaders } from "./auth";
import { BASE, gettitlepicker, gettastedna } from "./api";
import { playChime } from "./sound";
import { FAV_PAGE_SIZE } from "./onboardingUtils";
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

const FAV_TABS = [
  { key: "movie", label: "Movies" },
  { key: "show", label: "Shows" },
  { key: "anime", label: "Anime" }
];

const ANALYZING_LINES = [
  "Reading your favorites...",
  "Mapping your Story DNA...",
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

// Onboarding used to be a mandatory 5-step wizard (favorites, tropes,
// emotional goals, genre ratings, dealbreakers/sliders) standing entirely
// between registration and the first useful screen — measured at 3m12s.
// Only step 1 was ever actually enforced; steps 2-5 are now offered AFTER
// a first result exists, as the dismissible "Sharpen your TasteDNA" prompt
// on the dashboard (see TasteRefinement.jsx), which posts additively to
// /api/movies/onboard/refine instead of gating entry.
export default function Onboarding({ onFinish }) {
  const [favTab, setfavTab] = useState("movie");
  const [favQuery, setfavQuery] = useState("");
  const [favResults, setfavResults] = useState([]);
  const [favResultsLoading, setfavResultsLoading] = useState(true);
  const [favs, setfavs] = useState([]);
  const [err, seterr] = useState("");
  const [submitting, setsubmitting] = useState(false);
  const [analyzing, setanalyzing] = useState(false);
  const [reveal, setreveal] = useState(null);

  const userid = localStorage.getItem("userId") || 1;

  // Debounced regardless of branch, same reasoning as MovieFeed's own
  // search-suggestions effect (see its comment) — keeps this clean of the
  // set-state-in-effect lint rule without a manual disable, and avoids
  // firing a request on every keystroke. Re-fetches on tab switch too
  // (favTab isn't itself debounced, but folding it into the same effect
  // means a tab click doesn't race a still-pending search fetch). `favs`
  // is intentionally NOT a dependency — toggling a checkbox shouldn't
  // re-fetch the page it's already showing; the server only needs to know
  // the current picks at the moment a new page is requested, to keep them
  // visible across a tab/search change (see TitlePickerDTO's doc comment).
  useEffect(() => {
    const handle = setTimeout(() => {
      setfavResultsLoading(true);
      gettitlepicker(favTab, favQuery, favs).then((results) => {
        setfavResults(results);
        setfavResultsLoading(false);
      });
    }, 250);
    return () => clearTimeout(handle);
  }, [favTab, favQuery]);

  function togglefav(title) {
    setfavs((current) =>
      current.includes(title) ? current.filter((x) => x !== title) : [...current, title]
    );
  }

  async function doSubmit(payload) {
    setsubmitting(true);
    setanalyzing(true);

    const res = await fetch(`${BASE}/api/movies/onboard`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify(payload)
    }).catch(() => null);

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
      return;
    }
    await doSubmit({ userId: userid, favs });
  }

  // Submits whatever's actually been picked so far, not a hardcoded blank
  // slate — a user who's already selected a few favorites and then hits
  // "Skip for now" shouldn't lose that signal. A genuinely untouched skip
  // (nothing picked) still reaches the same neutral (all-0.5) TraitVector
  // server-side as before (see ProfileService).
  async function skiponboarding() {
    seterr("");
    await doSubmit({ userId: userid, favs });
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
            <h1 className="onboard-title">Your Comfort Movies</h1>
          </div>
          {/* A brand-new visitor's very first screen was a mandatory five-step
              wizard with no way out — "just let me look around" wasn't an
              option. Skipping seeds a neutral profile (see skiponboarding's
              comment) rather than blocking entry entirely. */}
          <button type="button" className="onboard-skip-link" onClick={skiponboarding} disabled={submitting}>
            Skip for now →
          </button>
        </div>

        <p className="onboard-intro">
          Pick at least 5 titles across movies, shows, and anime to seed your starting profile.
          You can sharpen it further anytime from your dashboard.
        </p>

        <div className="pill-row" style={{ marginBottom: "var(--sp-2)" }}>
          {FAV_TABS.map((tab) => (
            <button
              key={tab.key}
              type="button"
              className={`pill${favTab === tab.key ? " active" : ""}`}
              onClick={() => setfavTab(tab.key)}
            >
              {tab.label}
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

        {favResultsLoading ? (
          <p className="onboard-intro">Loading titles...</p>
        ) : (
          <div className="onboard-fav-grid">
            {favResults.map((t) => {
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
            {favResults.length === 0 && (
              <p style={{ color: "var(--text-faint)" }}>No titles match here yet — try another tab or search.</p>
            )}
          </div>
        )}

        <p style={{ color: "var(--text-faint)", fontSize: "0.78rem", marginBottom: "var(--sp-2)" }}>
          {favs.length} selected — {Math.max(0, 5 - favs.length)} more to go
        </p>

        <button type="button" onClick={submitall} className="btn-primary" disabled={submitting}>
          {submitting ? "Generating..." : "Generate TasteDNA"}
        </button>

        {err && <p className="status-message status-message--error" style={{ marginTop: "var(--sp-2)" }}>{err}</p>}
      </div>
    </div>
  );
}

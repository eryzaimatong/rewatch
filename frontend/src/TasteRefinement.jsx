import { useState } from "react";
import useModalA11y from "./useModalA11y";
import { refineOnboarding } from "./api";
import "./App.css";

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

// Steps 2-5 of the old mandatory onboarding wizard — tropes, emotional
// goals, genre ratings, pacing/intensity, dealbreakers — now offered here
// as optional post-dashboard refinement instead of gating the first result.
// Submits additively to /api/movies/onboard/refine (see api.js), which
// merges into the existing seed rather than overwriting it.
export default function TasteRefinement({ userid, onClose, onSaved }) {
  const modalRef = useModalA11y(onClose);

  const [tropes, settropes] = useState([]);
  const [emotionalGoals, setemotionalGoals] = useState([]);
  const [genres, setgenres] = useState({});
  const [avoid, setavoid] = useState([]);
  const [pacing, setpacing] = useState(3);
  const [intensity, setintensity] = useState(4);
  const [saving, setsaving] = useState(false);
  const [err, seterr] = useState("");

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

  function toggleavoidChip(chip) {
    setavoid((current) => {
      const hasAny = chip.keys.some((k) => current.includes(k));
      if (hasAny) {
        return current.filter((x) => !chip.keys.includes(x));
      }
      return [...current, ...chip.keys.filter((k) => !current.includes(k))];
    });
  }

  async function save() {
    seterr("");
    setsaving(true);
    const { ok } = await refineOnboarding({
      userId: userid, genres, avoid, tropes, emotionalGoals, pacing, intensity
    });
    setsaving(false);
    if (!ok) {
      seterr("Could not save your refinements. Please try again.");
      return;
    }
    onSaved && onSaved();
  }

  return (
    <div
      className="modal-overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          onClose();
        }
      }}
    >
      <div
        className="modal-card"
        ref={modalRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="taste-refinement-title"
        tabIndex={-1}
      >
        <div className="modal-header">
          <h2 id="taste-refinement-title" style={{ margin: 0 }}>Sharpen your TasteDNA</h2>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close">×</button>
        </div>

        <p className="onboard-intro">Which storytelling tropes pull you in? Pick as many as apply.</p>
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

        <p className="onboard-intro" style={{ marginTop: "var(--sp-3)" }}>
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

        <p className="onboard-intro" style={{ marginTop: "var(--sp-3)" }}>
          Tell us how you feel about each category — this shapes your recommendations.
          It nudges your TasteDNA; it won't hide titles outright. For a hard no, use Dealbreakers below.
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

        {err && <p className="status-message status-message--error" style={{ marginTop: "var(--sp-2)" }}>{err}</p>}

        <div style={{ display: "flex", gap: "10px", marginTop: "var(--sp-3)" }}>
          <button type="button" onClick={onClose} className="btn-block">
            Maybe later
          </button>
          <button type="button" onClick={save} className="btn-primary btn-block" disabled={saving}>
            {saving ? "Saving..." : "Save"}
          </button>
        </div>
      </div>
    </div>
  );
}

import { useState } from "react";
import { authHeaders } from "./auth";
import "./App.css";

const MOVIES = [
  "Interstellar", "Parasite", "Arrival", "Reply 1988", "Hospital Playlist",
  "Our Beloved Summer", "Twenty Five Twenty One", "My Liberation Notes",
  "The Glory", "La La Land", "Past Lives", "Attack on Titan",
  "Spirited Away", "Little Miss Sunshine", "Spider-Man: Across the Spider-Verse",
  "Arcane", "Oppenheimer", "Everything Everywhere All at Once", "Your Name", "Knives Out"
];

const GENRES = [
  "Drama", "Sci-Fi", "Slice of Life", "Romance", "Thriller", "Anime", "Comedy", "Mystery"
];

const BREAKERS = [
  "Excessive Gore", "Sad Ending", "Open Ending", "Love Triangle", "Jump Scares",
  "Slow Start", "Animal Death", "Cheating", "Poor CGI", "Unresolved Ending"
];

const RUNTIMES = [
  "20 min episodes", "45 min episodes", "90 min movie", "2+ hour movie", "Weekend binge"
];

const STEP_TITLES = ["Seed Your Favorites", "Rate Genres", "Dealbreakers & Pacing"];

export default function Onboarding({ onFinish }) {
  const [step, setstep] = useState(1);
  const [favs, setfavs] = useState([]);
  const [genres, setgenres] = useState({});
  const [avoid, setavoid] = useState([]);
  const [pacing, setpacing] = useState(3);
  const [intensity, setintensity] = useState(4);
  const [runtime, setruntime] = useState("90 min movie");
  const [err, seterr] = useState("");
  const [submitting, setsubmitting] = useState(false);

  const userid = localStorage.getItem("userId") || 1;

  function togglefav(m) {
    setfavs((current) =>
      current.includes(m) ? current.filter((x) => x !== m) : [...current, m]
    );
  }

  function setgenrerate(g, val) {
    setgenres((current) => ({ ...current, [g]: val }));
  }

  function toggleavoid(item) {
    setavoid((current) =>
      current.includes(item) ? current.filter((x) => x !== item) : [...current, item]
    );
  }

  async function submitall() {
    seterr("");
    if (favs.length < 5) {
      seterr("Pick at least 5 favorites so we can seed your starting profile.");
      return;
    }

    setsubmitting(true);

    const payload = {
      userId: userid,
      favs: favs,
      genres: genres,
      avoid: avoid,
      pacing: pacing,
      intensity: intensity,
      runtime: runtime
    };

    const res = await fetch("http://localhost:8080/api/movies/onboard", {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify(payload)
    }).catch(() => null);

    setsubmitting(false);

    if (!res || !res.ok) {
      seterr("Could not save your taste profile. Please try again.");
      return;
    }

    if (onFinish) {
      onFinish();
    }
  }

  return (
    <div className="onboard-shell">
      <div className="page-panel onboard-panel">
        <span className="eyebrow">TasteDNA Onboarding</span>
        <h1 className="onboard-title">
          Step {step} of 3: {STEP_TITLES[step - 1]}
        </h1>

        <div className="onboard-progress">
          {[1, 2, 3].map((n) => (
            <div key={n} className={`onboard-progress-segment${n <= step ? " is-complete" : ""}`} />
          ))}
        </div>

        {step === 1 && (
          <div>
            <p className="onboard-intro">
              Pick at least 5 titles across movies, shows, and anime to seed your starting profile.
            </p>

            <div className="onboard-chip-grid">
              {MOVIES.map((m) => {
                const active = favs.includes(m);
                return (
                  <button
                    key={m}
                    type="button"
                    onClick={() => togglefav(m)}
                    className={`onboard-chip${active ? " active" : ""}`}
                  >
                    {active ? "✓ " : "+ "} {m}
                  </button>
                );
              })}
            </div>

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
              Next: Rate Genres →
            </button>
          </div>
        )}

        {step === 2 && (
          <div>
            <p className="onboard-intro">
              Tell us how you feel about each category — this shapes your recommendations.
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
              <button type="button" onClick={() => setstep(1)}>
                ← Back
              </button>
              <button type="button" onClick={() => setstep(3)} className="btn-primary">
                Next: Dealbreakers →
              </button>
            </div>
          </div>
        )}

        {step === 3 && (
          <div>
            <p className="onboard-intro">
              Set pacing, emotional intensity, runtime, and hard dealbreakers to finalize your profile.
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

            <div style={{ marginBottom: "var(--sp-3)" }}>
              <span className="slider-field-label">Typical Runtime Preference</span>
              <div className="runtime-row">
                {RUNTIMES.map((r) => (
                  <button
                    key={r}
                    type="button"
                    onClick={() => setruntime(r)}
                    className={`onboard-chip${runtime === r ? " active" : ""}`}
                  >
                    {r}
                  </button>
                ))}
              </div>
            </div>

            <span className="slider-field-label">Things You Avoid (Hard Constraints)</span>
            <div className="onboard-chip-grid">
              {BREAKERS.map((item) => {
                const active = avoid.includes(item);
                return (
                  <button
                    key={item}
                    type="button"
                    onClick={() => toggleavoid(item)}
                    className={`onboard-chip avoid${active ? " active" : ""}`}
                  >
                    {active ? "✕ " : "+ "} {item}
                  </button>
                );
              })}
            </div>

            <div className="onboard-footer" style={{ marginTop: "var(--sp-3)" }}>
              <button type="button" onClick={() => setstep(2)}>
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

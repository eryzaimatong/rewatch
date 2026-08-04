import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import TraitRadar from "./TraitRadar";
import useModalA11y from "./useModalA11y";
import { authHeaders } from "./auth";
import { BASE } from "./api";
import "./App.css";

// Matches the validated pair in TraitRadar.jsx: purple (user) vs #0284c7
// (this title) clears CVD/contrast/lightness at --pairs all against the
// app's dark surface. The old #38bdf8 sky blue looked right but failed the
// dark-mode lightness band — swapped for the validated step.
const MOVIE_COLOR = "#0284c7";

function StarRow({ label, value, onChange }) {
  return (
    <div className="star-row">
      <span className="star-row-label">{label}</span>
      <div className="star-buttons">
        {[1, 2, 3, 4, 5].map((i) => (
          <button
            key={i}
            type="button"
            className={`star-button${i <= value ? " is-filled" : ""}`}
            onClick={() => onChange(i)}
            aria-label={`${label}: ${i} of 5 stars`}
          >
            ★
          </button>
        ))}
      </div>
    </div>
  );
}

function ContributionRow({ item, positive }) {
  return (
    <li className={`contribution-row ${positive ? "is-driver" : "is-tension"}`}>
      <span className="contribution-label">{item.label}</span>
      <span className="contribution-value">
        {item.contribution >= 0 ? "+" : ""}
        {item.contribution.toFixed(1)}
      </span>
    </li>
  );
}

export default function MovieModal({ movie, onClose }) {
  const [tab, settab] = useState("fingerprint");
  const [overall, setoverall] = useState(0);
  const [chars, setchars] = useState(0);
  const [ending, setending] = useState(0);
  const [visuals, setvisuals] = useState(0);
  const [story, setstory] = useState(0);
  const [rewatch, setrewatch] = useState(0);
  const [moment, setmoment] = useState("Ending Payoff");
  const [msg, setmsg] = useState("");
  const [err, seterr] = useState("");
  const [topshift, settopshift] = useState(null);

  const [matchData, setmatchData] = useState(null);
  const [matchLoading, setmatchLoading] = useState(true);
  const [matchError, setmatchError] = useState("");

  const userid = localStorage.getItem("userId") || 1;

  async function loadMatch(titleId, uid) {
    if (!titleId) {
      setmatchLoading(false);
      setmatchError("Detailed story fingerprint isn't available for this title yet.");
      return;
    }

    setmatchLoading(true);
    setmatchError("");

    const res = await fetch(`${BASE}/api/titles/${titleId}/match?userId=${encodeURIComponent(uid)}`, { headers: authHeaders() }).catch(() => null);
    const data = res && res.ok ? await res.json() : null;

    if (!data) {
      setmatchError("Could not load this title's story fingerprint.");
    } else {
      setmatchData(data);
    }
    setmatchLoading(false);
  }

  useEffect(() => {
    // See EvolutionTimeline.jsx for why this needs a targeted disable: a
    // fetch keyed on the title/user changing is exactly what an effect is
    // for, even though the rule flags any dependency-triggered effect that
    // sets state before its async call resolves.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadMatch(movie?.titleId, userid);
  }, [movie?.titleId, userid]);

  const modalRef = useModalA11y(onClose);

  if (!movie) {
    return null;
  }

  const explanation = matchData?.explanation;
  const radarTraits = explanation?.all?.map((c) => ({ key: c.trait, label: c.label })) ?? [];
  const userValues = {};
  const movieValues = {};
  explanation?.all?.forEach((c) => {
    userValues[c.trait] = c.userVal;
    movieValues[c.trait] = c.movieVal;
  });

  async function dorate() {
    setmsg("");
    seterr("");
    settopshift(null);

    if (overall === 0 || chars === 0 || ending === 0) {
      seterr("Rate at least Overall, Characters, and Ending before saving.");
      return;
    }

    const payload = {
      userId: userid,
      tmdbId: movie.id,
      title: movie.title,
      overall: overall,
      chars: chars,
      ending: ending,
      visuals: visuals || 3,
      story: story || 3,
      rewatch: rewatch || 3,
      moment: moment
    };

    const res = await fetch(`${BASE}/api/movies/rate`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify(payload)
    }).catch(() => null);

    if (!res || !res.ok) {
      seterr("Could not save this rating to the server.");
      return;
    }

    const result = await res.json().catch(() => null);
    setmsg("Rating saved.");
    if (result?.topShift) {
      settopshift(result.topShift);
    }
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
        aria-labelledby="movie-modal-title"
        tabIndex={-1}
      >
        <div className="modal-header">
          <div>
            <span className="eyebrow">Story Fingerprint</span>
            <h2 id="movie-modal-title">{movie.title}</h2>
            <p style={{ margin: 0, fontSize: "0.85rem" }}>
              {movie.year || "2024"} • {Math.round(matchData?.matchScore ?? movie.matchScore ?? 50)}% match
            </p>
          </div>

          <button type="button" onClick={onClose} className="modal-close" aria-label="Close">
            ✕
          </button>
        </div>

        <div className="modal-tabs">
          <button
            type="button"
            onClick={() => settab("fingerprint")}
            className={`modal-tab${tab === "fingerprint" ? " active" : ""}`}
          >
            Story Fingerprint
          </button>
          <button
            type="button"
            onClick={() => settab("rate")}
            className={`modal-tab${tab === "rate" ? " active" : ""}`}
          >
            Rate & Evolve
          </button>
        </div>

        {tab === "fingerprint" && (
          <div>
            {matchLoading && (
              <div className="feed-state" style={{ minHeight: "180px" }}>
                <div className="loading-orb" />
              </div>
            )}

            {!matchLoading && matchError && (
              <p className="status-message status-message--error">{matchError}</p>
            )}

            {!matchLoading && explanation && (
              <>
                <div
                  style={{
                    background: "var(--background)",
                    border: "1px solid rgba(168, 85, 247, 0.25)",
                    borderRadius: "12px",
                    padding: "16px",
                    marginBottom: "var(--sp-3)"
                  }}
                >
                  <span className="eyebrow" style={{ display: "block", marginBottom: "6px" }}>
                    Why this matches you
                  </span>
                  <p style={{ margin: 0, fontSize: "0.88rem", color: "var(--neutral-text)", lineHeight: "1.5" }}>
                    {explanation.summary}
                  </p>
                </div>

                <TraitRadar
                  traits={radarTraits}
                  primary={{ label: "Your TasteDNA", color: "#a855f7", values: userValues }}
                  secondary={{ label: movie.title, color: MOVIE_COLOR, values: movieValues }}
                  size={300}
                />

                {(explanation.drivers?.length > 0 || explanation.tensions?.length > 0) && (
                  <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--sp-2)", marginTop: "var(--sp-3)" }}>
                    {explanation.drivers?.length > 0 && (
                      <div>
                        <p className="fit-label" style={{ marginBottom: "8px" }}>Drivers</p>
                        <ul className="contribution-list">
                          {explanation.drivers.map((d) => (
                            <ContributionRow key={d.trait} item={d} positive />
                          ))}
                        </ul>
                      </div>
                    )}
                    {explanation.tensions?.length > 0 && (
                      <div>
                        <p className="fit-label" style={{ marginBottom: "8px", color: "var(--text-faint)" }}>Tensions</p>
                        <ul className="contribution-list">
                          {explanation.tensions.map((t) => (
                            <ContributionRow key={t.trait} item={t} positive={false} />
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {tab === "rate" && (
          <div>
            <p style={{ fontSize: "0.86rem", marginBottom: "var(--sp-2)" }}>
              Rating this title updates your TasteDNA profile in real time.
            </p>

            <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-1)", marginBottom: "var(--sp-3)" }}>
              <StarRow label="Overall Impression" value={overall} onChange={setoverall} />
              <StarRow label="Characters & Growth" value={chars} onChange={setchars} />
              <StarRow label="Ending Payoff" value={ending} onChange={setending} />
              <StarRow label="Visuals & Atmosphere" value={visuals} onChange={setvisuals} />
              <StarRow label="Story & Pacing" value={story} onChange={setstory} />
              <StarRow label="Rewatchability" value={rewatch} onChange={setrewatch} />
            </div>

            <div className="auth-field">
              <label htmlFor="moment-select">Which moment stayed with you the most?</label>
              <select id="moment-select" value={moment} onChange={(e) => setmoment(e.target.value)}>
                <option value="Ending Payoff">Ending Payoff</option>
                <option value="Character Dialogue">Character Dialogue</option>
                <option value="Plot Twist">Plot Twist</option>
                <option value="Quiet Intimate Scene">Quiet Intimate Scene</option>
                <option value="Cinematic Score">Music & Atmosphere</option>
              </select>
            </div>

            {err && <p className="status-message status-message--error">{err}</p>}

            <AnimatePresence>
              {topshift && (
                <motion.div
                  className="shift-toast"
                  initial={{ opacity: 0, y: 10, scale: 0.96 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: -6 }}
                  transition={{ type: "spring", stiffness: 340, damping: 22 }}
                  style={{ marginBottom: "var(--sp-2)" }}
                >
                  <span className="shift-toast-icon">✦</span>
                  <span className="shift-toast-text">
                    Your TasteDNA just moved — <strong>{topshift.label}</strong>{" "}
                    {topshift.delta >= 0 ? "+" : ""}
                    {Math.round(topshift.delta * 100)}%
                  </span>
                </motion.div>
              )}
            </AnimatePresence>
            {msg && !topshift && <p className="status-message status-message--success">{msg}</p>}

            <button type="button" onClick={dorate} className="btn-primary btn-block">
              Save Rating & Evolve Profile
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

import { useEffect, useRef, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import TraitRadar from "./TraitRadar";
import useModalA11y from "./useModalA11y";
import MatchShareCard from "./MatchShareCard";
import ConfirmDialog from "./ConfirmDialog";
import { authHeaders } from "./auth";
import { BASE, rateMovie, deleteRating } from "./api";
import { playConfirm, playChime, playSoftError } from "./sound";
import { IconTrophy } from "./Icons";
import "./App.css";

// Matches the validated pair in TraitRadar.jsx: purple (user) vs --cyan
// (#0284c7, this title) clears CVD/contrast/lightness at --pairs all against
// the app's dark surface. The old #38bdf8 sky blue looked right but failed
// the dark-mode lightness band — swapped for the validated step.
const MOVIE_COLOR = "var(--cyan)";

function ordinal(n) {
  const rem100 = n % 100;
  if (rem100 >= 11 && rem100 <= 13) return `${n}th`;
  switch (n % 10) {
    case 1: return `${n}st`;
    case 2: return `${n}nd`;
    case 3: return `${n}rd`;
    default: return `${n}th`;
  }
}

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

// Plain-language "what to expect" phrasing per axis, keyed off the TITLE'S
// OWN trait extremes — not the user-relative Drivers/Tensions panel above,
// which explains why THIS title fits YOU. Tradeoffs is the honest sibling:
// what's true about this story regardless of who's watching.
const TRADEOFF_PHRASES = {
  pacing: {
    high: "Slower pacing — more contemplative than plot-driven.",
    low: "Fast-moving — momentum over lingering."
  },
  intensity: {
    high: "Emotionally heavy — not a light watch.",
    low: "Low-key — nothing here will wreck you."
  },
  bitter: {
    high: "Bittersweet — don't expect a tidy bow.",
    low: "Resolves cleanly, not much left unsaid."
  },
  hope: {
    high: "Earns a genuinely hopeful ending.",
    low: "Doesn't offer much comfort by the end."
  },
  humor: {
    high: "Keeps a light touch even in the hard parts.",
    low: "Heavier tone — not much comic relief."
  },
  comfort: {
    high: "Low-anxiety, easy to sit with.",
    low: "More edge than warmth — not a cozy watch."
  },
  family: {
    high: "Ensemble-driven, relationship-heavy.",
    low: null
  },
  nostalgia: {
    high: "Steeped in memory, looking backward.",
    low: null
  },
  growth: {
    high: null,
    low: "Characters mostly stay who they started as."
  },
  romance: {
    high: "Romance is a real throughline, not a subplot.",
    low: null
  }
};

// The title's own biggest deviations from neutral, translated into the
// tradeoff phrasing above — real signal (the same movieVal already driving
// the radar's secondary series), not a fabricated "worth it" claim.
function deriveTradeoffs(explanationAll, matchScore) {
  if (!Array.isArray(explanationAll) || explanationAll.length === 0) return [];

  const ranked = explanationAll
    .map((c) => ({ ...c, deviation: Math.abs(c.movieVal - 0.5) }))
    .filter((c) => c.deviation > 0.15)
    .sort((a, b) => b.deviation - a.deviation)
    .slice(0, 3);

  const phrases = [];
  for (const c of ranked) {
    const entry = TRADEOFF_PHRASES[c.trait];
    if (!entry) continue;
    const phrase = c.movieVal >= 0.5 ? entry.high : entry.low;
    if (phrase) phrases.push(phrase);
  }

  if (phrases.length > 0 && typeof matchScore === "number" && matchScore >= 65) {
    phrases.push(`Still a ${Math.round(matchScore)}% match for you — worth the trade.`);
  }

  return phrases;
}

function ContributionRow({ item, positive }) {
  return (
    <li className={`contribution-row ${positive ? "is-driver" : "is-tension"}`}>
      <div className="contribution-row-head">
        <span className="contribution-label">{item.label}</span>
        <span className="contribution-value">
          {item.contribution >= 0 ? "+" : ""}
          {item.contribution.toFixed(1)}
        </span>
      </div>
      {item.text && <p className="contribution-text">{item.text}</p>}
    </li>
  );
}

export default function MovieModal({ movie, onClose, onRatingChange }) {
  const [tab, settab] = useState("fingerprint");
  const [overall, setoverall] = useState(0);
  const [chars, setchars] = useState(0);
  const [ending, setending] = useState(0);
  const [visuals, setvisuals] = useState(0);
  const [story, setstory] = useState(0);
  const [rewatch, setrewatch] = useState(0);
  const [moment, setmoment] = useState("Ending Payoff");
  const [deeperOpen, setdeeperOpen] = useState(false);
  const [msg, setmsg] = useState("");
  const [err, seterr] = useState("");
  const [topshift, settopshift] = useState(null);
  const [rewatchInfo, setrewatchInfo] = useState(null);
  const [unlockedAchievement, setunlockedAchievement] = useState(null);
  const [saving, setsaving] = useState(false);
  // A second, ref-backed guard alongside the `saving` state above. `saving`
  // is only checked/set for the *disabled* attribute and other render-driven
  // UI — state updates are scheduled, not synchronous, so two dorate() calls
  // fired back-to-back in the same tick (a fast real double-click, or two
  // 'click' events dispatched before React re-renders) both read the same
  // stale `saving === false` from closure and both pass the guard. A ref
  // mutates immediately, so the second call sees the first call's write
  // regardless of whether a render has happened yet.
  const savingRef = useRef(false);
  const [showsharecard, setshowsharecard] = useState(false);
  const [confirmingDeleteRating, setconfirmingDeleteRating] = useState(false);
  const [deletingRating, setdeletingRating] = useState(false);
  // The modal previously had no watchlist action at all — the only way to
  // save a title was the poster's quick-action icon back in the feed grid,
  // which meant "Discover Something New" (Dashboard.jsx's MiniCard, opened
  // straight into this modal with no surrounding card chrome) had no save
  // path whatsoever. Self-contained here (its own fetch/toggle) rather than
  // threaded through as props, since this modal is opened from more than one
  // parent (MovieFeed, Dashboard) with different local watchlist-state shapes.
  const [savedItemId, setsavedItemId] = useState(null);
  const [savingWatchlist, setsavingWatchlist] = useState(false);
  const savingWatchlistRef = useRef(false);

  const [matchData, setmatchData] = useState(null);
  const [matchLoading, setmatchLoading] = useState(true);
  const [matchError, setmatchError] = useState("");

  const [details, setdetails] = useState(null);
  const [detailsLoading, setdetailsLoading] = useState(true);

  const userid = localStorage.getItem("userId") || 1;

  async function loadMatch(titleId, uid) {
    if (!titleId) {
      setmatchLoading(false);
      setmatchError("A detailed breakdown isn't available for this title yet.");
      return;
    }

    setmatchLoading(true);
    setmatchError("");

    const res = await fetch(`${BASE}/api/titles/${titleId}/match?userId=${encodeURIComponent(uid)}`, { headers: authHeaders() }).catch(() => null);
    const data = res && res.ok ? await res.json() : null;

    if (!data) {
      setmatchError("Could not load why this matches you.");
    } else {
      setmatchData(data);
    }
    setmatchLoading(false);
  }

  async function loadDetails(titleId) {
    if (!titleId) {
      setdetailsLoading(false);
      return;
    }
    setdetailsLoading(true);
    const res = await fetch(`${BASE}/api/titles/${titleId}/details`, { headers: authHeaders() }).catch(() => null);
    const data = res && res.ok ? await res.json() : null;
    setdetails(data);
    setdetailsLoading(false);
  }

  async function loadWatchlistStatus(movieId, uid) {
    if (!movieId) {
      setsavedItemId(null);
      return;
    }
    const res = await fetch(`${BASE}/api/watchlist/${uid}`, { headers: authHeaders() }).catch(() => null);
    const items = res && res.ok ? await res.json().catch(() => null) : null;
    if (!Array.isArray(items)) {
      return;
    }
    const match = items.find((item) => String(item.tmdbId ?? item.titleId) === String(movieId));
    setsavedItemId(match ? match.id : null);
  }

  async function toggleWatchlist() {
    // Ref-backed for the same reason as dorate()/savingRef — a state read
    // from closure can't see another call's write until React re-renders.
    if (savingWatchlistRef.current || !movie) {
      return;
    }
    savingWatchlistRef.current = true;
    setsavingWatchlist(true);
    if (savedItemId) {
      const res = await fetch(`${BASE}/api/watchlist/items/${savedItemId}?userId=${userid}`, {
        method: "DELETE",
        headers: authHeaders()
      }).catch(() => null);
      if (res && res.ok) {
        setsavedItemId(null);
      }
    } else {
      const res = await fetch(`${BASE}/api/watchlist/items`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authHeaders() },
        body: JSON.stringify({
          userId: userid,
          tmdbId: movie.id,
          titleId: movie.titleId ?? undefined,
          title: movie.title
        })
      }).catch(() => null);
      const saved = res && res.ok ? await res.json().catch(() => null) : null;
      if (saved?.id) {
        setsavedItemId(saved.id);
        playConfirm();
      }
    }
    savingWatchlistRef.current = false;
    setsavingWatchlist(false);
  }

  useEffect(() => {
    // See EvolutionTimeline.jsx for why this needs a targeted disable: a
    // fetch keyed on the title/user changing is exactly what an effect is
    // for, even though the rule flags any dependency-triggered effect that
    // sets state before its async call resolves.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadMatch(movie?.titleId, userid);
    loadDetails(movie?.titleId);
    loadWatchlistStatus(movie?.id, userid);
  }, [movie?.id, movie?.titleId, userid]);

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
  const tradeoffs = deriveTradeoffs(explanation?.all, matchData?.matchScore ?? movie.matchScore);
  // Same "+12.4 Bittersweet Drama" shape as the on-screen Drivers list
  // (ContributionRow) — the share card's reasons should read identically to
  // what's already on screen, not a second, drifting phrasing of the same data.
  const shareDrivers = (explanation?.drivers ?? [])
    .slice(0, 3)
    .map((d) => `${d.contribution >= 0 ? "+" : ""}${d.contribution.toFixed(1)} ${d.label}`);

  async function dorate() {
    if (savingRef.current) {
      return;
    }
    setmsg("");
    seterr("");
    settopshift(null);
    setrewatchInfo(null);
    setunlockedAchievement(null);

    if (overall === 0) {
      seterr("Rate at least Overall before saving.");
      return;
    }

    savingRef.current = true;
    setsaving(true);

    // Facets the user never touched are sent as undefined, not faked as a
    // middle "3" — an untouched star row isn't the same signal as an
    // actual 3-star rating. RatingDTO only requires `overall`.
    const payload = {
      userId: userid,
      tmdbId: movie.id,
      title: movie.title,
      overall: overall,
      chars: chars || undefined,
      ending: ending || undefined,
      visuals: visuals || undefined,
      story: story || undefined,
      rewatch: rewatch || undefined,
      moment: moment
    };

    try {
      const { ok, data: result } = await rateMovie(payload);

      if (!ok) {
        seterr("Could not save this rating to the server.");
        playSoftError();
        return;
      }

      // A rating shifts the whole taste profile, not just this title's own
      // score — every match number already on screen elsewhere (the hero
      // ring, other feed cards) is now stale until the parent re-scores
      // them. Without this, the same title could show one number here
      // (freshly recomputed on this modal's own next load) and a leftover
      // pre-rating number wherever else it's still displayed.
      onRatingChange?.();

      if (result?.isRewatch) {
        setrewatchInfo({ watchNumber: result.watchNumber, previousOverall: result.previousOverall });
      } else {
        // Shown only when the shift-toast isn't (no trait moved enough to name) —
        // still real feedback that the rating landed, not a generic "saved."
        setmsg("Got it — that's now part of your story.");
      }
      if (result?.topShift) {
        settopshift(result.topShift);
      }
      // The bigger chime takes priority over the plain confirm — an
      // achievement unlocking is the more exciting of the two things that
      // could have just happened, so it gets the sound that matches that.
      if (result?.newlyUnlockedAchievements?.length > 0) {
        setunlockedAchievement(result.newlyUnlockedAchievements[0]);
        playChime();
      } else {
        playConfirm();
      }
    } finally {
      savingRef.current = false;
      setsaving(false);
    }
  }

  async function handleDeleteRating() {
    if (deletingRating || !matchData?.ratingId) {
      return;
    }
    setdeletingRating(true);
    try {
      const { ok } = await deleteRating(matchData.ratingId, userid);
      if (ok) {
        // Resets the whole rating form back to "never rated" rather than
        // just clearing matchData.ratingId — the star rows, moment picker,
        // and any leftover shift/achievement toasts from a prior submit all
        // need to go with it, or the UI would show stale stars for a rating
        // that no longer exists.
        setoverall(0);
        setchars(0);
        setending(0);
        setvisuals(0);
        setstory(0);
        setrewatch(0);
        setmsg("");
        seterr("");
        settopshift(null);
        setrewatchInfo(null);
        setunlockedAchievement(null);
        setconfirmingDeleteRating(false);
        // Re-fetches so matchData.rated/ratingId reflect the deletion, and
        // matchScore/explanation reflect the recomputed (now one rating
        // lighter) taste profile.
        loadMatch(movie?.titleId, userid);
        onRatingChange?.();
      } else {
        seterr("Could not delete this rating.");
        setconfirmingDeleteRating(false);
      }
    } finally {
      setdeletingRating(false);
    }
  }

  return (
    <>
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
            <span className="eyebrow">Why You'll Like It</span>
            <h2 id="movie-modal-title">{movie.title}</h2>
            <p style={{ margin: 0, fontSize: "0.85rem" }}>
              {movie.year || "2024"}
              {/* No fallback default here on purpose — see MovieFeed.jsx's normalizeMovie */}
              {(matchData?.matchScore ?? movie.matchScore) != null &&
                ` • ${Math.round(matchData?.matchScore ?? movie.matchScore)}% match`}
            </p>
          </div>

          <div style={{ display: "flex", alignItems: "flex-start", gap: "8px" }}>
            <button
              type="button"
              onClick={toggleWatchlist}
              disabled={savingWatchlist}
              className={`modal-share-trigger${savedItemId ? " is-saved" : ""}`}
              aria-label={savedItemId ? `Remove ${movie.title} from watchlist` : `Save ${movie.title} to watchlist`}
              title={savedItemId ? "Remove from watchlist" : "Save to watchlist"}
            >
              {savedItemId ? "✓" : "+"}
            </button>
            {(matchData?.matchScore ?? movie.matchScore) != null && (
              <button
                type="button"
                onClick={() => setshowsharecard(true)}
                className="modal-share-trigger"
                aria-label="Share this match"
                title="Share this match"
              >
                ⤴
              </button>
            )}
            <button type="button" onClick={onClose} className="modal-close" aria-label="Close">
              ✕
            </button>
          </div>
        </div>

        <div className="modal-tabs">
          <button
            type="button"
            onClick={() => settab("fingerprint")}
            className={`modal-tab${tab === "fingerprint" ? " active" : ""}`}
          >
            Why You'll Like It
          </button>
          <button
            type="button"
            onClick={() => settab("rate")}
            className={`modal-tab${tab === "rate" ? " active" : ""}`}
          >
            Rate This Story
          </button>
          <button
            type="button"
            onClick={() => settab("details")}
            className={`modal-tab${tab === "details" ? " active" : ""}`}
          >
            Details
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

                {tradeoffs.length > 0 && (
                  <div className="tradeoffs-panel">
                    <p className="fit-label" style={{ marginBottom: "8px" }}>Tradeoffs</p>
                    <ul className="tradeoffs-list">
                      {tradeoffs.map((t) => (
                        <li key={t}>{t}</li>
                      ))}
                    </ul>
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

            <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-1)", marginBottom: "var(--sp-2)" }}>
              <StarRow label="Overall Impression" value={overall} onChange={setoverall} />
              {deeperOpen && (
                <>
                  <StarRow label="Characters & Growth" value={chars} onChange={setchars} />
                  <StarRow label="Ending Payoff" value={ending} onChange={setending} />
                  <StarRow label="Visuals & Atmosphere" value={visuals} onChange={setvisuals} />
                  <StarRow label="Story & Pacing" value={story} onChange={setstory} />
                  <StarRow label="Rewatchability" value={rewatch} onChange={setrewatch} />
                </>
              )}
            </div>

            <button
              type="button"
              onClick={() => setdeeperOpen((v) => !v)}
              className="auth-toggle-link"
              style={{ fontSize: "0.85rem", background: "none", border: "none", marginBottom: "var(--sp-3)", padding: 0 }}
            >
              {deeperOpen ? "Show less" : "Rate deeper →"}
            </button>

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
              {rewatchInfo && (
                <motion.div
                  className="shift-toast"
                  initial={{ opacity: 0, y: 10, scale: 0.96 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: -6 }}
                  transition={{ type: "spring", stiffness: 340, damping: 22 }}
                  style={{ marginBottom: "var(--sp-2)" }}
                >
                  <span className="shift-toast-icon">↻</span>
                  <span className="shift-toast-text">
                    Back again? This is your {ordinal(rewatchInfo.watchNumber)} watch.
                    {rewatchInfo.previousOverall != null && rewatchInfo.previousOverall !== overall &&
                      ` Your rating went ${rewatchInfo.previousOverall}★ → ${overall}★.`}
                  </span>
                </motion.div>
              )}
            </AnimatePresence>

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
            <AnimatePresence>
              {unlockedAchievement && (
                <motion.div
                  className="shift-toast achievement-toast"
                  initial={{ opacity: 0, y: 10, scale: 0.9 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: -6 }}
                  transition={{ type: "spring", stiffness: 300, damping: 20 }}
                  style={{ marginBottom: "var(--sp-2)" }}
                >
                  <span className="shift-toast-icon"><IconTrophy /></span>
                  <span className="shift-toast-text">
                    Achievement unlocked — <strong>{unlockedAchievement}</strong>
                  </span>
                </motion.div>
              )}
            </AnimatePresence>

            {msg && !topshift && <p className="status-message status-message--success">{msg}</p>}

            <button type="button" onClick={dorate} disabled={saving} className="btn-primary btn-block">
              {saving ? "Saving…" : "Save Rating & Evolve Profile"}
            </button>

            {/* Only once matchData has actually loaded a ratingId — there
                was previously no way to remove a rating anywhere in the
                app, not from the UI, not from the API. */}
            {matchData?.ratingId && (
              <button
                type="button"
                onClick={() => setconfirmingDeleteRating(true)}
                disabled={deletingRating}
                className="btn-danger-outline btn-block"
                style={{ marginTop: "var(--sp-2)" }}
              >
                Delete My Rating
              </button>
            )}
          </div>
        )}

        {tab === "details" && (
          <div>
            {detailsLoading && (
              <div className="feed-state" style={{ minHeight: "180px" }}>
                <div className="loading-orb" />
              </div>
            )}

            {!detailsLoading && details?.trailerUrl && (
              <a
                href={details.trailerUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="btn-primary btn-block"
                style={{ display: "block", textAlign: "center", marginBottom: "var(--sp-3)", textDecoration: "none" }}
              >
                ▶ Watch trailer
              </a>
            )}

            {!detailsLoading && details?.director && (
              <p style={{ fontSize: "0.86rem", color: "var(--text-muted)", marginBottom: "var(--sp-2)" }}>
                Directed by <strong style={{ color: "var(--text)" }}>{details.director}</strong>
              </p>
            )}

            {!detailsLoading && (details?.runtimeMinutes || details?.certification) && (
              <p style={{ fontSize: "0.86rem", color: "var(--text-muted)", marginBottom: "var(--sp-3)" }}>
                {details.runtimeMinutes && (
                  <>{Math.floor(details.runtimeMinutes / 60)}h {details.runtimeMinutes % 60}m</>
                )}
                {details.runtimeMinutes && details.certification && " · "}
                {details.certification && <span className="chip" style={{ padding: "3px 9px" }}>{details.certification}</span>}
              </p>
            )}

            {!detailsLoading && details?.watchProviders?.length > 0 && (
              <div style={{ marginBottom: "var(--sp-3)" }}>
                <p className="fit-label" style={{ marginBottom: "8px" }}>Where to watch</p>
                <div className="pill-row" style={{ flexWrap: "wrap" }}>
                  {details.watchProviders.map((p) => (
                    <span key={p} className="chip">{p}</span>
                  ))}
                </div>
              </div>
            )}

            {!detailsLoading && details?.cast?.length > 0 && (
              <div>
                <p className="fit-label" style={{ marginBottom: "8px" }}>Cast</p>
                <div className="trait-list">
                  {details.cast.map((c) => (
                    <div key={c.name} className="trait-card">
                      <span className="trait-name">{c.name}</span>
                      {c.character && (
                        <span className="trait-trend">{c.character}</span>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {!detailsLoading && !details?.director && !details?.trailerUrl && !details?.runtimeMinutes &&
              !details?.certification && !(details?.watchProviders?.length > 0) && !(details?.cast?.length > 0) && (
                <p style={{ color: "var(--text-muted)", fontSize: "0.88rem" }}>
                  No additional details found for this title.
                </p>
            )}
          </div>
        )}
      </div>
    </div>
    {showsharecard && (
      <MatchShareCard
        movie={movie}
        matchScore={matchData?.matchScore ?? movie.matchScore}
        drivers={shareDrivers}
        username={localStorage.getItem("username") || "friend"}
        onClose={() => setshowsharecard(false)}
      />
    )}
    {confirmingDeleteRating && (
      <ConfirmDialog
        title="Delete your rating?"
        message="This removes your rating and any written reflection for this title, and recalculates your TasteDNA without it."
        confirmLabel={deletingRating ? "Deleting…" : "Delete Rating"}
        onConfirm={handleDeleteRating}
        onCancel={() => setconfirmingDeleteRating(false)}
      />
    )}
    </>
  );
}

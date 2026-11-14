import { useState } from "react";
import { authHeaders } from "./auth";
import { BASE } from "./api";
import { useResilientQuery, fetchJson } from "./resilience/useResilientQuery";
import { DoubleFeatureSkeleton } from "./Skeleton";
import Ree from "./Ree";
import "./App.css";

const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w342";
const FALLBACK_POSTER = "https://placehold.co/200x300/16233f/45c5b4?text=Re:Watch";

function posterUrl(path) {
  if (!path) return FALLBACK_POSTER;
  return TMDB_IMAGE_BASE_URL + (path.startsWith("/") ? path : "/" + path);
}

function shareText(dateStr, correct, streak, titleA, titleB, chosenTitle) {
  const dateLabel = new Date(dateStr).toLocaleDateString(undefined, { month: "short", day: "numeric" });
  const lines = [
    `Re:Watch Daily Double Feature — ${dateLabel}`,
    correct ? `✅ Picked right: ${chosenTitle}` : `❌ Missed it — went with ${chosenTitle}`,
    streak > 1 ? `🔥 ${streak}-day streak` : null,
    window.location.origin
  ];
  return lines.filter(Boolean).join("\n");
}

/**
 * "Daily Double Feature" — the Wordle-style daily ritual. Two titles, the
 * same for everyone today; the twist is the *answer* is personal — whichever
 * one is actually YOUR higher real match, computed the same way every other
 * match score in the app is. See DailyChallengeService (backend) for why
 * that makes guessing right a genuine signal, not shared trivia.
 */
export default function DailyDoubleFeature() {
  const userId = localStorage.getItem("userId");
  // ttlMs 0: today's pair must never come from yesterday's cache.
  const { data: state, status, retry } = useResilientQuery({
    key: userId ? `daily:${userId}` : null,
    fetcher: (signal) => fetchJson(`/api/daily/${userId}`, { signal }),
    validate: (d) => !!d?.titleA && !!d?.titleB,
    ttlMs: 0,
    softTimeoutMs: 8_000
  });
  const [guessing, setguessing] = useState(false);
  const [result, setresult] = useState(null);
  const [copied, setcopied] = useState(false);
  const [err, seterr] = useState("");

  async function pick(titleId, titleLabel) {
    if (guessing || !state || state.alreadyPlayed) return;
    setguessing(true);
    seterr("");
    const res = await fetch(`${BASE}/api/daily/guess`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ userId: Number(userId), titleId })
    }).catch(() => null);

    if (res && res.ok) {
      const data = await res.json();
      setresult({ ...data, chosenTitle: titleLabel });
    } else {
      seterr("Could not save your pick. Please try again.");
    }
    setguessing(false);
  }

  async function copyResult() {
    if (!state) return;
    const correct = result ? result.correct : state.previousGuess?.correct;
    const streak = result ? result.currentStreak : state.currentStreak;
    const chosen = result
      ? result.chosenTitle
      : (state.previousGuess?.chosenTitleId === state.titleA.titleId ? state.titleA.title : state.titleB.title);
    try {
      await navigator.clipboard.writeText(shareText(state.date, correct, streak, state.titleA.title, state.titleB.title, chosen));
      setcopied(true);
      setTimeout(() => setcopied(false), 2000);
    } catch {
      seterr("Could not copy your result.");
    }
  }

  if (!userId) return null;
  if (status === "loading") return <DoubleFeatureSkeleton />;
  if (!state) {
    // Nothing cached, no fallback and the request failed: a gentle, actionable
    // card instead of a vanished section.
    return (
      <section className="feed-section daily-double-feature df-rest">
        <Ree pose="sleepy" size={84} />
        <div>
          <h2>Daily Double Feature</h2>
          <p>Ree is still setting up tonight's pair.</p>
        </div>
        <button type="button" className="pill" onClick={retry}>Try again</button>
      </section>
    );
  }

  const played = state.alreadyPlayed || !!result;
  const outcome = result || (state.previousGuess ? {
    correct: state.previousGuess.correct,
    currentStreak: state.currentStreak,
    chosenTitle: state.previousGuess.chosenTitleId === state.titleA.titleId ? state.titleA.title : state.titleB.title
  } : null);
  const streak = outcome?.currentStreak ?? state.currentStreak ?? 0;

  return (
    <section className="feed-section daily-double-feature">
      <header className="df-head">
        <div>
          <p className="section-eyebrow">Today's Ritual</p>
          <h2>Daily Double Feature</h2>
          <p className="df-sub">
            {played
              ? "You've played today. Come back tomorrow for a new pair."
              : "Which one is YOUR real better match tonight? Choose before you see the numbers."}
          </p>
        </div>
        {streak > 0 && (
          <span className="df-streak" title="Daily streak"><span aria-hidden="true">🔥</span> <span className="mono">{streak}</span>-day streak</span>
        )}
      </header>

      <div className="df-grid">
        {[state.titleA, state.titleB].map((t) => {
          const isChosen = played && outcome && outcome.chosenTitle === t.title;
          const score = result
            ? (t.titleId === state.titleA.titleId ? result.titleAScore : result.titleBScore)
            : null;
          return (
            <button
              key={t.titleId}
              type="button"
              className={`df-card${isChosen ? " is-chosen" : ""}`}
              onClick={() => pick(t.titleId, t.title)}
              disabled={played || guessing}
              aria-pressed={isChosen}
            >
              <span className="df-poster">
                <img
                  src={posterUrl(t.posterPath)}
                  alt=""
                  loading="lazy"
                  onError={(e) => { e.currentTarget.onerror = null; e.currentTarget.src = FALLBACK_POSTER; }}
                />
                {score !== null && (
                  <span className="df-score"><span className="mono">{Math.round(score)}%</span> match</span>
                )}
              </span>
              <span className="df-title">{t.title}</span>
            </button>
          );
        })}
      </div>

      {played && outcome && (
        <p className={`df-outcome ${outcome.correct ? "is-right" : "is-wrong"}`}>
          {outcome.correct ? "Nailed it — that one really was your better match." : "Not quite — the other one fit you better."}
        </p>
      )}

      {err && <p className="status-message status-message--error">{err}</p>}

      {played && (
        <button type="button" onClick={copyResult} className="btn-block df-copy">
          {copied ? "Copied!" : "Copy Result"}
        </button>
      )}
    </section>
  );
}

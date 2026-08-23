import { useEffect, useState } from "react";
import { authHeaders } from "./auth";
import { BASE } from "./api";
import "./App.css";

const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w200";
const FALLBACK_POSTER = "https://placehold.co/200x300/191a21/a855f7?text=Re:Watch";

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
  const [state, setstate] = useState(null);
  const [loading, setloading] = useState(true);
  const [guessing, setguessing] = useState(false);
  const [result, setresult] = useState(null);
  const [copied, setcopied] = useState(false);
  const [err, seterr] = useState("");

  useEffect(() => {
    if (!userId) {
      // See EvolutionTimeline.jsx for why this needs a targeted disable: a
      // fetch keyed on userId is exactly what an effect is for, even though
      // the rule flags any dependency-triggered effect that sets state
      // before its async call resolves.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setloading(false);
      return;
    }
    (async () => {
      const res = await fetch(`${BASE}/api/daily/${userId}`, { headers: authHeaders() }).catch(() => null);
      if (res && res.ok) {
        setstate(await res.json());
      }
      setloading(false);
    })();
  }, [userId]);

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

  if (!userId || loading || !state) {
    return null;
  }

  const played = state.alreadyPlayed || !!result;
  const outcome = result || (state.previousGuess ? {
    correct: state.previousGuess.correct,
    currentStreak: state.currentStreak,
    chosenTitle: state.previousGuess.chosenTitleId === state.titleA.titleId ? state.titleA.title : state.titleB.title
  } : null);

  return (
    <section className="feed-section daily-double-feature">
      <p className="section-eyebrow">Today's Ritual</p>
      <h2 style={{ margin: "0 0 6px" }}>Daily Double Feature</h2>
      <p style={{ margin: "0 0 var(--sp-2)", color: "var(--text-muted)" }}>
        {played
          ? "You've already played today — come back tomorrow for the next one."
          : "Which one is YOUR real better match tonight? Pick before you see the numbers."}
      </p>

      <div style={{ display: "flex", gap: "var(--sp-2)", alignItems: "stretch" }}>
        {[state.titleA, state.titleB].map((t) => {
          const isChosen = played && outcome && outcome.chosenTitle === t.title;
          const score = result
            ? (t.titleId === state.titleA.titleId ? result.titleAScore : result.titleBScore)
            : null;
          return (
            <button
              key={t.titleId}
              type="button"
              onClick={() => pick(t.titleId, t.title)}
              disabled={played || guessing}
              style={{
                flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: "6px",
                background: "var(--surface)", border: isChosen ? "2px solid var(--primary)" : "1px solid var(--border)",
                borderRadius: "12px", padding: "10px", cursor: played ? "default" : "pointer"
              }}
            >
              <img
                src={posterUrl(t.posterPath)}
                alt=""
                onError={(e) => { e.currentTarget.onerror = null; e.currentTarget.src = FALLBACK_POSTER; }}
                style={{ width: "72px", height: "108px", objectFit: "cover", borderRadius: "8px" }}
              />
              <span style={{ fontSize: "0.82rem", fontWeight: 600, textAlign: "center" }}>{t.title}</span>
              {score !== null && (
                <span style={{ fontSize: "0.78rem", color: "var(--text-muted)" }}><span className="mono">{Math.round(score)}%</span> match</span>
              )}
            </button>
          );
        })}
      </div>

      {played && outcome && (
        <p style={{ margin: "var(--sp-2) 0 0", fontWeight: 600 }}>
          {outcome.correct ? "✅ Nailed it." : "❌ Not quite."}{" "}
          {outcome.currentStreak > 1 && <span style={{ color: "var(--text-muted)", fontWeight: 400 }}>🔥 <span className="mono">{outcome.currentStreak}</span>-day streak</span>}
        </p>
      )}

      {err && <p className="status-message status-message--error">{err}</p>}

      {played && (
        <button type="button" onClick={copyResult} className="btn-block" style={{ marginTop: "var(--sp-2)", maxWidth: "220px" }}>
          {copied ? "Copied!" : "Copy Result"}
        </button>
      )}
    </section>
  );
}

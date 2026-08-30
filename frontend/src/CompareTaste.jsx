import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import BrandMark from "./BrandMark";
import MatchRing from "./MatchRing";
import TraitRadar from "./TraitRadar";
import WakingState from "./WakingState";
import { BASE } from "./api";
import {
  CARD_SCALE, CARD_W, CARD_H, drawCardFrame, drawCardFooter, drawVerticallyCentered,
  wrapText, shareOrDownloadBlob
} from "./shareCard";
import "./App.css";

// Shown by WakingState while the quiz loads — a real example (the exact
// "Cozy Nostalgic" combo from ArchetypeService.java: comfort >= 0.80 &&
// nostalgia >= 0.80), not invented data, so a stranger waiting out a cold
// boot gets a true preview of the product instead of a placeholder.
const SAMPLE_TRAITS = [
  { key: "family", label: "Found Family" },
  { key: "nostalgia", label: "Nostalgia & Memory" },
  { key: "growth", label: "Character Growth" },
  { key: "pacing", label: "Slow Burn Pacing" },
  { key: "humor", label: "Humor & Lightheartedness" },
  { key: "romance", label: "Romance" },
  { key: "intensity", label: "Emotional Intensity" },
  { key: "hope", label: "Hopeful Payoff" },
  { key: "bitter", label: "Bittersweet Drama" },
  { key: "comfort", label: "Comfort & Warmth" }
];
const SAMPLE_VALUES = {
  family: 0.55, nostalgia: 0.85, growth: 0.4, pacing: 0.6, humor: 0.35,
  romance: 0.5, intensity: 0.3, hope: 0.65, bitter: 0.35, comfort: 0.85
};

const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w200";
const FALLBACK_POSTER = "https://placehold.co/200x300/191a21/a855f7?text=Re:Watch";

function posterUrl(path) {
  if (!path) return FALLBACK_POSTER;
  return TMDB_IMAGE_BASE_URL + (path.startsWith("/") ? path : "/" + path);
}

const MIN_ANSWERS = 3;

function StarPicker({ value, onChange }) {
  return (
    <div style={{ display: "flex", gap: "4px" }}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          onClick={() => onChange(value === n ? 0 : n)}
          aria-label={`${n} star${n === 1 ? "" : "s"}`}
          aria-pressed={value >= n}
          style={{
            background: "none", border: "none", padding: "2px", cursor: "pointer",
            fontSize: "1.3rem", lineHeight: 1,
            color: value >= n ? "var(--primary-light, #c4b5fd)" : "var(--text-faint)"
          }}
        >
          {value >= n ? "★" : "☆"}
        </button>
      ))}
    </div>
  );
}

/**
 * The no-signup half of the social loop — a visitor with no account rates a
 * handful of titles and sees a real compatibility score against a specific
 * user's actual TasteDNA. See CompatibilityController (backend) for why this
 * has to be a public route: the whole point is one link a friend can open
 * and use cold, no account, no friction. Everything about the flow — the
 * math, the result — is real, not a lead-gen gimmick with a fake number.
 */
export default function CompareTaste() {
  const { username } = useParams();
  const navigate = useNavigate();

  const [quiz, setquiz] = useState([]);
  const [quizStatus, setquizStatus] = useState("loading");
  const [quizErrorKind, setquizErrorKind] = useState(null);
  // Bumped on every retry and used as WakingState's `key` — a fresh mount
  // resets its internal elapsed-time clock via useState's own initial value
  // instead of a synchronous setState call inside an effect (see
  // WakingState.jsx). Not used for anything else.
  const [quizAttempt, setquizAttempt] = useState(0);
  const [answers, setanswers] = useState({});
  const [submitting, setsubmitting] = useState(false);
  const [result, setresult] = useState(null);
  const [err, seterr] = useState("");
  const [linkCopied, setlinkCopied] = useState(false);
  const [exporting, setexporting] = useState(false);

  // No synchronous setState before the first `await` — kept free of the
  // "loading"/error-reset assignments on purpose so this can be called
  // directly from the initial-mount effect below without triggering it to
  // re-render synchronously on every mount. retryQuiz (used by the actual
  // retry button, a real event handler, not an effect) does that reset.
  async function fetchQuiz() {
    try {
      const res = await fetch(`${BASE}/api/compatibility/quiz`);
      const data = res.ok ? await res.json().catch(() => null) : null;
      setquiz(Array.isArray(data) ? data : []);
      setquizStatus("ready");
    } catch (e) {
      setquizStatus("error");
      setquizErrorKind(e?.name === "TimeoutError" ? "timeout" : "network");
    }
  }

  function retryQuiz() {
    setquizAttempt((n) => n + 1);
    setquizStatus("loading");
    setquizErrorKind(null);
    fetchQuiz();
  }

  useEffect(() => {
    (async () => {
      await fetchQuiz();
    })();
  }, []);

  const answeredCount = Object.values(answers).filter((v) => v > 0).length;

  async function submit() {
    setsubmitting(true);
    seterr("");
    const responses = Object.entries(answers)
      .filter(([, overall]) => overall > 0)
      .map(([titleId, overall]) => ({ titleId: Number(titleId), overall }));

    const res = await fetch(`${BASE}/api/compatibility/check`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ targetUsername: username, responses })
    }).catch(() => null);

    if (res && res.ok) {
      setresult(await res.json());
    } else if (res && res.status === 404) {
      const body = await res.json().catch(() => null);
      seterr(body?.message || "Could not compare with that profile. Double-check the username and try again.");
    } else if (res && res.status === 429) {
      // The backend's own message now includes a real retry timeframe
      // (RateLimiterService.tooManyRequests) — shown as-is rather than
      // reworded into a fixed, timeframe-free string.
      const body = await res.json().catch(() => null);
      seterr(body?.message || "Too many checks from here — try again in a bit.");
    } else {
      seterr("Something went wrong. Please try again.");
    }
    setsubmitting(false);
  }

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setlinkCopied(true);
      setTimeout(() => setlinkCopied(false), 2000);
    } catch {
      seterr("Could not copy the link automatically — select it from the address bar instead.");
    }
  }

  async function exportCard() {
    if (!result) return;
    setexporting(true);

    const canvas = document.createElement("canvas");
    canvas.width = CARD_W * CARD_SCALE;
    canvas.height = CARD_H * CARD_SCALE;
    const ctx = canvas.getContext("2d");
    ctx.scale(CARD_SCALE, CARD_SCALE);

    drawCardFrame(ctx, "TASTE COMPATIBILITY");

    drawVerticallyCentered(ctx, 220, CARD_H - 100, (c, startY) => {
      c.textAlign = "center";
      c.fillStyle = "#ffffff";
      c.font = "bold 64px 'Plus Jakarta Sans', sans-serif";
      c.fillText(`${result.compatibilityPercent}%`, CARD_W / 2, startY);

      c.fillStyle = "#71717a";
      c.font = "14px 'Plus Jakarta Sans', sans-serif";
      c.fillText(`compatible with @${result.targetUsername}`, CARD_W / 2, startY + 30);

      c.fillStyle = "#e4e4e7";
      c.font = "bold 22px 'Plus Jakarta Sans', sans-serif";
      let y = wrapText(c, result.headline, CARD_W / 2, startY + 90, CARD_W - 100, 30);

      if (result.sharedTraits.length > 0) {
        y += 30;
        c.strokeStyle = "rgba(255,255,255,0.1)";
        c.lineWidth = 1;
        c.beginPath();
        c.moveTo(60, y);
        c.lineTo(CARD_W - 60, y);
        c.stroke();

        y += 34;
        c.fillStyle = "#a855f7";
        c.font = "bold 11px 'Plus Jakarta Sans', sans-serif";
        c.fillText("WHAT YOU SHARE", CARD_W / 2, y);

        y += 32;
        c.fillStyle = "#e4e4e7";
        c.font = "16px 'Plus Jakarta Sans', sans-serif";
        result.sharedTraits.forEach((label) => {
          c.fillText(label, CARD_W / 2, y);
          y += 26;
        });
      }

      return y;
    });

    drawCardFooter(ctx);

    setexporting(false);
    canvas.toBlob((blob) => {
      shareOrDownloadBlob(blob, `taste-compatibility-with-${result.targetUsername}.png`, {
        title: `${result.compatibilityPercent}% taste compatibility on Re:Watch`,
        text: `I'm ${result.compatibilityPercent}% compatible with @${result.targetUsername} on Re:Watch.`
      }).catch(() => seterr("Could not share this card. Try again, or take a screenshot instead."));
    });
  }

  return (
    <div className="page-shell">
      <div className="page-panel" style={{ maxWidth: "560px", textAlign: "center" }}>
        <div style={{ display: "flex", justifyContent: "center", marginBottom: "8px" }}>
          <BrandMark size={32} />
        </div>
        <span className="eyebrow">Taste Compatibility</span>

        {!result && (
          <>
            <h1 style={{ margin: "4px 0 8px" }}>How compatible are you with @{username}?</h1>
            <p style={{ color: "var(--text-muted)", margin: "0 0 var(--sp-3)" }}>
              Rate at least {MIN_ANSWERS} of these — skip anything you haven't seen. No account needed.
            </p>

            {quizStatus !== "ready" && (
              <WakingState
                key={quizAttempt}
                status={quizStatus}
                errorKind={quizErrorKind}
                onRetry={retryQuiz}
                subject="the quiz"
              >
                <div className="waking-sample" style={{ marginTop: "var(--sp-3)" }}>
                  <p style={{
                    fontSize: "0.72rem", textTransform: "uppercase",
                    color: "var(--text-faint)", margin: "0 0 8px"
                  }}>
                    Example TasteDNA — "Cozy Nostalgic"
                  </p>
                  <TraitRadar
                    traits={SAMPLE_TRAITS}
                    primary={{ label: "Cozy Nostalgic", color: "#a855f7", values: SAMPLE_VALUES }}
                    size={220}
                  />
                  <p style={{ color: "var(--text-muted)", fontSize: "0.9rem", marginTop: "8px" }}>
                    "You gravitate toward warmth, memory, and low-anxiety worlds."
                  </p>
                </div>
              </WakingState>
            )}

            {quizStatus === "ready" && quiz.length === 0 && (
              <p style={{ color: "var(--text-muted)" }}>Could not load the quiz right now. Try again shortly.</p>
            )}

            <div style={{ display: "flex", flexDirection: "column", gap: "var(--sp-2)", textAlign: "left" }}>
              {quiz.map((t) => (
                <div
                  key={t.titleId}
                  style={{
                    display: "flex", alignItems: "center", gap: "12px",
                    padding: "10px 12px", border: "1px solid var(--border)", borderRadius: "12px"
                  }}
                >
                  <img
                    src={posterUrl(t.posterPath)}
                    alt=""
                    onError={(e) => { e.currentTarget.onerror = null; e.currentTarget.src = FALLBACK_POSTER; }}
                    style={{ width: "40px", height: "60px", objectFit: "cover", borderRadius: "6px", flexShrink: 0 }}
                  />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {t.title} {t.year ? `(${t.year})` : ""}
                    </div>
                  </div>
                  <StarPicker
                    value={answers[t.titleId] || 0}
                    onChange={(v) => setanswers((a) => ({ ...a, [t.titleId]: v }))}
                  />
                </div>
              ))}
            </div>

            {err && <p className="status-message status-message--error">{err}</p>}

            <button
              type="button"
              onClick={submit}
              className="btn-primary btn-block"
              disabled={answeredCount < MIN_ANSWERS || submitting}
              style={{ marginTop: "var(--sp-3)" }}
            >
              {submitting
                ? "Comparing..."
                : answeredCount < MIN_ANSWERS
                  ? `Rate ${MIN_ANSWERS - answeredCount} more to see your score`
                  : "See My Compatibility"}
            </button>
          </>
        )}

        {result && (
          <>
            <div style={{ display: "flex", justifyContent: "center", margin: "var(--sp-3) 0" }}>
              <MatchRing score={result.compatibilityPercent} size={120} />
            </div>
            <h1 style={{ margin: "0 0 6px" }}><span className="mono">{result.compatibilityPercent}%</span> compatible with @{result.targetUsername}</h1>
            <p style={{ fontSize: "1.1rem", fontWeight: 600, margin: "0 0 var(--sp-2)" }}>{result.headline}</p>

            {result.sharedTraits.length > 0 && (
              <div className="share-card-traits" style={{ textAlign: "left" }}>
                <p style={{ fontSize: "0.72rem", margin: "0 0 10px", textTransform: "uppercase", color: "var(--text-faint)" }}>
                  What you share
                </p>
                {result.sharedTraits.map((label) => (
                  <div key={label} className="share-card-trait-row" style={{ justifyContent: "flex-start" }}>
                    <span>{label}</span>
                  </div>
                ))}
              </div>
            )}

            {err && <p className="status-message status-message--error">{err}</p>}

            <div style={{ display: "flex", gap: "10px", marginTop: "var(--sp-3)" }}>
              <button type="button" onClick={copyLink} className="btn-block">
                {linkCopied ? "Link copied" : "Copy link"}
              </button>
              <button type="button" onClick={exportCard} className="btn-primary btn-block" disabled={exporting}>
                {exporting ? "Preparing..." : "Download image"}
              </button>
            </div>
            <button type="button" onClick={() => navigate("/")} className="btn-primary btn-block" style={{ marginTop: "8px" }}>
              Create Your Own TasteDNA
            </button>
          </>
        )}
      </div>
    </div>
  );
}

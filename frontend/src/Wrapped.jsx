import { useEffect, useState } from "react";
import { authHeaders } from "./auth";
import { BASE } from "./api";
import {
  CARD_SCALE, CARD_W, CARD_H, drawCardFrame, drawCardFooter, loadImage, shareOrDownloadBlob
} from "./shareCard";
import EmptyState from "./EmptyState";
import "./App.css";

const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w200";
const FALLBACK_POSTER = "https://placehold.co/200x300/191a21/a855f7?text=Re:Watch";

function poster(path) {
  if (!path) return FALLBACK_POSTER;
  if (path.startsWith("http")) return path;
  return TMDB_IMAGE_BASE_URL + (path.startsWith("/") ? path : "/" + path);
}

function currentMonthValue() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

function pct(v) {
  return Math.round(v * 100);
}

export default function Wrapped() {
  const username = localStorage.getItem("username") || "friend";
  const userid = localStorage.getItem("userId") || 1;
  const thisMonth = currentMonthValue();

  const [month, setmonth] = useState(thisMonth);
  const [summary, setsummary] = useState(null);
  const [loading, setloading] = useState(true);
  const [err, seterr] = useState("");
  const [exporting, setexporting] = useState(false);

  async function load(m) {
    setloading(true);
    seterr("");
    const res = await fetch(`${BASE}/api/tastedna/wrapped/${userid}?month=${m}`, { headers: authHeaders() }).catch(() => null);
    if (res && res.ok) {
      setsummary(await res.json());
    } else {
      seterr("Could not load your wrapped recap right now.");
      setsummary(null);
    }
    setloading(false);
  }

  useEffect(() => {
    // See EvolutionTimeline.jsx for why this needs no further change despite
    // the linter's static analysis of dependency-triggered fetches.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load(month);
  }, [month]);

  function shiftLabel(shift) {
    const arrow = shift.delta > 0.001 ? "↑" : shift.delta < -0.001 ? "↓" : "→";
    const sign = shift.delta >= 0 ? "+" : "";
    // A single rating's pull on any one trait is often under a full point
    // (the EMA step is scaled by relevance/confidence/settle — see
    // ProfileService.weightFor). Rounding to whole points would show "+0pts"
    // for movement that's real but small, making the headline feature look
    // broken on exactly the case — one or two ratings — where a first-time
    // user is most likely to see it. Still round to the nearest 0.1 via
    // integer math before formatting (not `.toFixed(1)` directly on the raw
    // float) — toFixed's own rounding is decided against the imprecise binary
    // value, which can surface as e.g. "44.9" for what's actually a clean 45.
    const points = Math.round(shift.delta * 1000) / 10;
    return `${arrow} ${sign}${points.toFixed(1)}pts`;
  }

  async function exportWrappedPNG() {
    if (!summary) return;
    setexporting(true);

    const topPosters = summary.topRatings.slice(0, 5);
    // Posters loaded before any drawing starts — a poster collage is the
    // single biggest lever for this card actually looking like something
    // worth sharing rather than a stats readout; the previous version had
    // no imagery at all. Failures degrade to no image for that slot rather
    // than failing the whole export (a dead TMDB poster URL shouldn't cost
    // someone their whole recap).
    const posterImages = await Promise.all(
      topPosters.map((r) => loadImage(poster(r.poster)).catch(() => null))
    );

    const canvas = document.createElement("canvas");
    canvas.width = CARD_W * CARD_SCALE;
    canvas.height = CARD_H * CARD_SCALE;
    const ctx = canvas.getContext("2d");
    ctx.scale(CARD_SCALE, CARD_SCALE);

    drawCardFrame(ctx, "RE:WATCH WRAPPED");

    ctx.textAlign = "center";
    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 32px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText(summary.period, CARD_W / 2, 146);

    ctx.fillStyle = "#a1a1aa";
    ctx.font = "14px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText(`${username} · ${summary.ratingCount} title${summary.ratingCount === 1 ? "" : "s"} rated`, CARD_W / 2, 172);

    ctx.fillStyle = "#d8b4fe";
    ctx.font = "bold 19px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText(summary.archetype, CARD_W / 2, 214);

    // Poster collage — the visual hook. Real cover art, not just numbers.
    let y = 250;
    if (posterImages.some(Boolean)) {
      ctx.textAlign = "left";
      ctx.fillStyle = "#71717a";
      ctx.font = "bold 12px 'Plus Jakarta Sans', sans-serif";
      ctx.fillText("TOP RATED", 36, y);
      y += 18;

      // Sized for a fixed 5-slot row (this section's max, per TOP_RATING_COUNT
      // server-side) regardless of how many titles actually rendered — dividing
      // by topPosters.length instead scaled a single poster up to nearly the
      // full card width on a light month (1-2 ratings) and blew straight
      // through the footer below it. Centered as a row instead of always
      // left-aligned, so 1-2 posters don't look stranded against the margin.
      const gap = 10;
      const slotCount = 5;
      const posterW = (CARD_W - 72 - gap * (slotCount - 1)) / slotCount;
      const posterH = posterW * 1.5;
      const rowWidth = topPosters.length * posterW + gap * (topPosters.length - 1);
      const rowStart = (CARD_W - rowWidth) / 2;
      topPosters.forEach((r, i) => {
        const x = rowStart + i * (posterW + gap);
        const img = posterImages[i];
        ctx.save();
        ctx.beginPath();
        const radius = 8;
        ctx.moveTo(x + radius, y);
        ctx.arcTo(x + posterW, y, x + posterW, y + posterH, radius);
        ctx.arcTo(x + posterW, y + posterH, x, y + posterH, radius);
        ctx.arcTo(x, y + posterH, x, y, radius);
        ctx.arcTo(x, y, x + posterW, y, radius);
        ctx.closePath();
        ctx.clip();
        if (img) {
          ctx.drawImage(img, x, y, posterW, posterH);
        } else {
          ctx.fillStyle = "#20212a";
          ctx.fillRect(x, y, posterW, posterH);
        }
        ctx.restore();

        ctx.textAlign = "center";
        ctx.fillStyle = "#facc15"; // mirrors --gold in App.css — Canvas can't resolve CSS vars
        ctx.font = "11px 'Plus Jakarta Sans', sans-serif";
        ctx.fillText("★".repeat(r.overall || 0), x + posterW / 2, y + posterH + 16);
      });
      y += posterH + 40;
    }

    ctx.textAlign = "left";
    ctx.fillStyle = "#71717a";
    ctx.font = "bold 12px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText("BIGGEST MOVERS THIS MONTH", 36, y);
    y += 34;

    summary.topShifts.forEach((s) => {
      ctx.fillStyle = "#ffffff";
      ctx.font = "16px 'Plus Jakarta Sans', sans-serif";
      ctx.fillText(s.label, 36, y);

      ctx.textAlign = "right";
      ctx.fillStyle = s.delta >= 0 ? "#4ade80" : "#f87171"; // mirrors --success/--danger — Canvas can't resolve CSS vars
      ctx.font = "bold 16px 'Plus Jakarta Sans', sans-serif";
      ctx.fillText(shiftLabel(s), CARD_W - 36, y);
      ctx.textAlign = "left";
      y += 40;
    });

    drawCardFooter(ctx);

    setexporting(false);
    canvas.toBlob((blob) => {
      shareOrDownloadBlob(blob, `${username}-wrapped-${month}.png`, {
        title: "My Re:Watch Wrapped",
        text: `My ${summary.period} on Re:Watch: ${summary.archetype}`
      });
    });
  }

  return (
    <div className="page-shell">
      <div className="page-panel taste-panel">
        <div className="taste-panel-head">
          <div>
            <span className="eyebrow">Monthly Wrapped</span>
            <h1>{summary ? summary.period : "Your Recap"}</h1>
          </div>

          <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
            <input
              type="month"
              value={month}
              max={thisMonth}
              onChange={(e) => e.target.value && setmonth(e.target.value)}
              className="wrapped-month-picker"
              aria-label="Select month"
            />
            {summary && summary.hasData && (
              <button type="button" onClick={exportWrappedPNG} className="btn-primary" disabled={exporting}>
                {exporting ? "Exporting..." : "Share Card"}
              </button>
            )}
          </div>
        </div>

        {loading && (
          <div className="feed-state">
            <div className="loading-orb" />
            <p>Building your recap...</p>
          </div>
        )}

        {err && <p className="status-message status-message--error">{err}</p>}

        {!loading && summary && !summary.hasData && (
          <EmptyState message={`No ratings yet in ${summary.period} — rate a few titles and this will fill in.`} />
        )}

        {!loading && summary && summary.hasData && (
          <>
            <div className="archetype-banner">
              <div className="archetype-banner-row">
                <span className="archetype-label">Archetype this month: {summary.archetype}</span>
                <span className="archetype-confidence">{summary.ratingCount} title{summary.ratingCount === 1 ? "" : "s"} rated</span>
              </div>
              <p className="archetype-blurb">{summary.archetypeBlurb}</p>
            </div>

            {summary.topShifts.length > 0 && (
              <div className="moment-card">
                <p className="moment-eyebrow">Biggest Shift This Month</p>
                <h3 className="moment-title">{summary.topShifts[0].label}</h3>
                <p className="moment-sub">
                  {pct(summary.topShifts[0].startVal)}% → {pct(summary.topShifts[0].endVal)}%
                  {" "}({shiftLabel(summary.topShifts[0])})
                </p>
              </div>
            )}

            <p className="section-eyebrow">Biggest movers</p>
            <div className="trait-list">
              {summary.topShifts.map((s) => (
                <div key={s.key} className="trait-card">
                  <div className="trait-card-row">
                    <span className="trait-name">{s.label}</span>
                    <span
                      className="trait-percent"
                      style={{ color: s.delta >= 0 ? "var(--success)" : "var(--danger)" }}
                    >
                      {shiftLabel(s)}
                    </span>
                  </div>
                  <div className="trait-meta-row">
                    <span>{pct(s.startVal)}% → {pct(s.endVal)}%</span>
                  </div>
                </div>
              ))}
            </div>

            {summary.topRatings.length > 0 && (
              <>
                <p className="section-eyebrow">Top rated this month</p>
                <div className="mini-card-grid" style={{ marginBottom: "var(--sp-4)" }}>
                  {summary.topRatings.map((r) => (
                    <article className="mini-card" key={r.ratingId}>
                      <div className="mini-card-poster">
                        <img src={poster(r.poster)} alt={`${r.title} poster`} loading="lazy" />
                      </div>
                      <div className="mini-card-body">
                        <h4>{r.title}</h4>
                        <span style={{ fontSize: "0.76rem", color: "var(--gold)" }}>
                          {"★".repeat(r.overall || 0)}
                        </span>
                      </div>
                    </article>
                  ))}
                </div>
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}

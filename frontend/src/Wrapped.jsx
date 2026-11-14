import { useEffect, useState } from "react";
import { authHeaders } from "./auth";
import { BASE } from "./api";
import {
  CARD_SCALE,
  CARD_W,
  CARD_H,
  drawCardFrame,
  drawCardFooter,
  drawVerticallyCentered,
  loadImage,
  shareOrDownloadBlob,
  CARD_FONT,
  ensureCardFonts,
  compareUrl,
  CARD_FOOTER_TOP,
  toBlobSafe
} from "./shareCard";
import EmptyState from "./EmptyState";
import Ree from "./Ree";
import "./App.css";

const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w200";
const FALLBACK_POSTER = "https://placehold.co/200x300/16233f/45c5b4?text=Re:Watch";

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
    await ensureCardFonts();
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

    drawVerticallyCentered(ctx, 146, CARD_FOOTER_TOP, (c, startY) => {
    c.textAlign = "center";
    c.fillStyle = "#ffffff";
    c.font = `700 32px ${CARD_FONT}`;
    c.fillText(summary.period, CARD_W / 2, startY);

    c.fillStyle = "#a1a1aa";
    c.font = `400 14px ${CARD_FONT}`;
    c.fillText(`${username} · ${summary.ratingCount} title${summary.ratingCount === 1 ? "" : "s"} rated`, CARD_W / 2, startY + 26);

    c.fillStyle = "#d8b4fe";
    c.font = `700 19px ${CARD_FONT}`;
    c.fillText(summary.archetype, CARD_W / 2, startY + 68);

    // Poster collage — the visual hook. Real cover art, not just numbers.
    let y = startY + 104;
    if (posterImages.some(Boolean)) {
      c.textAlign = "left";
      c.fillStyle = "#71717a";
      c.font = `700 12px ${CARD_FONT}`;
      c.fillText("TOP RATED", 36, y);
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
        c.save();
        c.beginPath();
        const radius = 8;
        c.moveTo(x + radius, y);
        c.arcTo(x + posterW, y, x + posterW, y + posterH, radius);
        c.arcTo(x + posterW, y + posterH, x, y + posterH, radius);
        c.arcTo(x, y + posterH, x, y, radius);
        c.arcTo(x, y, x + posterW, y, radius);
        c.closePath();
        c.clip();
        if (img) {
          c.drawImage(img, x, y, posterW, posterH);
        } else {
          c.fillStyle = "#223459";
          c.fillRect(x, y, posterW, posterH);
        }
        c.restore();

        c.textAlign = "center";
        c.fillStyle = "#facc15"; // mirrors --gold in App.css — Canvas can't resolve CSS vars
        c.font = `400 11px ${CARD_FONT}`;
        c.fillText("★".repeat(r.overall || 0), x + posterW / 2, y + posterH + 16);
      });
      y += posterH + 40;
    }

    c.textAlign = "left";
    c.fillStyle = "#71717a";
    c.font = `700 12px ${CARD_FONT}`;
    c.fillText("BIGGEST MOVERS THIS MONTH", 36, y);
    y += 34;

    summary.topShifts.forEach((s) => {
      c.fillStyle = "#ffffff";
      c.font = `400 16px ${CARD_FONT}`;
      c.fillText(s.label, 36, y);

      c.textAlign = "right";
      c.fillStyle = s.delta >= 0 ? "#7fe0be" : "#f08a9b"; // mirrors --success/--danger — Canvas can't resolve CSS vars
      c.font = `700 16px ${CARD_FONT}`;
      c.fillText(shiftLabel(s), CARD_W - 36, y);
      c.textAlign = "left";
      y += 40;
    });

    return y;
    });

    drawCardFooter(ctx, { url: compareUrl(username, "card") });

    setexporting(false);
    toBlobSafe(canvas, (blob) => {
      shareOrDownloadBlob(blob, `${username}-wrapped-${month}.png`, {
        kind: "wrapped",
        url: compareUrl(username, "share"),
        title: "My Re:Watch Wrapped",
        text: `My ${summary.period} on Re:Watch: ${summary.archetype}`
      });
    });
  }

  return (
    <div className="page-shell page-shell--wide">
      <div className="page-panel taste-panel">
        <div className="page-hero">
          <div className="page-hero-copy">
            <span className="eyebrow">Monthly Wrapped</span>
            <h1>{summary ? summary.period : "Your Recap"}</h1>
            <p className="page-hero-sub">Your month in taste — what moved, what stayed.</p>
          </div>
          <Ree pose="cinema" size={120} className="page-hero-ree" />

          <div className="page-hero-actions">
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

        {err && (
          <div className="inline-error">
            <p className="status-message status-message--error">{err}</p>
            <button type="button" onClick={() => load(month)}>Try again</button>
          </div>
        )}

        {!loading && summary && !summary.hasData && (
          <EmptyState mood="welcome" message={`No ratings yet in ${summary.period} — rate a few titles and this will fill in.`} />
        )}

        {!loading && summary && summary.hasData && (
          <>
            <div className="archetype-banner">
              <div className="archetype-banner-row">
                <span className="archetype-label">Archetype this month: {summary.archetype}</span>
                <span className="archetype-confidence"><span className="mono">{summary.ratingCount}</span> title{summary.ratingCount === 1 ? "" : "s"} rated</span>
              </div>
              <p className="archetype-blurb">{summary.archetypeBlurb}</p>
            </div>

            {summary.topShifts.length > 0 && (
              <div className="moment-card">
                <p className="moment-eyebrow">Biggest Shift This Month</p>
                <h2 className="moment-title">{summary.topShifts[0].label}</h2>
                <p className="moment-sub">
                  <span className="mono">{pct(summary.topShifts[0].startVal)}% → {pct(summary.topShifts[0].endVal)}%</span>
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
                    <span className="mono">{pct(s.startVal)}% → {pct(s.endVal)}%</span>
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
                        <h3>{r.title}</h3>
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

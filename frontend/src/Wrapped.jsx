import { useEffect, useState } from "react";
import { authHeaders } from "./auth";
import { BASE } from "./api";
import { drawBrandMark } from "./canvasBrandMark";
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
    // user is most likely to see it.
    return `${arrow} ${sign}${(shift.delta * 100).toFixed(1)}pts`;
  }

  function exportWrappedPNG() {
    if (!summary) return;
    setexporting(true);

    const scale = 2;
    const cardW = 560;
    const cardH = 700;

    const canvas = document.createElement("canvas");
    canvas.width = cardW * scale;
    canvas.height = cardH * scale;
    const ctx = canvas.getContext("2d");
    ctx.scale(scale, scale);

    const grad = ctx.createLinearGradient(0, 0, cardW, cardH);
    grad.addColorStop(0, "#1f2029");
    grad.addColorStop(1, "#14151a");
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, cardW, cardH);
    ctx.strokeStyle = "rgba(168,85,247,0.5)";
    ctx.lineWidth = 2;
    ctx.strokeRect(1, 1, cardW - 2, cardH - 2);

    drawBrandMark(ctx, cardW / 2, 20, 13);

    ctx.textAlign = "center";
    ctx.fillStyle = "#a855f7";
    ctx.font = "bold 12px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText("RE:WATCH WRAPPED", cardW / 2, 56);

    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 24px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText(summary.period, cardW / 2, 92);

    ctx.fillStyle = "#a1a1aa";
    ctx.font = "13px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText(`${username} · ${summary.ratingCount} title${summary.ratingCount === 1 ? "" : "s"} rated`, cardW / 2, 114);

    ctx.fillStyle = "#d8b4fe";
    ctx.font = "bold 15px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText(summary.archetype, cardW / 2, 146);

    let y = 190;
    ctx.textAlign = "left";
    ctx.fillStyle = "#71717a";
    ctx.font = "bold 11px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText("BIGGEST MOVERS THIS MONTH", 32, y);
    y += 26;

    summary.topShifts.forEach((s) => {
      ctx.fillStyle = "#ffffff";
      ctx.font = "14px 'Plus Jakarta Sans', sans-serif";
      ctx.fillText(s.label, 32, y);

      ctx.textAlign = "right";
      ctx.fillStyle = s.delta >= 0 ? "#4ade80" : "#f87171";
      ctx.font = "bold 14px 'Plus Jakarta Sans', sans-serif";
      ctx.fillText(shiftLabel(s), cardW - 32, y);
      ctx.textAlign = "left";
      y += 30;
    });

    y += 16;
    ctx.fillStyle = "#71717a";
    ctx.font = "bold 11px 'Plus Jakarta Sans', sans-serif";
    ctx.fillText("TOP RATED", 32, y);
    y += 26;

    summary.topRatings.slice(0, 5).forEach((r) => {
      ctx.fillStyle = "#ffffff";
      ctx.font = "14px 'Plus Jakarta Sans', sans-serif";
      const title = r.title.length > 34 ? r.title.slice(0, 34) + "…" : r.title;
      ctx.fillText(title, 32, y);

      ctx.textAlign = "right";
      ctx.fillStyle = "#facc15";
      ctx.font = "13px 'Plus Jakarta Sans', sans-serif";
      ctx.fillText("★".repeat(r.overall || 0), cardW - 32, y);
      ctx.textAlign = "left";
      y += 26;
    });

    setexporting(false);
    canvas.toBlob((blob) => {
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = `${username}-wrapped-${month}.png`;
      link.click();
      URL.revokeObjectURL(link.href);
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
                {exporting ? "Exporting..." : "Download Card"}
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
          <div className="feed-state">
            No ratings yet in {summary.period} — rate a few titles and this will fill in.
          </div>
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

            <p className="section-eyebrow">Biggest movers</p>
            <div className="trait-list">
              {summary.topShifts.map((s) => (
                <div key={s.key} className="trait-card">
                  <div className="trait-card-row">
                    <span className="trait-name">{s.label}</span>
                    <span
                      className="trait-percent"
                      style={{ color: s.delta >= 0 ? "#4ade80" : "#f87171" }}
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
                        <span style={{ fontSize: "0.76rem", color: "#facc15" }}>
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

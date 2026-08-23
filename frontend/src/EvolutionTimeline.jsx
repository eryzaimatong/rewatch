import { useEffect, useMemo, useState } from "react";
import { authHeaders } from "./auth";
import { BASE } from "./api";

/**
 * Multi-line evolution chart, reading `/api/tastedna/history/{userId}` —
 * this is what turns "your taste evolved" from a label into a queryable,
 * causally-attributed timeline (each milestone names the rating that moved it).
 *
 * Capped at 3 simultaneous lines. The dataviz skill's validator: purple +
 * orange + aqua clears every check at --pairs all (worst CVD ΔE 9.4, worst
 * normal-vision ΔE 26.5), but a 4th line does not — the reference palette's
 * own doc calls out the same wall ("the fourth slot puts yellow and orange
 * on screen together, and that pair fails"). So the UI caps at 3 rather than
 * silently shipping an unvalidated 4th hue.
 *
 * Color follows the entity, not its rank: toggling a trait off frees its
 * color slot for the next selection, but never repaints the lines still on
 * screen — see `assignment` below.
 */

const LINE_COLORS = ["#a855f7", "#d95926", "#199e70"];
const MAX_LINES = 3;

const W = 640;
const H = 220;
const PAD = { top: 14, right: 16, bottom: 26, left: 34 };
const innerW = W - PAD.left - PAD.right;
const innerH = H - PAD.top - PAD.bottom;

function formatDate(t) {
  const d = new Date(t);
  if (Number.isNaN(d.getTime())) {
    return t;
  }
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export default function EvolutionTimeline({ userId, allTraits, defaultKeys }) {
  const [history, setHistory] = useState(null);
  const [loading, setLoading] = useState(true);
  const [journey, setJourney] = useState(null);
  const [visibleKeys, setVisibleKeys] = useState(defaultKeys.slice(0, MAX_LINES));
  const [assignment, setAssignment] = useState(() => {
    const a = {};
    defaultKeys.slice(0, MAX_LINES).forEach((k, i) => (a[k] = i));
    return a;
  });
  const [hoverIdx, setHoverIdx] = useState(null);

  async function loadHistory() {
    setLoading(true);
    const res = await fetch(`${BASE}/api/tastedna/history/${userId}?granularity=day`, { headers: authHeaders() }).catch(() => null);
    const data = res && res.ok ? await res.json() : null;
    setHistory(data);
    setLoading(false);
  }

  async function loadJourney() {
    const res = await fetch(`${BASE}/api/tastedna/journey/${userId}`, { headers: authHeaders() }).catch(() => null);
    setJourney(res && res.ok ? await res.json() : []);
  }

  useEffect(() => {
    // Standard fetch-on-prop-change: set loading, await, set data. The
    // set-state-in-effect rule flags this because the effect re-runs on a
    // dependency change (unlike a mount-only `[]` effect), but there's no
    // external subscription to model this as — a fetch keyed on `userId`
    // is exactly what an effect is for.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadHistory();
    loadJourney();
  }, [userId]);

  function toggleTrait(key) {
    setVisibleKeys((current) => {
      if (current.includes(key)) {
        setAssignment((a) => {
          const next = { ...a };
          delete next[key];
          return next;
        });
        return current.filter((k) => k !== key);
      }
      if (current.length >= MAX_LINES) {
        return current;
      }
      setAssignment((a) => {
        const used = new Set(Object.values(a));
        let slot = 0;
        while (used.has(slot)) slot++;
        return { ...a, [key]: slot };
      });
      return [...current, key];
    });
  }

  const seriesByKey = useMemo(() => {
    const map = {};
    (history?.series ?? []).forEach((s) => (map[s.trait] = s));
    return map;
  }, [history]);

  const pointCount = history?.series?.[0]?.points?.length ?? 0;
  const xFor = (i) => (pointCount <= 1 ? PAD.left + innerW / 2 : PAD.left + (innerW * i) / (pointCount - 1));
  const yFor = (v) => PAD.top + innerH * (1 - Math.max(0, Math.min(1, v)));

  function handleMouseMove(e) {
    if (pointCount === 0) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const fracX = (e.clientX - rect.left) / rect.width;
    const svgX = fracX * W;
    const idx = pointCount <= 1
      ? 0
      : Math.round(((svgX - PAD.left) / innerW) * (pointCount - 1));
    setHoverIdx(Math.max(0, Math.min(pointCount - 1, idx)));
  }

  if (loading) {
    return (
      <div className="timeline-card">
        <div className="feed-state" style={{ minHeight: "120px" }}>
          <div className="loading-orb" />
        </div>
      </div>
    );
  }

  if (!history || pointCount === 0) {
    return (
      <div className="timeline-card">
        <p className="timeline-empty">
          Your evolution timeline will appear here once your profile has a rating history to plot.
        </p>
      </div>
    );
  }

  const referenceSeries = history.series[0];
  const hoverPoint = hoverIdx != null ? referenceSeries.points[hoverIdx] : null;
  const milestones = [...(history.milestones ?? [])].reverse().slice(0, 5);

  return (
    <div className="timeline-card">
      <div className="timeline-head">
        <span className="eyebrow" style={{ letterSpacing: "0.08em" }}>Your Taste Evolved</span>
        <div className="timeline-legend">
          {allTraits.map((t) => {
            const active = visibleKeys.includes(t.key);
            const colorIdx = assignment[t.key];
            const full = !active && visibleKeys.length >= MAX_LINES;
            return (
              <button
                key={t.key}
                type="button"
                onClick={() => toggleTrait(t.key)}
                disabled={full}
                className={`timeline-legend-chip${active ? " active" : ""}`}
                style={active ? { background: LINE_COLORS[colorIdx] } : undefined}
                title={full ? "Up to 3 traits at once — deselect one first" : undefined}
              >
                {t.label}
              </button>
            );
          })}
        </div>
      </div>

      <div className="timeline-svg-wrap">
        {/* The mouse-driven crosshair here has no keyboard equivalent — the
            milestone list rendered below (always visible, not hover-gated)
            is the real accessible equivalent of "what this chart shows",
            same pattern as TraitRadar. */}
        <svg
          viewBox={`0 0 ${W} ${H}`}
          width="100%"
          style={{ height: "auto" }}
          role="img"
          aria-label={`Trait evolution over time for ${visibleKeys.map((k) => seriesByKey[k]?.label).filter(Boolean).join(", ")}`}
          onMouseMove={handleMouseMove}
          onMouseLeave={() => setHoverIdx(null)}
        >
          {[0, 0.25, 0.5, 0.75, 1].map((level) => (
            <line
              key={level}
              x1={PAD.left}
              x2={W - PAD.right}
              y1={yFor(level)}
              y2={yFor(level)}
              className="timeline-gridline"
            />
          ))}
          {[0, 0.5, 1].map((level) => (
            <text key={level} x={PAD.left - 8} y={yFor(level)} dy="0.32em" textAnchor="end" className="timeline-axis-label mono">
              {Math.round(level * 100)}%
            </text>
          ))}

          {pointCount === 1 && (
            <text x={xFor(0)} y={H - 6} textAnchor="middle" className="timeline-axis-label">
              {formatDate(referenceSeries.points[0].t)}
            </text>
          )}
          {pointCount > 1 && (
            <>
              <text x={xFor(0)} y={H - 6} textAnchor="start" className="timeline-axis-label">
                {formatDate(referenceSeries.points[0].t)}
              </text>
              <text x={xFor(pointCount - 1)} y={H - 6} textAnchor="end" className="timeline-axis-label">
                {formatDate(referenceSeries.points[pointCount - 1].t)}
              </text>
            </>
          )}

          {hoverIdx != null && (
            <line
              x1={xFor(hoverIdx)}
              x2={xFor(hoverIdx)}
              y1={PAD.top}
              y2={H - PAD.bottom}
              className="timeline-gridline"
              style={{ stroke: "var(--border-strong)" }}
            />
          )}

          {visibleKeys.map((key) => {
            const series = seriesByKey[key];
            if (!series) return null;
            const color = LINE_COLORS[assignment[key]];
            const d = series.points
              .map((p, i) => `${i === 0 ? "M" : "L"}${xFor(i).toFixed(2)},${yFor(p.val).toFixed(2)}`)
              .join(" ");
            return (
              <g key={key}>
                <path d={d} className="timeline-line" style={{ stroke: color }} />
                {series.points.map((p, i) => (
                  <circle
                    key={i}
                    cx={xFor(i)}
                    cy={yFor(p.val)}
                    r={hoverIdx === i ? 5 : 3}
                    className="timeline-dot"
                    style={{ fill: color }}
                  >
                    <title>{`${series.label} — ${formatDate(p.t)}: ${Math.round(p.val * 100)}%`}</title>
                  </circle>
                ))}
              </g>
            );
          })}

          {milestones.map((m) => {
            const idx = referenceSeries.points.findIndex((p) => p.t === m.t.slice(0, 10));
            if (idx < 0) return null;
            const topTrait = m.topShifts?.[0];
            return (
              <path
                key={m.ratingId}
                d={`M${xFor(idx)},${PAD.top - 6} l4,6 l-4,6 l-4,-6 Z`}
                fill="var(--primary-light)"
                stroke="var(--background)"
                strokeWidth="1"
              >
                <title>
                  {`${m.title}${m.stars ? ` (${m.stars}★)` : ""}${topTrait ? ` — ${topTrait.label} ${topTrait.delta >= 0 ? "+" : ""}${Math.round(topTrait.delta * 100)}%` : ""}`}
                </title>
              </path>
            );
          })}
        </svg>

        {hoverPoint && (
          <div
            className="page-panel"
            style={{
              position: "absolute",
              top: 4,
              left: `${(xFor(hoverIdx) / W) * 100}%`,
              transform: `translateX(${hoverIdx < pointCount / 2 ? "8px" : "calc(-100% - 8px)"})`,
              padding: "10px 12px",
              fontSize: "0.76rem",
              pointerEvents: "none",
              whiteSpace: "nowrap",
              zIndex: 2
            }}
          >
            <div style={{ color: "var(--text-faint)", marginBottom: "4px" }}>{formatDate(hoverPoint.t)}</div>
            {visibleKeys.map((key) => {
              const series = seriesByKey[key];
              const p = series?.points?.[hoverIdx];
              if (!p) return null;
              return (
                <div key={key} style={{ display: "flex", justifyContent: "space-between", gap: "12px" }}>
                  <span style={{ color: LINE_COLORS[assignment[key]] }}>{series.label}</span>
                  <strong className="mono">{Math.round(p.val * 100)}%</strong>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {milestones.length > 0 ? (
        <div className="timeline-milestones">
          {milestones.map((m) => {
            const topTrait = m.topShifts?.[0];
            return (
              <div key={m.ratingId} className="timeline-milestone-row">
                <span>
                  <strong>{m.title}</strong>
                  {m.stars ? ` rated ${m.stars}★` : ""} — {formatDate(m.t)}
                </span>
                {topTrait && (
                  <span>
                    {topTrait.label} <span className="mono">{topTrait.delta >= 0 ? "+" : ""}{Math.round(topTrait.delta * 100)}%</span>
                  </span>
                )}
              </div>
            );
          })}
        </div>
      ) : (
        <p className="timeline-empty">Rate a few titles and this will fill in with the story of how your taste moved.</p>
      )}

      {journey && journey.length > 0 && (
        <div className="taste-journey">
          <span className="eyebrow" style={{ letterSpacing: "0.08em", display: "block", marginBottom: "var(--sp-2)" }}>
            Your Taste Journey
          </span>
          <div className="taste-journey-list">
            {[...journey].reverse().map((entry) => (
              <div key={entry.period} className="taste-journey-row">
                <span className="taste-journey-period">{entry.period}</span>
                <p className="taste-journey-sentence">{entry.sentence}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

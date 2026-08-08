/**
 * A hand-rolled SVG radar chart for the 10-trait storytelling vector.
 *
 * Colors are validated, not eyeballed: purple (primary/user) vs --cyan
 * (#0284c7, secondary/movie) clears every check in the dataviz skill's validator
 * (CVD deutan ΔE 15.4, normal-vision ΔE 26.2 — both well above the 8/15
 * targets; lightness band, chroma floor, and contrast all pass) at
 * --pairs all against this app's dark surface. The one WARN band the
 * validator allows only with secondary encoding (the 6-8 CVD floor) isn't
 * even hit here, but the chart still ships a legend, a dashed stroke on
 * the secondary series, and direct hover values regardless — belt and
 * braces for anyone relying on hue alone.
 *
 * A radar isn't the validator's default recommendation for "compare
 * magnitude across categories" (a bar chart reads more precisely — angle
 * and area are harder judgments than length on a common baseline). It's
 * used here deliberately: this is the signature, shareable visualization
 * for the product, and the accessible fallback (a table view) already
 * exists as the trait-card list rendered alongside it.
 *
 * Animation is pure CSS (@keyframes, not a JS-driven transition toggle):
 * the animated elements are remounted via a `key` tied to the data
 * signature, so a fresh draw-in plays whenever the underlying values
 * change, with no effect/setState timing dance required. The existing
 * global `prefers-reduced-motion` rule in App.css already collapses
 * `animation-duration` to ~0 for every animation in the app, so reduced
 * motion is handled for free rather than re-implemented here.
 */

const LEVELS = 4;
const LABEL_GAP = 15;
const DOT_RADIUS = 4.5;

// The longest axis label ("Humor & Lightheartedness") plus LABEL_GAP needs real
// room in the viewBox itself, not just CSS `overflow: visible` — that works for
// inline DOM SVG (the label can bleed into surrounding page layout) but does
// NOT expand the raster canvas when this SVG is flattened to a bitmap (the
// share-card PNG export does exactly that via drawImage) — a rasterizer clips
// to the declared viewBox bounds regardless of overflow. Padding the viewBox
// itself keeps the export honest with what's on screen.
const VIEWBOX_PAD = 90;

function polar(cx, cy, r, angle) {
  return [cx + r * Math.cos(angle), cy + r * Math.sin(angle)];
}

function pointsToPath(points) {
  return points.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(2)},${y.toFixed(2)}`).join(" ") + " Z";
}

function signature(series) {
  if (!series) return "";
  return Object.entries(series.values)
    .map(([k, v]) => `${k}:${v.toFixed(3)}`)
    .join(",");
}

export default function TraitRadar({
  traits,
  primary,
  secondary,
  size = 340,
  className = "",
  svgId
}) {
  const n = traits.length;
  const cx = size / 2;
  const cy = size / 2;
  const maxR = size / 2 - 44;
  const angleStep = (2 * Math.PI) / n;
  const angleFor = (i) => -Math.PI / 2 + i * angleStep;

  function seriesPoints(series) {
    return traits.map((t, i) => {
      const v = Math.max(0, Math.min(1, series.values[t.key] ?? 0.5));
      return polar(cx, cy, maxR * v, angleFor(i));
    });
  }

  function confidenceBand(series) {
    const outer = [];
    const inner = [];
    traits.forEach((t, i) => {
      const v = series.values[t.key] ?? 0.5;
      const conf = series.confidences?.[t.key];
      if (conf == null) {
        return;
      }
      const spread = (1 - conf) * 0.22;
      const outerV = Math.max(0, Math.min(1, v + spread / 2));
      const innerV = Math.max(0, Math.min(1, v - spread / 2));
      outer.push(polar(cx, cy, maxR * outerV, angleFor(i)));
      inner.push(polar(cx, cy, maxR * innerV, angleFor(i)));
    });
    if (outer.length === 0) {
      return null;
    }
    return pointsToPath([...outer, ...inner.reverse()]);
  }

  const primaryPoints = seriesPoints(primary);
  const secondaryPoints = secondary ? seriesPoints(secondary) : null;
  const band = confidenceBand(primary);
  const drawKey = signature(primary) + "|" + signature(secondary);

  const labelAnchorFor = (angle) => {
    const cos = Math.cos(angle);
    if (cos > 0.3) return "start";
    if (cos < -0.3) return "end";
    return "middle";
  };

  return (
    <figure
      className={`radar-figure ${className}`}
      role="img"
      aria-label={`Storytelling trait radar for ${primary.label}${secondary ? ` compared with ${secondary.label}` : ""}`}
    >
      {/* The figure's role="img" + aria-label above is the single accessible
          description; without aria-hidden here, some screen readers would
          additionally walk in and read each of the 10 axis <text> labels
          one at a time, which is noise, not information — the real
          equivalent is the visible trait-card list rendered alongside this
          chart wherever it's used. */}
      <svg
        id={svgId}
        aria-hidden="true"
        viewBox={`${-VIEWBOX_PAD} ${-VIEWBOX_PAD} ${size + VIEWBOX_PAD * 2} ${size + VIEWBOX_PAD * 2}`}
        width="100%"
        style={{ maxWidth: size + VIEWBOX_PAD * 2, height: "auto" }}
      >
        <g>
          {/* Grid rings */}
          {Array.from({ length: LEVELS }, (_, l) => {
            const level = (l + 1) / LEVELS;
            const pts = traits.map((t, i) => polar(cx, cy, maxR * level, angleFor(i)));
            return <path key={l} d={pointsToPath(pts)} className="radar-grid-ring" fill="none" />;
          })}

          {/* Spokes */}
          {traits.map((t, i) => {
            const [x, y] = polar(cx, cy, maxR, angleFor(i));
            return <line key={t.key} x1={cx} y1={cy} x2={x} y2={y} className="radar-spoke" />;
          })}

          {/* Confidence band — geometry, not a footnote: a wide band IS low confidence */}
          {band && <path d={band} className="radar-confidence-band" style={{ fill: primary.color }} />}

          {/* key={drawKey} remounts this subtree (and replays its CSS animation)
              whenever the underlying values change, e.g. after a rating. */}
          <g key={drawKey}>
            {secondaryPoints && (
              <path
                d={pointsToPath(secondaryPoints)}
                className="radar-series-path radar-series-secondary"
                style={{ stroke: secondary.color, fill: secondary.color }}
                pathLength="100"
              />
            )}

            <path
              d={pointsToPath(primaryPoints)}
              className="radar-series-path radar-series-primary"
              style={{ stroke: primary.color, fill: primary.color }}
              pathLength="100"
            />

            {traits.map((t, i) => {
              const [x, y] = primaryPoints[i];
              const pct = Math.round((primary.values[t.key] ?? 0.5) * 100);
              return (
                <circle
                  key={t.key}
                  cx={x}
                  cy={y}
                  r={DOT_RADIUS}
                  className="radar-dot radar-dot-animated"
                  style={{ fill: primary.color, "--radar-dot-i": i }}
                >
                  <title>{`${t.label}: ${pct}%`}</title>
                </circle>
              );
            })}

            {secondaryPoints && traits.map((t, i) => {
              const [x, y] = secondaryPoints[i];
              const pct = Math.round((secondary.values[t.key] ?? 0.5) * 100);
              return (
                <circle
                  key={`sec-${t.key}`}
                  cx={x}
                  cy={y}
                  r={DOT_RADIUS - 1}
                  className="radar-dot radar-dot-animated radar-dot-secondary"
                  style={{ fill: secondary.color, "--radar-dot-i": i }}
                >
                  <title>{`${secondary.label} — ${t.label}: ${pct}%`}</title>
                </circle>
              );
            })}
          </g>

          {/* Axis labels — direct labels are cheap here: only 10 fixed positions, not a per-point flood */}
          {traits.map((t, i) => {
            const angle = angleFor(i);
            const [x, y] = polar(cx, cy, maxR + LABEL_GAP, angle);
            const sin = Math.sin(angle);
            const dy = sin > 0.3 ? "0.9em" : sin < -0.3 ? "-0.3em" : "0.35em";
            return (
              <text key={t.key} x={x} y={y} dy={dy} textAnchor={labelAnchorFor(angle)} className="radar-axis-label">
                {t.label}
              </text>
            );
          })}
        </g>
      </svg>

      {secondary && (
        <figcaption className="radar-legend">
          <span className="radar-legend-item">
            <span className="radar-legend-swatch" style={{ background: primary.color }} />
            {primary.label}
          </span>
          <span className="radar-legend-item">
            <span className="radar-legend-swatch radar-legend-swatch--dashed" style={{ borderColor: secondary.color }} />
            {secondary.label}
          </span>
        </figcaption>
      )}
    </figure>
  );
}

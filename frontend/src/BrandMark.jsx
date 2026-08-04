/**
 * The RE:WATCH mark: a film-frame aperture with a small radar polygon at its
 * center, tying the logo directly to the TasteDNA visualization elsewhere in
 * the app — the same decagon shape that draws in on the profile page and the
 * movie modal shows up here at a glance, in miniature.
 */
export default function BrandMark({ size = 26 }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      aria-hidden="true"
      className="brand-mark-svg"
    >
      <circle cx="16" cy="16" r="14.5" stroke="url(#brandmark-gradient)" strokeWidth="1.6" />
      {/* Aperture blades */}
      {Array.from({ length: 6 }, (_, i) => {
        const angle = (i * 60 * Math.PI) / 180;
        const x1 = 16 + 11 * Math.cos(angle);
        const y1 = 16 + 11 * Math.sin(angle);
        const x2 = 16 + 14.5 * Math.cos(angle);
        const y2 = 16 + 14.5 * Math.sin(angle);
        return (
          <line
            key={i}
            x1={x1}
            y1={y1}
            x2={x2}
            y2={y2}
            stroke="url(#brandmark-gradient)"
            strokeWidth="1.4"
            strokeLinecap="round"
            opacity="0.55"
          />
        );
      })}
      {/* Inner radar polygon (decagon, echoing TraitRadar) */}
      <polygon
        points={Array.from({ length: 10 }, (_, i) => {
          const angle = -Math.PI / 2 + (i * 2 * Math.PI) / 10;
          const r = i % 2 === 0 ? 8 : 5.5;
          return `${(16 + r * Math.cos(angle)).toFixed(1)},${(16 + r * Math.sin(angle)).toFixed(1)}`;
        }).join(" ")}
        fill="url(#brandmark-gradient)"
        fillOpacity="0.85"
      />
      <defs>
        <linearGradient id="brandmark-gradient" x1="0" y1="0" x2="32" y2="32">
          <stop offset="0%" stopColor="#c084fc" />
          <stop offset="100%" stopColor="#7c3aed" />
        </linearGradient>
      </defs>
    </svg>
  );
}

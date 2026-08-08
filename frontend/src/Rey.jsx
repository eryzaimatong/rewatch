/**
 * "Rey" — the one collectible bit of personality this app allows itself,
 * used only in empty/loading/error states (never in place of real data,
 * never claiming to "feel" anything about your actual taste profile). Built
 * from the exact same vocabulary as BrandMark — the aperture ring, the
 * radar decagon, the purple gradient — with two eyes and a mouth added, so
 * it reads as "the brand mark, personified" rather than a disconnected
 * cartoon character bolted onto the product.
 *
 * No new art assets: still just SVG primitives, same as every other icon
 * in this app (BrandMark, MatchRing, etc).
 */
const MOODS = {
  loading: { mouth: "M 12 20 Q 16 20 20 20", eyeScaleY: 1, label: "Rey is digging through the archives..." },
  empty: { mouth: "M 12 21 Q 16 18 20 21", eyeScaleY: 1, label: "Rey couldn't find anything here." },
  error: { mouth: "M 12 22 Q 16 19 20 22", eyeScaleY: 0.4, label: "Rey dropped the film reel." },
  happy: { mouth: "M 12 19 Q 16 23 20 19", eyeScaleY: 1, label: "Rey" }
};

export default function Rey({ mood = "happy", size = 64, className = "" }) {
  const { mouth, eyeScaleY, label } = MOODS[mood] || MOODS.happy;

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 32 32"
      fill="none"
      role="img"
      aria-label={label}
      className={`rey-mascot rey-mascot--${mood} ${className}`}
    >
      <circle cx="16" cy="16" r="14.5" stroke="url(#rey-gradient)" strokeWidth="1.6" />
      {Array.from({ length: 6 }, (_, i) => {
        const angle = (i * 60 * Math.PI) / 180;
        const x1 = 16 + 11 * Math.cos(angle);
        const y1 = 16 + 11 * Math.sin(angle);
        const x2 = 16 + 14.5 * Math.cos(angle);
        const y2 = 16 + 14.5 * Math.sin(angle);
        return (
          <line
            key={i}
            x1={x1} y1={y1} x2={x2} y2={y2}
            stroke="url(#rey-gradient)"
            strokeWidth="1.4"
            strokeLinecap="round"
            opacity="0.4"
          />
        );
      })}
      <polygon
        points={Array.from({ length: 10 }, (_, i) => {
          const angle = -Math.PI / 2 + (i * 2 * Math.PI) / 10;
          const r = i % 2 === 0 ? 8 : 5.5;
          return `${(16 + r * Math.cos(angle)).toFixed(1)},${(16 + r * Math.sin(angle)).toFixed(1)}`;
        }).join(" ")}
        fill="url(#rey-gradient)"
        fillOpacity="0.22"
      />
      {/* Eyes */}
      <ellipse className="rey-eye rey-eye-left" cx="12.5" cy="15" rx="1.6" ry={1.6 * eyeScaleY} fill="#fafafa" />
      <ellipse className="rey-eye rey-eye-right" cx="19.5" cy="15" rx="1.6" ry={1.6 * eyeScaleY} fill="#fafafa" />
      {/* Mouth */}
      <path d={mouth} stroke="#fafafa" strokeWidth="1.4" strokeLinecap="round" fill="none" />
      <defs>
        <linearGradient id="rey-gradient" x1="0" y1="0" x2="32" y2="32">
          <stop offset="0%" stopColor="#c084fc" />
          <stop offset="100%" stopColor="#7c3aed" />
        </linearGradient>
      </defs>
    </svg>
  );
}

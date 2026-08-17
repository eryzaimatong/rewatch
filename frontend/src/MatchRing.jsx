/**
 * Replaces the flat "88% match" pill, which rendered a 42% and a 97% score
 * with identical visual weight — the number was the only signal.
 *
 * The ring encodes magnitude with a single hue at varying lightness
 * (sequential, per the dataviz skill: "one hue, more-is-darker/brighter" —
 * never a hue swap on a magnitude scale, that's the "rainbow" anti-pattern).
 * Purple is already this app's "system's judgment about you" color, so a
 * dim, muted arc at low confidence and a bright, saturated one at high
 * confidence stays inside the established purple-discipline rule rather
 * than introducing a second accent color for the same meaning.
 *
 * Draws in from empty on mount rather than appearing already-at-value — the
 * ring's `transition` (see App.css) only animates a value that *changes*
 * after mount, so without this it painted at its final state on the very
 * first frame, silent, on every card in the feed simultaneously. Starting
 * at 0 and flipping to the real offset one frame later gives the transition
 * something to animate, so the "here's your match" moment actually reads as
 * one — the single most-repeated visual in the app, so this is the highest-
 * leverage place for that first impression to land.
 */
import { useEffect, useState } from "react";

const R = 15;
const CIRCUMFERENCE = 2 * Math.PI * R;

function colorForScore(score) {
  // Purple hue held constant; lightness ramps with score. Light end still
  // clears ~2:1 against the dark card surface it sits on.
  const t = Math.max(0, Math.min(1, score / 100));
  const lightness = 38 + t * 30; // 38% (dim) -> 68% (bright)
  const saturation = 55 + t * 30; // 55% -> 85%
  return `hsl(271, ${saturation}%, ${lightness}%)`;
}

export default function MatchRing({ score, size = 44 }) {
  // Hooks first, unconditionally — the "no score, render nothing" bailout
  // below can't come before these without breaking the rules of hooks.
  const [drawn, setdrawn] = useState(false);
  useEffect(() => {
    const id = requestAnimationFrame(() => setdrawn(true));
    return () => cancelAnimationFrame(id);
  }, []);

  // No fallback default — an absent score renders nothing rather than a
  // fabricated number (this ring used to be fed `?? 60`/`?? 50` upstream).
  if (score === null || score === undefined || Number.isNaN(Number(score))) {
    return null;
  }

  const clamped = Math.max(0, Math.min(100, Math.round(score)));
  const offset = drawn ? CIRCUMFERENCE * (1 - clamped / 100) : CIRCUMFERENCE;
  const color = colorForScore(clamped);

  return (
    <div
      className="match-ring"
      style={{ width: size, height: size }}
      title={`${clamped}% match`}
      aria-label={`${clamped} percent match`}
    >
      <svg width={size} height={size} viewBox="0 0 36 36" aria-hidden="true">
        <circle cx="18" cy="18" r={R} className="match-ring-track" fill="none" />
        <circle
          cx="18"
          cy="18"
          r={R}
          fill="none"
          stroke={color}
          strokeWidth="3"
          strokeLinecap="round"
          strokeDasharray={CIRCUMFERENCE}
          strokeDashoffset={offset}
          transform="rotate(-90 18 18)"
          className="match-ring-arc"
        />
      </svg>
      <span className={`match-ring-label${drawn ? " is-visible" : ""}`} aria-hidden="true">{clamped}</span>
    </div>
  );
}

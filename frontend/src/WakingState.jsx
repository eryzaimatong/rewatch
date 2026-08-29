import { useState, useEffect } from "react";
import Rey from "./Rey";

const STAGE_2_AT_MS = 5_000;
const STAGE_3_AT_MS = 20_000;
const STAGE_4_AT_MS = 60_000;
const ROTATION_INTERVAL_MS = 20_000;
const TICK_MS = 1_000;

const DEFAULT_STAGE_4_MESSAGES = [
  "Still working on it — nothing's frozen, the server's just slow to stretch.",
  "While you wait: TasteDNA scores 10 different storytelling traits, from pacing to how bittersweet you like your endings.",
  "That radar below is a ‘Cozy Nostalgic’ profile — leans hard into warmth and memory. Yours might look nothing like it. That’s the point.",
  "Every match on Re:Watch comes from a real taste model — not popularity, not what’s trending."
];

/**
 * Staged, elapsed-time-aware loading/error state for a backend call that
 * might be waking a cold free-tier server (135-166s+ measured, see
 * DEPLOYMENT.md) rather than failing or completing quickly. Generic, not
 * specific to any one call — meant to front other routes with the same
 * problem. Page-specific "sell the product while they wait" content goes
 * through `children`, rendered once the honest-framing stage starts.
 *
 * `status` is a single enum rather than separate loading/error booleans —
 * both true at once was representable before and meant nothing.
 *
 * Stage-4 messages cycle by a deterministic index
 * (elapsed / ROTATION_INTERVAL_MS % length), not random selection, so the
 * sequence is reproducible and never repeats a message back-to-back.
 *
 * Every retry (timeout or network failure) restarts the stage clock at 0.
 * A resume-at-stage-3 alternative was considered for a post-timeout retry
 * specifically, on the theory the server is probably still mid-wake — but
 * by the time a human reads the error and taps retry, several more seconds
 * have passed on top of the 200s that already elapsed, often enough for
 * the boot to have finished underneath. Claiming "still waking up" on a
 * retry that resolves in under a second is worse than briefly
 * under-explaining one that's genuinely still slow, which self-corrects by
 * stage 2 (5s) and stage 3 (20s) regardless of where it started.
 */
export default function WakingState({
  status,
  errorKind,
  onRetry,
  subject = "this",
  stage4Messages = DEFAULT_STAGE_4_MESSAGES,
  children
}) {
  // No manual "reset elapsed to 0" here: the parent remounts this component
  // (via a `key` tied to its retry-attempt counter) on every fresh loading
  // attempt, so useState(0)'s own initial value handles the reset — avoids
  // calling setState synchronously within the effect body, which forces an
  // extra render on every mount for no benefit here.
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    if (status !== "loading") {
      return;
    }
    const start = Date.now();
    const id = setInterval(() => {
      setElapsed(Date.now() - start);
    }, TICK_MS);
    return () => clearInterval(id);
  }, [status]);

  if (status === "ready") {
    return null;
  }

  if (status === "error") {
    const timedOut = errorKind === "timeout";
    return (
      <div className="feed-state feed-error" role="alert">
        <Rey mood="error" size={56} />
        <h3>{timedOut ? "That took a while." : "Couldn't reach Re:Watch."}</h3>
        <p>
          {timedOut
            ? "That took far longer than it should have — the free server might be having a rough day."
            : "Couldn't reach Re:Watch's server. Check your connection and try again."}
        </p>
        {onRetry && (
          <button type="button" onClick={onRetry}>
            Try again
          </button>
        )}
      </div>
    );
  }

  const stage =
    elapsed < STAGE_2_AT_MS ? 1
    : elapsed < STAGE_3_AT_MS ? 2
    : elapsed < STAGE_4_AT_MS ? 3
    : 4;

  const message =
    stage === 1 ? `Pulling up ${subject}...`
    : stage === 2 ? "Still here — this is taking longer than usual."
    : stage === 3 ? "Re:Watch runs on a free server a student built, and it’s still waking up. Hang tight — it’s coming."
    : stage4Messages[Math.floor((elapsed - STAGE_4_AT_MS) / ROTATION_INTERVAL_MS) % stage4Messages.length];

  return (
    <div
      role="status"
      aria-live="polite"
      style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "var(--sp-2)" }}
    >
      <Rey mood="loading" size={56} />
      <div className="loading-orb" />
      <p style={{ color: "var(--text-muted)", minHeight: "1.5em" }}>{message}</p>
      {stage >= 3 && children}
    </div>
  );
}

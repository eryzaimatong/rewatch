import Ree from "../Ree";
import { useServerStatus } from "./serverStatus";

/**
 * One calm, honest line whenever what's on screen isn't live data yet.
 * Renders nothing for real data, so pages can drop it in unconditionally.
 */
export default function StatusBanner({ source, isFallback, isStale, isRevalidating, onRetry, subject = "this page" }) {
  const { phase } = useServerStatus();
  if (!isFallback && !isStale) return null;

  const waking = phase === "waking" || phase === "idle";
  let text;
  if (isFallback) {
    text = waking
      ? `Ree is waking the server. Here's a starting point for ${subject} — the real thing slides in the moment it answers.`
      : `We can't reach the server right now. Here's a starting point for ${subject} — nothing you've done is lost.`;
  } else if (isRevalidating) {
    text = `Showing your last saved version of ${subject} while we refresh it…`;
  } else {
    text = `Showing your last saved version of ${subject}. We couldn't refresh it just now.`;
  }

  return (
    <div className={`status-banner status-banner--${source}`} role="status" aria-live="polite">
      <Ree pose={isFallback && !waking ? "sad" : "thinking"} size={56} />
      <p>{text}</p>
      {!isRevalidating && onRetry && (
        <button type="button" className="pill" onClick={onRetry}>Try again</button>
      )}
    </div>
  );
}

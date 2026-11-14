import { Link } from "react-router-dom";
import Ree from "./Ree";

/**
 * Shared error-state shell — same .feed-state shell as EmptyState, but reads
 * as "something broke" rather than "nothing here yet" (title + Ree in her
 * error mood + a retry action). Consolidates what MovieFeed.jsx and
 * Dashboard.jsx each hand-rolled slightly differently.
 */
export default function ErrorState({ title = "Something went off-script.", message, onRetry, escapeTo, escapeLabel = "Pick a film by mood" }) {
  return (
    <div className="feed-state feed-error">
      <Ree mood="error" size={132} className="state-ree" />
      <h3>{title}</h3>
      {message && <p>{message}</p>}
      {onRetry && (
        <button type="button" onClick={onRetry}>
          Try again
        </button>
      )}
      {escapeTo && <Link to={escapeTo} className="feed-error-escape">{escapeLabel}</Link>}
    </div>
  );
}

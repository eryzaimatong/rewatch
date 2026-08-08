import Rey from "./Rey";

/**
 * Shared error-state shell — same .feed-state shell as EmptyState, but reads
 * as "something broke" rather than "nothing here yet" (title + Rey in his
 * error mood + a retry action). Consolidates what MovieFeed.jsx and
 * Dashboard.jsx each hand-rolled slightly differently.
 */
export default function ErrorState({ title = "Something went off-script.", message, onRetry }) {
  return (
    <div className="feed-state feed-error">
      <Rey mood="error" size={56} />
      <h3>{title}</h3>
      {message && <p>{message}</p>}
      {onRetry && (
        <button type="button" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

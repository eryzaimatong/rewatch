import Rey from "./Rey";

/**
 * Reusable empty state: Rey (in his "empty" mood — see Rey.jsx) + title +
 * message + optional action, on the existing .feed-state shell. Not for
 * loading (that's .loading-orb, a separate deliberate animation) or error
 * states (see ErrorState.jsx) — this is specifically for "there's genuinely
 * nothing here yet."
 */
export default function EmptyState({ title, message, action, className = "", style }) {
  return (
    <div className={`feed-state empty-state ${className}`} style={style}>
      <Rey mood="empty" size={64} />
      {title && <h3>{title}</h3>}
      {message && <p>{message}</p>}
      {action}
    </div>
  );
}

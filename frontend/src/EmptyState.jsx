import Ree from "./Ree";

/**
 * Reusable empty state: Ree (in her "empty" mood — see Ree.jsx) + title +
 * message + optional action, on the existing .feed-state shell. Not for
 * loading (that's .loading-orb, a separate deliberate animation) or error
 * states (see ErrorState.jsx) — this is specifically for "there's genuinely
 * nothing here yet."
 */
export default function EmptyState({ title, message, action, mood = "empty", className = "", style }) {
  return (
    <div className={`feed-state empty-state ${className}`} style={style}>
      <Ree mood={mood} size={132} className="state-ree" />
      {title && <h3>{title}</h3>}
      {message && <p>{message}</p>}
      {action}
    </div>
  );
}

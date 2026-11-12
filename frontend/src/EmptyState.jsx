import Ree from "./Ree";

/**
 * Reusable empty state: Ree (in her "empty" mood — see Ree.jsx) + title +
 * message + optional action, on the existing .feed-state shell. Not for
 * loading (that's .loading-orb, a separate deliberate animation) or error
 * states (see ErrorState.jsx) — this is specifically for "there's genuinely
 * nothing here yet."
 */
function Heading({ level, children }) {
  const Tag = `h${level}`;
  return <Tag>{children}</Tag>;
}

export default function EmptyState({ title, message, action, mood = "empty", compact = false, headingLevel = 3, className = "", style }) {
  return (
    <div className={`feed-state empty-state${compact ? " empty-state--compact" : ""} ${className}`} style={style}>
      <Ree mood={mood} size={compact ? 84 : 132} className="state-ree" />
      <div className="empty-state-copy">
        {title && <Heading level={headingLevel}>{title}</Heading>}
        {message && <p>{message}</p>}
        {action}
      </div>
    </div>
  );
}

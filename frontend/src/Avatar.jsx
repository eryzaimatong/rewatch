import "./App.css";

/**
 * Real photo if the user has set one, otherwise the existing initials-circle
 * fallback (same classes/gradient Community.jsx and SocialProfile.jsx already
 * used before an avatar was possible) — one component so every place a
 * person appears (nav, Community cards, profile header, collection cards)
 * stays in sync automatically.
 */
export default function Avatar({ username, avatarUrl, avatarFrame, size = 40, large = false, className = "" }) {
  const classes = `dna-match-avatar${large ? " dna-match-avatar--large" : ""}${className ? " " + className : ""}`;

  const inner = avatarUrl ? (
    <img
      src={avatarUrl}
      alt={`${username || "User"}'s avatar`}
      className={classes}
      style={{ width: size, height: size, objectFit: "cover" }}
    />
  ) : (
    <div className={classes} style={{ width: size, height: size }} aria-hidden="true">
      {username ? username.slice(0, 1).toUpperCase() : "?"}
    </div>
  );

  if (!avatarFrame) {
    return inner;
  }

  // The frame is a ring sized slightly larger than the avatar itself, not a
  // border on the avatar's own box — keeps the avatar's circle crisp instead
  // of eating into it.
  const framePad = Math.max(3, Math.round(size * 0.08));
  return (
    <div
      className={`avatar-frame avatar-frame--${avatarFrame}`}
      style={{ width: size + framePad * 2, height: size + framePad * 2, padding: framePad }}
      title={`Frame: ${avatarFrame}`}
    >
      {inner}
    </div>
  );
}

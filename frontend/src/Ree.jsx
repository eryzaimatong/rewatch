import { POSES, canonicalUrl, iconUrl } from "./reeAssets";

/**
 * Ree — Re:Watch's companion chameleon (see the Figma "Mascot design system").
 *
 * Rules from the board, enforced here rather than left to call sites:
 *  - "A companion, not a decoration": Ree appears where she says something
 *    (onboarding, loading, empty, error, mood choice) — never as wallpaper.
 *  - Emotion lives in the eyes/posture, so poses are the real photographed
 *    studio shots; the mood *accent* is applied by the surrounding UI, never
 *    by recoloring Ree.
 *  - Below 48px, simplify: `size < 48` swaps to the flat icon version.
 *
 * Usage:
 *   <Ree mood="error" size={120} />      // semantic mood (matches old API)
 *   <Ree pose="detective" size={160} />  // any of the 12 expression poses
 *   <Ree variant="canonical" size={420}/>// transparent cut-out hero render
 *   <Ree variant="icon" size={40} />     // flat app-icon version
 */
// Semantic moods used across the app -> expression poses.
const MOOD_TO_POSE = {
  welcome: "default",
  empty: "sleepy",
  error: "sad",
  loading: "thinking",
  success: "celebrating",
  search: "detective",
  lost: "curious",
  trailer: "cinema",
  romance: "lovestory",
  mystery: "mystery"
};

const POSE_ALT = {
  default: "Ree the chameleon smiling warmly",
  curious: "Ree the chameleon tilting her head, curious",
  excited: "Ree the chameleon jumping with joy",
  sleepy: "Ree the chameleon with her eyes softly closed",
  sad: "Ree the chameleon looking sad",
  surprised: "Ree the chameleon with a small gasp",
  thinking: "Ree the chameleon thinking, hand on chin",
  detective: "Ree the chameleon in a tiny detective hat with a magnifier",
  cinema: "Ree the chameleon holding a film reel",
  lovestory: "Ree the chameleon dreamy, holding flowers",
  mystery: "Ree the chameleon in deep teal and violet, alert",
  celebrating: "Ree the chameleon with her arms up, celebrating"
};

export default function Ree({
  mood,
  pose,
  variant = "pose",
  size = 120,
  decorative = true,
  alt,
  className = "",
  style
}) {
  const resolvedPose = pose ?? MOOD_TO_POSE[mood] ?? "default";
  // Board rule: below 48px, drop the shading and keep head, eyes, spiral.
  const kind = variant === "icon" || size < 48 ? "icon" : variant;
  const label = alt ?? (kind === "icon" ? "Ree" : POSE_ALT[resolvedPose]);
  const a11y = decorative && !alt ? { alt: "", "aria-hidden": true } : { alt: label };

  if (kind === "canonical") {
    return (
      <img
        src={canonicalUrl}
        width={size}
        height={Math.round(size * 0.958)}
        className={`ree ree--canonical ${className}`}
        style={style}
        draggable={false}
        {...a11y}
      />
    );
  }

  if (kind === "icon") {
    return (
      <img
        src={iconUrl}
        width={size}
        height={size}
        className={`ree ree--icon ${className}`}
        style={style}
        draggable={false}
        {...a11y}
      />
    );
  }

  return (
    <img
      src={POSES[resolvedPose] ?? POSES.default}
      width={size}
      height={size}
      className={`ree ree--pose ${className}`}
      style={style}
      draggable={false}
      loading="lazy"
      {...a11y}
    />
  );
}

/**
 * Ree speaking — the companion voice used for guidance and reassurance:
 * a small icon-Ree beside a soft bubble. Keep copy short and kind.
 */
export function ReeSays({ children, pose, className = "" }) {
  return (
    <div className={`ree-says ${className}`}>
      {pose ? <Ree pose={pose} size={52} className="ree-says-avatar" /> : <Ree variant="icon" size={40} className="ree-says-icon" />}
      <p className="ree-says-bubble">{children}</p>
    </div>
  );
}

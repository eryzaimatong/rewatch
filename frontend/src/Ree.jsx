import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion, useMotionValue, useReducedMotion, useSpring, useTransform } from "framer-motion";
import { POSES, canonicalUrl, iconUrl } from "./reeAssets";
import { MOODS, MOOD_REE_POSE, MOOD_REE_TONE } from "./moods";

/**
 * Ree — Re:Watch's companion chameleon (see the Figma "Mascot design system").
 *
 * Rules from the board, enforced here rather than left to call sites:
 *  - "A companion, not a decoration": Ree appears where she says something
 *    (onboarding, loading, empty, error, mood choice) — never as wallpaper.
 *  - Emotion lives in the eyes/posture. Poses are transparent cut-outs of the
 *    real studio shots, so Ree stands IN the page (no photo box) and can move:
 *    she floats, breathes, looks toward the cursor, hops when poked, changes
 *    pose with a squash-and-stretch pop, and dozes off when you go idle.
 *  - Below 48px, simplify: `size < 48` swaps to the flat icon version.
 *
 * Usage:
 *   <Ree mood="error" size={120} />      // semantic mood (matches old API)
 *   <Ree pose="detective" size={160} />  // any of the 12 expression poses
 *   <Ree variant="canonical" size={420}/>// transparent cut-out hero render
 *   <Ree variant="icon" size={40} />     // flat app-icon version
 *   <Ree moodKey="mysterious" />         // user's chosen mood: pose + colour shift
 *
 * Every asset is a true-alpha cut-out (no photo box, no tile), so Ree sits on
 * the page's own background. Gaze: the art is raster, so "eyes follow the
 * cursor" is done as a 3D head-turn of the whole figure (a few degrees of
 * yaw/pitch plus parallax) — a separate eye layer from design would let the
 * pupils move on their own later without touching this API.
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

// width / height of each cut-out, so layout never jumps while it loads.
const ASPECT = {
  default: 0.82, curious: 0.78, excited: 0.75, sleepy: 1.17, sad: 0.8, surprised: 0.64,
  thinking: 0.69, detective: 0.72, cinema: 0.94, lovestory: 0.74, mystery: 0.78, celebrating: 0.76
};

const POP = {
  initial: { opacity: 0, scale: 0.82, y: 10, scaleX: 0.9, scaleY: 1.12 },
  animate: { opacity: 1, scale: 1, y: 0, scaleX: 1, scaleY: 1, transition: { type: "spring", stiffness: 380, damping: 15 } },
  exit: { opacity: 0, scale: 0.94, transition: { duration: 0.12 } }
};

export default function Ree({
  mood,
  pose,
  variant = "pose",
  size = 120,
  decorative = true,
  alt,
  className = "",
  style,
  live,
  aura = true,
  hop = 0,
  moodKey
}) {
  const resolvedPose = pose ?? MOOD_REE_POSE[moodKey] ?? MOOD_TO_POSE[mood] ?? "default";
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
    <LiveRee
      pose={resolvedPose}
      size={size}
      className={className}
      style={style}
      live={live ?? size >= 110}
      aura={aura}
      hop={hop}
      moodKey={moodKey}
      a11y={a11y}
    />
  );
}

function LiveRee({ pose, size, className, style, live, aura, hop, moodKey, a11y }) {
  const reduce = useReducedMotion();
  const rootRef = useRef(null);
  const [dozing, setDozing] = useState(false);
  const [poked, setPoked] = useState(0);
  const lookX = useMotionValue(0);
  const lookY = useMotionValue(0);
  const x = useSpring(lookX, { stiffness: 90, damping: 14 });
  const y = useSpring(lookY, { stiffness: 90, damping: 14 });
  // Head-turn: yaw follows horizontal gaze, pitch follows vertical (inverted).
  const yaw = useTransform(x, (v) => v * 1.5);
  const pitch = useTransform(y, (v) => v * -1.4);
  const animated = live && !reduce;

  // Look toward the pointer; doze off after ~40s without any interaction.
  useEffect(() => {
    if (!animated) return undefined;
    let timer;
    const sleepSoon = () => {
      clearTimeout(timer);
      setDozing(false);
      timer = setTimeout(() => setDozing(true), 40000);
    };
    const onMove = (e) => {
      sleepSoon();
      const el = rootRef.current;
      if (!el) return;
      const r = el.getBoundingClientRect();
      const dx = (e.clientX - (r.left + r.width / 2)) / Math.max(window.innerWidth, 1);
      const dy = (e.clientY - (r.top + r.height / 2)) / Math.max(window.innerHeight, 1);
      lookX.set(Math.max(-1, Math.min(1, dx * 2)) * 10);
      lookY.set(Math.max(-1, Math.min(1, dy * 2)) * 6);
    };
    sleepSoon();
    window.addEventListener("pointermove", onMove, { passive: true });
    window.addEventListener("keydown", sleepSoon);
    return () => {
      clearTimeout(timer);
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("keydown", sleepSoon);
    };
  }, [animated, lookX, lookY]);

  const shown = dozing && pose === "default" ? "sleepy" : pose;
  const ratio = ASPECT[shown] ?? 0.8;
  const h = size;
  const w = Math.round(size * ratio);
  const hopKey = hop + poked;
  const tone = MOOD_REE_TONE[moodKey];
  const moodInfo = MOODS.find((m) => m.key === moodKey);
  const toneVars = tone
    ? { "--ree-hue": `${tone.hue}deg`, "--ree-sat": tone.sat, "--ree-lum": tone.lum, "--ree-tint": moodInfo?.tint }
    : undefined;

  return (
    <span
      ref={rootRef}
      className={`ree ree-live${tone ? " ree-toned" : ""} ${className}`}
      style={{ width: Math.round(size * 0.95), height: h, ...toneVars, ...style }}
      onClick={animated ? () => setPoked((n) => n + 1) : undefined}
    >
      {aura && <span className="ree-aura" aria-hidden="true" />}
      <motion.span
        className="ree-look"
        style={{ x: animated ? x : 0, y: animated ? y : 0, rotateY: animated ? yaw : 0, rotateX: animated ? pitch : 0, transformOrigin: "50% 80%" }}
      >
      <motion.span
        className="ree-body"
        style={{ transformOrigin: "50% 100%" }}
        animate={animated ? { y: [0, -size * 0.035, 0], rotate: [-0.8, 0.8, -0.8], scaleY: [1, 1.014, 1] } : undefined}
        transition={animated ? { duration: 5.2, repeat: Infinity, ease: "easeInOut" } : undefined}
      >
        <motion.span
          key={hopKey}
          className="ree-hop"
          initial={hopKey ? { y: 0, scaleY: 0.86, scaleX: 1.1 } : false}
          animate={hopKey ? { y: [0, -size * 0.12, 0], scaleY: [0.86, 1.08, 1], scaleX: [1.1, 0.95, 1] } : undefined}
          transition={{ duration: 0.55, times: [0, 0.45, 1], ease: "easeOut" }}
        >
          <AnimatePresence mode="wait" initial={false}>
            <motion.img
              key={shown}
              src={POSES[shown] ?? POSES.default}
              width={w}
              height={h}
              className="ree-img"
              draggable={false}
              {...(reduce ? {} : POP)}
              style={{ transformOrigin: "50% 100%" }}
              {...a11y}
            />
          </AnimatePresence>
        </motion.span>
      </motion.span>
      </motion.span>
      <span className="ree-shadow" aria-hidden="true" />
    </span>
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

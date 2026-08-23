/**
 * Small hand-drawn line-icon set, replacing the emoji this app used to lean
 * on for functional UI markers (streak flame, watched badge, show/hide
 * password, etc). Emoji render inconsistently across OS/browser (a different
 * glyph shape, weight, and color per platform) and read as "generic app"
 * rather than something considered — a stroke-based icon in `currentColor`
 * stays crisp and matches whatever color the surrounding UI already uses,
 * the same way a real icon font/library would.
 *
 * Every icon is a 24x24 viewBox, stroke="currentColor", fill="none" — drop
 * one in and set `color`/font-size on the wrapper, same as a text glyph.
 */

const base = {
  width: "1em",
  height: "1em",
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true
};

export function IconEye(props) {
  return (
    <svg {...base} {...props}>
      <path d="M1.5 12S5 5 12 5s10.5 7 10.5 7-3.5 7-10.5 7S1.5 12 1.5 12Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

export function IconEyeOff(props) {
  return (
    <svg {...base} {...props}>
      <path d="M3 3l18 18" />
      <path d="M10.6 5.1A10.7 10.7 0 0 1 12 5c7 0 10.5 7 10.5 7a13.4 13.4 0 0 1-3.1 4M6.6 6.6C3.4 8.6 1.5 12 1.5 12S5 19 12 19a10.7 10.7 0 0 0 4.4-.9" />
      <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
    </svg>
  );
}

export function IconFlame(props) {
  return (
    <svg {...base} {...props}>
      <path d="M12 2.5s-1.5 3-1.5 5.5c0 1 .5 1.8 1 2.5.4-1 .3-2 1-2.5.8 1.3 3 3 3 6a5.5 5.5 0 0 1-11 0c0-2.5 1.2-4 2-5.5.3 1 .8 1.5 1.5 1.5-.3-2.5.5-5.5 3-7.5Z" />
    </svg>
  );
}

export function IconShuffle(props) {
  return (
    <svg {...base} {...props}>
      <path d="M3 5h3.5c2 0 3 1 4 2.5l.5.8M3 19h3.5c2 0 3-1 4-2.5l3-4.5c1-1.5 2-2.5 4-2.5H21" />
      <path d="M18 3l3 2-3 2M18 17l3 2-3 2" />
    </svg>
  );
}

export function IconTrophy(props) {
  return (
    <svg {...base} {...props}>
      <path d="M7 4h10v4a5 5 0 0 1-10 0V4Z" />
      <path d="M7 5H4a3 3 0 0 0 3 4M17 5h3a3 3 0 0 1-3 4" />
      <path d="M12 13v3M9 20h6M9.5 20c0-2 .5-3 2.5-3s2.5 1 2.5 3" />
    </svg>
  );
}

export function IconLock(props) {
  return (
    <svg {...base} {...props}>
      <rect x="4.5" y="10.5" width="15" height="10" rx="2" />
      <path d="M7.5 10.5V7a4.5 4.5 0 0 1 9 0v3.5" />
    </svg>
  );
}

export function IconMusic(props) {
  return (
    <svg {...base} {...props}>
      <path d="M9 18V5l11-2v13" />
      <circle cx="6" cy="18" r="3" />
      <circle cx="17" cy="16" r="3" />
    </svg>
  );
}

export function IconWarning(props) {
  return (
    <svg {...base} {...props}>
      <path d="M12 3.5 2.5 20h19L12 3.5Z" />
      <path d="M12 10v4.5" />
      <circle cx="12" cy="17.5" r="0.75" fill="currentColor" stroke="none" />
    </svg>
  );
}

export function IconFilmCheck(props) {
  return (
    <svg {...base} {...props}>
      <rect x="2.5" y="4.5" width="19" height="15" rx="2" />
      <path d="M8 4.5v15M16 4.5v15M2.5 9.5H8M2.5 14.5H8M16 9.5h5.5M16 14.5h5.5" />
    </svg>
  );
}

export function IconSearch(props) {
  return (
    <svg {...base} {...props}>
      <circle cx="10.5" cy="10.5" r="7" />
      <path d="M20.5 20.5 15.8 15.8" />
    </svg>
  );
}

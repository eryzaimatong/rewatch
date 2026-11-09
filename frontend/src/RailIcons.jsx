/* Rail / nav icons — same 24px stroke style as Icons.jsx. */
const base = {
  width: "1em",
  height: "1em",
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true
};

export function IconHome(props) {
  return (
    <svg {...base} {...props}>
      <path d="M3 11.5 12 4l9 7.5" />
      <path d="M5.5 10v9.5h13V10" />
      <path d="M10 19.5v-5h4v5" />
    </svg>
  );
}

export function IconBookmark(props) {
  return (
    <svg {...base} {...props}>
      <path d="M6 3.5h12v17l-6-4.2-6 4.2v-17Z" />
    </svg>
  );
}

export function IconUsers(props) {
  return (
    <svg {...base} {...props}>
      <circle cx="9" cy="8.5" r="3.2" />
      <path d="M2.8 20c.4-3.4 3-5.4 6.2-5.4s5.8 2 6.2 5.4" />
      <path d="M16 5.6a3.2 3.2 0 0 1 0 5.8" />
      <path d="M17.5 14.9c2.1.5 3.4 2.2 3.7 5.1" />
    </svg>
  );
}

export function IconDna(props) {
  return (
    <svg {...base} {...props}>
      <path d="M7 3c0 6 10 6 10 12s-10 6-10 6" />
      <path d="M17 3c0 6-10 6-10 12s10 6 10 6" />
      <path d="M8.2 7h7.6M8.2 17h7.6M10 12h4" />
    </svg>
  );
}

export function IconPlay(props) {
  return (
    <svg {...base} fill="currentColor" stroke="none" {...props}>
      <path d="M8 5.2v13.6a.8.8 0 0 0 1.2.7l11-6.8a.8.8 0 0 0 0-1.4L9.2 4.5A.8.8 0 0 0 8 5.2Z" />
    </svg>
  );
}

export function IconArrowRight(props) {
  return (
    <svg {...base} strokeWidth={2.2} {...props}>
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}

export function IconChevron({ dir = "left", ...props }) {
  const d = { left: "M15 5l-7 7 7 7", right: "M9 5l7 7-7 7", down: "M5 9l7 7 7-7" }[dir];
  return (
    <svg {...base} strokeWidth={2.4} {...props}>
      <path d={d} />
    </svg>
  );
}

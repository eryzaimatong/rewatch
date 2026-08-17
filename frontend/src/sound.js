// Tiny, generated sound design — no audio files, no network requests, no
// licensing to worry about. Every sound here is a handful of oscillator
// tones with a short gain envelope, same "no new dependency for something
// this small" trade-off as RateLimiterService/TmdbCacheManager on the
// backend. Off by default: unexpected audio from a web app is a genuinely
// bad experience in the contexts people actually browse in (open-plan
// offices, late-night-in-bed, a shared room) — this is opt-in via Settings,
// not opt-out.
//
// Deliberately sparse: only the handful of moments that are genuinely worth
// marking (a rating landing, an achievement unlocking, the onboarding
// reveal) get a sound. Sound on every button press stops being a delight
// and starts being an annoyance within about ten clicks.

const KEY = "soundEnabled";

export function isSoundEnabled() {
  return localStorage.getItem(KEY) === "yes";
}

export function setSoundEnabled(enabled) {
  if (enabled) {
    localStorage.setItem(KEY, "yes");
  } else {
    localStorage.removeItem(KEY);
  }
}

let ctx = null;

function getCtx() {
  const AudioCtx = window.AudioContext || window.webkitAudioContext;
  if (!AudioCtx) {
    return null;
  }
  if (!ctx) {
    ctx = new AudioCtx();
  }
  // Browsers suspend a freshly-created (or backgrounded-tab) context until a
  // user gesture resumes it — every call site here is already inside a click
  // handler, so this resolves synchronously in practice.
  if (ctx.state === "suspended") {
    ctx.resume().catch(() => {});
  }
  return ctx;
}

function tone(audioCtx, freq, startTime, duration, peakGain, type) {
  const osc = audioCtx.createOscillator();
  const gain = audioCtx.createGain();
  osc.type = type;
  osc.frequency.value = freq;
  gain.gain.setValueAtTime(0, startTime);
  gain.gain.linearRampToValueAtTime(peakGain, startTime + 0.015);
  gain.gain.exponentialRampToValueAtTime(0.0001, startTime + duration);
  osc.connect(gain).connect(audioCtx.destination);
  osc.start(startTime);
  osc.stop(startTime + duration + 0.02);
}

function play(fn) {
  if (!isSoundEnabled()) {
    return;
  }
  const audioCtx = getCtx();
  if (!audioCtx) {
    return;
  }
  try {
    fn(audioCtx);
  } catch {
    // Audio is a nice-to-have layered on top of a real action (a rating
    // that already saved, an achievement that already unlocked) — a synth
    // failure here must never surface as a user-facing error.
  }
}

/** A small, soft confirmation — a rating saved, a title added to the watchlist. */
export function playConfirm() {
  play((audioCtx) => {
    const now = audioCtx.currentTime;
    tone(audioCtx, 740, now, 0.1, 0.08, "sine");
  });
}

/** The bigger "something real just happened" moment — achievement unlocked, TasteDNA reveal. */
export function playChime() {
  play((audioCtx) => {
    const now = audioCtx.currentTime;
    tone(audioCtx, 523.25, now, 0.2, 0.07, "sine"); // C5
    tone(audioCtx, 659.25, now + 0.1, 0.24, 0.07, "sine"); // E5
    tone(audioCtx, 783.99, now + 0.2, 0.38, 0.08, "sine"); // G5
  });
}

/** A gentle downward note — something didn't work (kept soft on purpose; this is feedback, not an alarm). */
export function playSoftError() {
  play((audioCtx) => {
    const now = audioCtx.currentTime;
    tone(audioCtx, 300, now, 0.16, 0.06, "sine");
  });
}

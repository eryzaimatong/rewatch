// Product analytics (PostHog) and crash reporting (Sentry), kept behind one
// tiny API so screens never import a vendor directly.
//
// Principles, each enforced by telemetry.test.js:
//  - Off unless configured: with no VITE_POSTHOG_KEY / VITE_SENTRY_DSN nothing
//    is downloaded and every call is a silent no-op.
//  - Lazy: the SDKs load after first paint via dynamic import, so they never
//    block the app. Events and errors raised before they load are queued.
//  - Private by construction: only harmless scalar properties are ever sent
//    (usernames, emails, tokens and ids are stripped), events are anonymous (no
//    person profiles, no autocapture, no session recording), and Do Not Track
//    or the in-app opt-out switches everything off.
//  - Never breaks the app: a blocked or failing SDK is swallowed.

const OPT_OUT_KEY = "telemetryOptOut";
const MAX_QUEUE = 50;

const defaultLoaders = {
  posthog: () => import("posthog-js"),
  sentry: () => import("@sentry/react")
};

let posthog = null;
let sentry = null;
let disabled = false;
let eventQueue = [];
let errorQueue = [];

/** Test helper: back to a clean, never-initialised state. */
export function __resetTelemetry() {
  posthog = null;
  sentry = null;
  disabled = false;
  eventQueue = [];
  errorQueue = [];
}

const SENSITIVE_KEY = /user|name|mail|token|pass|auth|secret|session|cookie|(^|_)id$|Id$/i;

/** Keep only plain string/number/boolean values under non-sensitive keys. */
export function scrubProps(props = {}) {
  const out = {};
  for (const [key, value] of Object.entries(props ?? {})) {
    if (SENSITIVE_KEY.test(key)) continue;
    if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") out[key] = value;
  }
  return out;
}

function doNotTrack() {
  try {
    const nav = typeof navigator !== "undefined" ? navigator : {};
    const w = typeof window !== "undefined" ? window : {};
    return nav.doNotTrack === "1" || w.doNotTrack === "1" || nav.msDoNotTrack === "1";
  } catch {
    return false;
  }
}

function optedOutInApp() {
  try {
    return localStorage.getItem(OPT_OUT_KEY) === "1";
  } catch {
    return false;
  }
}

function optedOut() {
  return doNotTrack() || optedOutInApp();
}

function stripUrl(url) {
  try {
    const u = new URL(url);
    return `${u.origin}${u.pathname}`;
  } catch {
    return url;
  }
}

/** Sentry beforeSend: drop the user block and query strings (they can carry tokens). */
function scrubEvent(event) {
  if (event.user) delete event.user;
  if (event.request?.url) event.request.url = stripUrl(event.request.url);
  if (event.request?.query_string) delete event.request.query_string;
  if (event.request?.cookies) delete event.request.cookies;
  return event;
}

function sendEvent(name, props) {
  try {
    posthog.capture(name, scrubProps(props));
  } catch {
    // analytics must never break the app
  }
}

function sendError(error, context) {
  try {
    sentry.withScope((scope) => {
      if (context) scope.setContext("boundary", scrubProps(context));
      sentry.captureException(error);
    });
  } catch {
    // crash reporting must never break the app
  }
}

/** Record a product event. Safe to call at any time, before or after init. */
export function track(name, props) {
  if (disabled || optedOut()) return;
  if (posthog) return sendEvent(name, props);
  if (eventQueue.length < MAX_QUEUE) eventQueue.push([name, props]);
}

/** Report a caught error (error boundaries call this). Safe at any time. */
export function captureError(error, context) {
  if (disabled || optedOut()) return;
  if (sentry) return sendError(error, context);
  if (errorQueue.length < MAX_QUEUE) errorQueue.push([error, context]);
}

/** Turn telemetry off (or back on) for this browser; also stops a running SDK. */
export function setTelemetryOptOut(optOut) {
  try {
    localStorage.setItem(OPT_OUT_KEY, optOut ? "1" : "0");
  } catch {
    // storage blocked: the in-memory state below still applies this session
  }
  if (optOut) {
    eventQueue = [];
    errorQueue = [];
    try {
      posthog?.opt_out_capturing?.();
    } catch {
      // ignore
    }
  }
}

/**
 * Start the SDKs that are configured. Call once at startup; it resolves after
 * the SDKs have loaded (or immediately when there is nothing to load).
 */
export async function initTelemetry({ env = import.meta.env, loaders = defaultLoaders } = {}) {
  const phKey = env.VITE_POSTHOG_KEY;
  const dsn = env.VITE_SENTRY_DSN;
  if (optedOut() || (!phKey && !dsn)) {
    disabled = true;
    eventQueue = [];
    errorQueue = [];
    return;
  }

  await Promise.allSettled([
    (async () => {
      if (!phKey) return;
      const mod = await loaders.posthog();
      const ph = mod.default ?? mod;
      ph.init(phKey, {
        api_host: env.VITE_POSTHOG_HOST || "https://us.i.posthog.com",
        autocapture: false,
        capture_pageview: "history_change",
        person_profiles: "never",
        disable_session_recording: true,
        persistence: "localStorage"
      });
      posthog = ph;
      const queued = eventQueue;
      eventQueue = [];
      queued.forEach(([name, props]) => sendEvent(name, props));
    })(),
    (async () => {
      if (!dsn) return;
      const s = await loaders.sentry();
      s.init({
        dsn,
        release: env.VITE_RELEASE || undefined,
        environment: env.MODE,
        sendDefaultPii: false,
        tracesSampleRate: 0,
        beforeSend: scrubEvent
      });
      sentry = s;
      const queued = errorQueue;
      errorQueue = [];
      queued.forEach(([error, context]) => sendError(error, context));
    })()
  ]);

  // A vendor that was configured but failed to load must not leave a queue
  // growing forever: whatever is still unsent has nowhere to go.
  if (!posthog) eventQueue = [];
  if (!sentry) errorQueue = [];
}

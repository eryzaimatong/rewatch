import { clearSession } from "./auth";

/**
 * Installed once at app startup (see main.jsx) to patch window.fetch, rather
 * than wrapping every authenticated call site individually — this app has
 * ~50 fetch() calls to its own API spread across api.js and a dozen
 * components that fetch inline, and every one of them currently treats a
 * failed response the same way (`if (!res.ok) return []/null`). That means a
 * 401 — a session that died because the 7-day JWT expired, or because the
 * account's tokenVersion was bumped by a password change elsewhere — looks
 * identical to "no data yet" everywhere in the app: feeds render empty,
 * profiles render blank, and nothing tells the user they need to log back
 * in. Patching fetch once here catches every request, present and future,
 * without relying on every call site remembering to check for it.
 *
 * Scoped to requests that actually attached an Authorization header (this
 * app's own authHeaders()), so an unauthenticated call — login, register, a
 * logged-out public feed request — never triggers a session reset over what
 * would legitimately be a 401 for a different reason.
 */
// This app's Render free-tier backend spins down after inactivity. Real
// cold wake-ups measured this session (from actual boot logs, not a vendor
// estimate): 135-146s typical, one single-service outlier at 166.6s, and
// 167.40s for the worst case actually tested — Render idle-sleep AND Neon
// (the Postgres provider) suspended at the same time, measured with a
// clean 16-minute silent window and no interference. The previous 180s
// budget left only ~13s of margin over that worst-observed case, on a
// small handful of samples with ~30s of spread already seen between the
// typical and outlier figures — not enough margin to be confident another
// sample wouldn't exceed it.
//
// The tradeoff: raising this budget costs nothing to a legitimately slow
// but succeeding wake-up (a real visitor just waits — and WakingState.jsx
// fills the entire wait with staged, evolving content, not a stall) but
// delays how long a genuinely dead server takes to surface a real error.
// Lowering it (or leaving it thin) risks aborting a request that would
// have succeeded a few seconds later, which is worse than either
// alternative: the visitor lands on a false "something's wrong, retry"
// state, and a retry restarts the whole wait from zero — turning one
// ~167s wait into a ~167s wait plus a second ~150s+ one. That asymmetry
// (a false abort costs a full extra wake cycle; a longer budget on a truly
// dead server costs seconds of already-filled waiting) is why this leans
// toward more margin. 200s = worst observed (167.40s) + roughly one more
// typical-to-outlier spread (~30s), giving ~33s of margin over the worst
// case actually measured — not an arbitrary round-up, and not a reflexive
// "just make it bigger": if a future measurement lands meaningfully closer
// to 200s, this number needs revisiting again, not left untouched forever.
const FETCH_TIMEOUT_MS = 200_000;

export function installSessionGuard() {
  if (typeof window === "undefined" || window.fetch.__rewatchSessionGuarded) {
    return;
  }

  const nativeFetch = window.fetch.bind(window);
  let sessionExpiredHandled = false;

  function guardedFetch(input, init) {
    // No call site in this app passes its own `signal` today (grepped —
    // zero uses of AbortController/signal outside this file), so every
    // request gets this budget. If one starts passing its own signal in
    // the future, respect it instead of overriding.
    const hasOwnSignal = !!(init && init.signal);
    const controller = hasOwnSignal ? null : new AbortController();
    const timeoutId = controller
      ? setTimeout(() => controller.abort(new DOMException("Timed out waiting for a response", "TimeoutError")), FETCH_TIMEOUT_MS)
      : null;

    const requestInit = controller ? { ...init, signal: controller.signal } : init;

    return nativeFetch(input, requestInit).then(
      (res) => {
        if (timeoutId) clearTimeout(timeoutId);
        const hadAuthHeader = !!(init && init.headers && init.headers.Authorization);
        if (res.status === 401 && hadAuthHeader && !sessionExpiredHandled) {
          sessionExpiredHandled = true;
          clearSession();
          window.location.href = "/";
        }
        return res;
      },
      (err) => {
        if (timeoutId) clearTimeout(timeoutId);
        throw err;
      }
    );
  }

  guardedFetch.__rewatchSessionGuarded = true;
  window.fetch = guardedFetch;
}

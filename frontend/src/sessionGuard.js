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
// This app's Render free-tier backend spins down after inactivity, and a
// real cold wake-up was measured this session (from actual boot logs, not
// a vendor estimate) at 135-144 seconds for the app to finish starting —
// on top of whatever platform-level delay happens before that process
// even starts receiving traffic. A budget picked against the commonly
// quoted "~40s cold start" figure would abort every request made during a
// completely legitimate wake-up, which is worse than no timeout at all:
// it turns "the server is slow right now" into "the server is broken" in
// the UI. 180s gives real margin (25-45s) above both measured boots while
// still bounding a genuinely dead request (server unreachable, hung
// connection) to a finite wait instead of forever.
const FETCH_TIMEOUT_MS = 180_000;

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

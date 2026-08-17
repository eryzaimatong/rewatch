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
export function installSessionGuard() {
  if (typeof window === "undefined" || window.fetch.__rewatchSessionGuarded) {
    return;
  }

  const nativeFetch = window.fetch.bind(window);
  let sessionExpiredHandled = false;

  function guardedFetch(input, init) {
    return nativeFetch(input, init).then((res) => {
      const hadAuthHeader = !!(init && init.headers && init.headers.Authorization);
      if (res.status === 401 && hadAuthHeader && !sessionExpiredHandled) {
        sessionExpiredHandled = true;
        clearSession();
        window.location.href = "/";
      }
      return res;
    });
  }

  guardedFetch.__rewatchSessionGuarded = true;
  window.fetch = guardedFetch;
}

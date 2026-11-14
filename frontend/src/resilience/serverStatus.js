import { useSyncExternalStore } from "react";
import { BASE } from "../api";

/**
 * Invisible wake-up ping for the sleeping backend (Render) and its database
 * (Neon/Supabase free tier). It is a tiny external store so any component can
 * read "is the server awake yet?" without a provider, and so nothing ever
 * waits on it: callers fire `warmUp()` and carry on.
 *
 * GET /api/health opens a real DB connection (see HealthController), so a
 * 200 means the app AND the database are up, and a 503 means the app is up
 * but the database is still stretching. Both are "waking", not "down".
 *
 * phase: "idle" -> "waking" -> "awake"   (or "down" after the retry budget)
 */
const ATTEMPT_TIMEOUT_MS = 12_000;
const BACKOFF_MS = [0, 2_000, 4_000, 7_000, 10_000, 15_000];
const MAX_WAIT_MS = 4 * 60_000; // cold boots measured at ~135-170s, see sessionGuard.js

let state = { phase: "idle", attempts: 0, startedAt: 0 };
let inflight = null;
const listeners = new Set();

function set(next) {
  state = { ...state, ...next };
  listeners.forEach((l) => l());
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function ping() {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), ATTEMPT_TIMEOUT_MS);
  try {
    // Own signal on purpose: sessionGuard's 200s budget must not apply here.
    const res = await fetch(`${BASE}/api/health`, { signal: ctrl.signal, cache: "no-store" });
    return res.ok;
  } catch {
    return false;
  } finally {
    clearTimeout(t);
  }
}

/** Idempotent and non-blocking: safe to call from main.jsx, Login, anywhere. */
export function warmUp() {
  if (inflight) return inflight;
  if (state.phase === "awake") return Promise.resolve(true);
  const startedAt = Date.now();
  set({ phase: "waking", attempts: 0, startedAt });
  inflight = (async () => {
    for (let i = 0; Date.now() - startedAt < MAX_WAIT_MS; i++) {
      await sleep(BACKOFF_MS[Math.min(i, BACKOFF_MS.length - 1)]);
      set({ attempts: i + 1 });
      if (await ping()) {
        set({ phase: "awake" });
        return true;
      }
    }
    set({ phase: "down" });
    return false;
  })().finally(() => {
    inflight = null;
  });
  return inflight;
}

/** A real request just succeeded: the server is evidently awake. */
export function markAwake() {
  if (state.phase !== "awake") set({ phase: "awake" });
}

export function getServerStatus() {
  return state;
}

function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useServerStatus() {
  return useSyncExternalStore(subscribe, getServerStatus, getServerStatus);
}

/** Test helper. */
export function __resetServerStatus() {
  state = { phase: "idle", attempts: 0, startedAt: 0 };
  inflight = null;
}

import { useCallback, useEffect, useReducer, useRef, useState } from "react";
import { BASE } from "../api";
import { authHeaders } from "../auth";
import { readCache, writeCache } from "./cache";
import { markAwake, useServerStatus } from "./serverStatus";

/** Throws on network failure AND on non-2xx, so the hook sees one failure path. */
export async function fetchJson(path, { signal, headers, ...init } = {}) {
  const res = await fetch(path.startsWith("http") ? path : `${BASE}${path}`, {
    ...init,
    signal,
    headers: { ...authHeaders(), ...headers }
  });
  if (!res.ok) throw Object.assign(new Error(`HTTP ${res.status}`), { status: res.status });
  return res.json();
}

const SOURCE = { NETWORK: "network", CACHE: "cache", FALLBACK: "fallback" };

function init(key, ttlMs) {
  const hit = key ? readCache(key, ttlMs) : null;
  return {
    key,
    data: hit ? hit.value : undefined,
    source: hit ? SOURCE.CACHE : null,
    savedAt: hit ? hit.savedAt : null,
    inflight: false,
    error: null
  };
}

function reducer(s, a) {
  if (a.type === "reset") return a.state;
  if (a.key !== s.key) return s; // a result for a key we've since left
  switch (a.type) {
    case "start":
      return { ...s, inflight: true, error: null };
    case "network":
      return { ...s, data: a.data, source: SOURCE.NETWORK, savedAt: Date.now(), inflight: false, error: null };
    case "fallback":
      // Only fills a gap; never overwrites real or cached data.
      return s.data === undefined && a.fallback !== undefined
        ? { ...s, data: a.fallback, source: SOURCE.FALLBACK }
        : s;
    case "fail": {
      const next = { ...s, inflight: false, error: a.error };
      return s.data === undefined && a.fallback !== undefined
        ? { ...next, data: a.fallback, source: SOURCE.FALLBACK }
        : next;
    }
    default:
      return s;
  }
}

/**
 * Stale-while-revalidate with a guaranteed way out. The ladder:
 *
 *   1. cached copy from last visit  -> shown instantly (source "cache")
 *   2. nothing cached               -> skeleton for `softTimeoutMs`
 *   3. still nothing at soft limit  -> `fallback` is shown (source "fallback")
 *   4. the REAL request keeps going (cold boots take ~150s) and hot-swaps in
 *      the moment it lands; it is never aborted early just to show a fallback
 *   5. the request fails outright   -> keep what we have, expose `retry`, and
 *      refetch automatically once the wake-up ping reports the server awake
 *
 * `status` is only ever "loading" (skeleton), "ready" (data of any source) or
 * "error" (failed with literally nothing to show: no cache, no fallback).
 *
 * @param {object} o
 * @param {string|null} o.key       cache identity; include the user id. null disables.
 * @param {(signal: AbortSignal) => Promise<T>} o.fetcher
 * @param {T|undefined} [o.fallback] shown when the network can't deliver in time
 * @param {(d: T) => boolean} [o.validate] reject malformed payloads (-> failure path)
 */
export function useResilientQuery({
  key,
  fetcher,
  fallback,
  validate,
  softTimeoutMs = 6_000,
  hardTimeoutMs = 200_000,
  ttlMs = 7 * 24 * 60 * 60 * 1000,
  enabled = true
}) {
  const [attempt, setAttempt] = useState(0);
  const [stored, dispatch] = useReducer(reducer, undefined, () => init(key, ttlMs));
  // Re-derive on key change during render (no effect, no extra paint).
  let view = stored;
  if (stored.key !== key) {
    view = init(key, ttlMs);
    dispatch({ type: "reset", state: view });
  }
  const server = useServerStatus();
  const awake = server.phase === "awake";

  // Latest props without re-triggering the fetch effect.
  const live = useRef({});
  const sourceRef = useRef(null);
  const attemptRef = useRef(0);
  useEffect(() => {
    live.current = { fetcher, fallback, validate };
    sourceRef.current = view.source;
  });

  useEffect(() => {
    if (!enabled || !key) return undefined;
    // A manual retry always refetches. Anything else that re-runs this effect
    // (the server reporting "awake", a timeout change) only matters while we
    // are still showing cache/fallback: real data on screen means we're done.
    const isRetry = attempt !== attemptRef.current;
    attemptRef.current = attempt;
    if (sourceRef.current === SOURCE.NETWORK && !isRetry) return undefined;

    const ctrl = new AbortController();
    dispatch({ type: "start", key });
    const soft = setTimeout(() => dispatch({ type: "fallback", key, fallback: live.current.fallback }), softTimeoutMs);
    const hard = setTimeout(() => ctrl.abort(new DOMException("Timed out", "TimeoutError")), hardTimeoutMs);

    live.current
      .fetcher(ctrl.signal)
      .then((data) => {
        if (live.current.validate && !live.current.validate(data)) throw new Error("Malformed response");
        if (ctrl.signal.aborted) return;
        writeCache(key, data);
        markAwake();
        dispatch({ type: "network", key, data });
      })
      .catch((error) => {
        // Cleanup-aborts (unmount / key change) are not failures.
        if (ctrl.signal.aborted && ctrl.signal.reason?.name !== "TimeoutError") return;
        dispatch({ type: "fail", key, error, fallback: live.current.fallback });
      })
      .finally(() => {
        clearTimeout(soft);
        clearTimeout(hard);
      });

    return () => {
      clearTimeout(soft);
      clearTimeout(hard);
      ctrl.abort(new DOMException("Superseded", "AbortError"));
    };
    // `awake` flipping true re-runs this once: auto-recovery after a wake-up.
  }, [key, enabled, attempt, awake, softTimeoutMs, hardTimeoutMs]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);

  const hasData = view.data !== undefined;
  return {
    data: view.data,
    status: hasData ? "ready" : view.error ? "error" : "loading",
    source: view.source, // "network" | "cache" | "fallback" | null
    isFallback: view.source === SOURCE.FALLBACK,
    isStale: hasData && view.source !== SOURCE.NETWORK,
    isRevalidating: view.inflight && hasData,
    savedAt: view.savedAt,
    error: view.error,
    retry
  };
}

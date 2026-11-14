/**
 * Versioned, TTL-aware localStorage cache. Every access is wrapped: storage
 * can throw (private windows, blocked site data, quota) and the app must work
 * identically without it. Bump VERSION to invalidate every stored shape.
 */
const VERSION = 1;
const PREFIX = `rewatch:cache:v${VERSION}:`;

export function readCache(key, maxAgeMs = Infinity) {
  try {
    const raw = localStorage.getItem(PREFIX + key);
    if (!raw) return null;
    const { v, t } = JSON.parse(raw);
    if (v === undefined || typeof t !== "number") return null;
    if (Date.now() - t > maxAgeMs) return null;
    return { value: v, savedAt: t };
  } catch {
    return null;
  }
}

export function writeCache(key, value) {
  try {
    localStorage.setItem(PREFIX + key, JSON.stringify({ v: value, t: Date.now() }));
  } catch {
    // Quota or blocked storage: caching is a bonus, never a requirement.
  }
}

export function clearCache(key) {
  try {
    localStorage.removeItem(PREFIX + key);
  } catch {
    // ignore
  }
}

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { gettitles, rateMovie, login, getApiMeta, API_META } from "./api";

// The bug this exists to prevent: "loaded, empty" and "failed to load" were
// visually identical to every caller, because every fallback was the same
// empty array/null regardless of WHY the request failed. This locks in
// that (a) the old return value on every path is unchanged for every
// function except login/register (see that describe block for why those
// two are the deliberate exception) — no other caller anywhere in the app
// breaks from this pass — and (b) the hidden API_META channel actually
// distinguishes success / HTTP error / network error, which is the whole
// point of doing this.
describe("api.js — three-state result envelope", () => {
  let originalFetch;

  beforeEach(() => {
    originalFetch = window.fetch;
  });

  afterEach(() => {
    window.fetch = originalFetch;
  });

  describe("gettitles (array-returning function)", () => {
    it("returns the parsed array and ok:true meta on success", async () => {
      window.fetch = vi.fn(() =>
        Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve([{ id: 1 }]) })
      );

      const result = await gettitles();

      expect(result).toEqual([{ id: 1 }]);
      expect(getApiMeta(result)).toEqual({ ok: true, status: 200 });
    });

    it("returns [] on an HTTP error, distinguishable from a network failure via meta", async () => {
      window.fetch = vi.fn(() => Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve(null) }));

      const result = await gettitles();

      expect(result).toEqual([]);
      expect(getApiMeta(result)).toEqual({ ok: false, status: 500, type: "http" });
    });

    it("returns [] on a network-layer failure (fetch rejects) without throwing, distinguishable from an HTTP error", async () => {
      window.fetch = vi.fn(() => Promise.reject(new Error("Failed to fetch")));

      const result = await gettitles();

      expect(result).toEqual([]);
      expect(getApiMeta(result)).toEqual({ ok: false, type: "network", message: "Failed to fetch", name: "Error" });
    });

    it("never throws on a rejected fetch — the exact bug that used to strand callers on their loading skeleton", async () => {
      window.fetch = vi.fn(() => Promise.reject(new Error("network down")));

      await expect(gettitles()).resolves.not.toThrow();
    });

    it("the meta tag is invisible to every existing consumer of the array", async () => {
      window.fetch = vi.fn(() => Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve(null) }));

      const result = await gettitles();

      expect(Object.keys(result)).toEqual([]);
      expect(JSON.stringify(result)).toBe("[]");
      expect(result.length).toBe(0);
      expect([...result]).toEqual([]);
    });
  });

  describe("rateMovie ({ok, data}-returning function)", () => {
    it("preserves the exact {ok, data} shape on success", async () => {
      window.fetch = vi.fn(() =>
        Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ id: 5 }) })
      );

      const result = await rateMovie({ titleId: 1, score: 8 });

      expect(result).toEqual({ ok: true, data: { id: 5 } });
      expect(getApiMeta(result)).toEqual({ ok: true, status: 200 });
    });

    it("preserves {ok: false, data} on an HTTP error instead of throwing", async () => {
      window.fetch = vi.fn(() =>
        Promise.resolve({ ok: false, status: 400, json: () => Promise.resolve({ message: "bad score" }) })
      );

      const result = await rateMovie({ titleId: 1, score: 99 });

      expect(result).toEqual({ ok: false, data: { message: "bad score" } });
      expect(getApiMeta(result)).toEqual({ ok: false, status: 400, type: "http" });
    });

    it("returns {ok: false, data: null} on a network failure rather than an uncaught rejection", async () => {
      window.fetch = vi.fn(() => Promise.reject(new Error("offline")));

      const result = await rateMovie({ titleId: 1, score: 8 });

      expect(result).toEqual({ ok: false, data: null });
      expect(getApiMeta(result)).toEqual({ ok: false, type: "network", message: "offline", name: "Error" });
    });
  });

  describe("login (always-parse-regardless-of-status function)", () => {
    it("still surfaces the backend's error message body on a 401, unchanged from before", async () => {
      window.fetch = vi.fn(() =>
        Promise.resolve({ ok: false, status: 401, json: () => Promise.resolve({ message: "Invalid username/email or password" }) })
      );

      const result = await login("someone", "wrong");

      expect(result).toEqual({ message: "Invalid username/email or password" });
      expect(getApiMeta(result)).toEqual({ ok: false, status: 401, type: "http" });
    });

    // Deliberately {} rather than null (the only exception to this file's
    // usual "return value never changes" rule — see the describe block's
    // own comment above): Login.jsx needs getApiMeta() to actually work on
    // a network failure, to tell a cold/timed-out backend (WakingState)
    // apart from real invalid credentials, and withMeta() cannot attach to
    // null. login() has exactly one caller (Login.jsx), updated alongside
    // this change.
    it("returns {} (not null) on a network failure, with working meta, so Login.jsx can tell it apart from invalid credentials", async () => {
      window.fetch = vi.fn(() => Promise.reject(new Error("DNS failure")));

      const result = await login("someone", "pw");

      expect(result).toEqual({});
      expect(getApiMeta(result)).toEqual({ ok: false, type: "network", message: "DNS failure", name: "Error" });
    });
  });

  it("API_META is a real exported Symbol, not a guessable string key", () => {
    expect(typeof API_META).toBe("symbol");
  });
});

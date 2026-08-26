import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { installSessionGuard } from "./sessionGuard";

// The bug this exists to prevent: a dead session (expired JWT, or
// tokenVersion bumped by a password change elsewhere) looks identical to
// "no data yet" everywhere in the app, because every call site already
// treats a failed response the same way. That makes this patch's own
// correctness — WHICH responses it reacts to, and how many times — the
// actual thing worth locking in, not just "it exists."
describe("installSessionGuard", () => {
  let originalFetch;
  let originalLocation;

  beforeEach(() => {
    originalFetch = window.fetch;
    // window.location.href assignment triggers real jsdom/happy-dom
        // navigation attempts; stub it out so the test can observe the
    // intended redirect without actually navigating.
    originalLocation = window.location;
    delete window.location;
    window.location = { href: "" };
    localStorage.setItem("token", "some-token");
    localStorage.setItem("userId", "1");
  });

  afterEach(() => {
    window.fetch = originalFetch;
    window.location = originalLocation;
    localStorage.clear();
  });

  function mockFetchReturning(status) {
    window.fetch = vi.fn(() => Promise.resolve({ status }));
  }

  it("clears the session and redirects on a 401 to an authenticated request", async () => {
    mockFetchReturning(401);
    installSessionGuard();

    await window.fetch("/api/watchlist/1", { headers: { Authorization: "Bearer x" } });

    expect(localStorage.getItem("token")).toBeNull();
    expect(window.location.href).toBe("/");
  });

  it("does not treat a 401 on an unauthenticated request (login/register) as a dead session", async () => {
    mockFetchReturning(401);
    installSessionGuard();

    await window.fetch("/api/auth/login", { headers: { "Content-Type": "application/json" } });

    expect(localStorage.getItem("token")).toBe("some-token");
    expect(window.location.href).toBe("");
  });

  it("leaves a successful authenticated request alone", async () => {
    mockFetchReturning(200);
    installSessionGuard();

    await window.fetch("/api/watchlist/1", { headers: { Authorization: "Bearer x" } });

    expect(localStorage.getItem("token")).toBe("some-token");
  });

  it("only reacts to the first 401 — a burst of already-in-flight requests shouldn't redirect repeatedly", async () => {
    mockFetchReturning(401);
    installSessionGuard();

    let hrefAssignments = 0;
    Object.defineProperty(window.location, "href", {
      set: () => { hrefAssignments++; },
      get: () => "/"
    });

    const opts = { headers: { Authorization: "Bearer x" } };
    await Promise.all([
      window.fetch("/api/a", opts),
      window.fetch("/api/b", opts),
      window.fetch("/api/c", opts)
    ]);

    expect(hrefAssignments).toBe(1);
  });

  it("installing twice does not double-patch fetch", () => {
    mockFetchReturning(200);
    installSessionGuard();
    const patchedOnce = window.fetch;
    installSessionGuard();
    expect(window.fetch).toBe(patchedOnce);
  });

  // A timeout that fires during a legitimate Render cold-start wake-up
  // (measured this session at 135-144s) is worse than no timeout at all —
  // it would show "failed" for a server that was actually just slow. These
  // lock in that the 180s budget doesn't misfire on a slow-but-legitimate
  // response, and that it does eventually abort a truly hung request.
  describe("request timeout", () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it("does not abort a request that resolves well within the budget (simulating a slow cold-start wake-up)", async () => {
      let capturedSignal;
      window.fetch = vi.fn((input, init) => {
        capturedSignal = init?.signal;
        return new Promise((resolve) => {
          setTimeout(() => resolve({ status: 200 }), 150_000); // 150s < 180s budget
        });
      });
      installSessionGuard();

      const promise = window.fetch("/api/titles");
      await vi.advanceTimersByTimeAsync(150_000);
      const res = await promise;

      expect(res.status).toBe(200);
      expect(capturedSignal.aborted).toBe(false);
    });

    it("aborts a request that never resolves once the 180s budget elapses", async () => {
      let capturedSignal;
      window.fetch = vi.fn((input, init) => {
        capturedSignal = init?.signal;
        return new Promise(() => {}); // never resolves — a truly hung request
      });
      installSessionGuard();

      window.fetch("/api/titles").catch(() => {});
      await vi.advanceTimersByTimeAsync(180_000);

      expect(capturedSignal.aborted).toBe(true);
    });

    it("respects a caller-supplied signal instead of overriding it", async () => {
      const ownController = new AbortController();
      let capturedSignal;
      window.fetch = vi.fn((input, init) => {
        capturedSignal = init?.signal;
        return Promise.resolve({ status: 200 });
      });
      installSessionGuard();

      await window.fetch("/api/titles", { signal: ownController.signal });

      expect(capturedSignal).toBe(ownController.signal);
    });
  });
});

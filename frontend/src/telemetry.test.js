import { beforeEach, describe, expect, it, vi } from "vitest";
import { __resetTelemetry, captureError, initTelemetry, scrubProps, setTelemetryOptOut, track } from "./telemetry";

/** Fake vendor SDKs so we test OUR behaviour (queueing, privacy, opt-out), not theirs. */
function fakeVendors() {
  const posthog = { init: vi.fn(), capture: vi.fn(), opt_out_capturing: vi.fn() };
  const sentry = { init: vi.fn(), captureException: vi.fn(), withScope: vi.fn((fn) => fn({ setContext: vi.fn(), setTag: vi.fn() })) };
  const loaders = {
    posthog: vi.fn(async () => ({ default: posthog })),
    sentry: vi.fn(async () => sentry)
  };
  return { posthog, sentry, loaders };
}

const ENV = { VITE_POSTHOG_KEY: "phc_test", VITE_SENTRY_DSN: "https://k@example.ingest.sentry.io/1" };

beforeEach(() => {
  __resetTelemetry();
  localStorage.clear();
  vi.unstubAllGlobals();
});

describe("telemetry", () => {
  it("is a silent no-op when no keys are configured: nothing is loaded, nothing throws", async () => {
    const { loaders } = fakeVendors();
    await initTelemetry({ env: {}, loaders });
    expect(() => track("card_exported", { kind: "tastedna" })).not.toThrow();
    expect(() => captureError(new Error("boom"))).not.toThrow();
    expect(loaders.posthog).not.toHaveBeenCalled();
    expect(loaders.sentry).not.toHaveBeenCalled();
  });

  it("delivers events tracked BEFORE the SDK finished loading, once, in order", async () => {
    const { posthog, loaders } = fakeVendors();
    track("compare_link_opened", { src: "card" });
    track("card_exported", { kind: "tastedna", method: "downloaded" });
    await initTelemetry({ env: ENV, loaders });
    expect(posthog.capture.mock.calls.map((c) => c[0])).toEqual(["compare_link_opened", "card_exported"]);
    track("mood_picked", { mood: "calm" });
    expect(posthog.capture).toHaveBeenCalledTimes(3);
  });

  it("never sends personal data: usernames, emails, tokens and ids are stripped from event properties", async () => {
    const { posthog, loaders } = fakeVendors();
    await initTelemetry({ env: ENV, loaders });
    track("card_exported", { kind: "tastedna", username: "mara", email: "m@x.io", token: "t", userId: 7, nested: { password: "p" } });
    const sent = posthog.capture.mock.calls[0][1];
    expect(sent).toEqual({ kind: "tastedna" });
  });

  it("scrubProps keeps harmless scalars and drops everything sensitive", () => {
    expect(scrubProps({ mood: "calm", count: 3, ok: true, Username: "x", authorization: "y", object: {} })).toEqual({
      mood: "calm",
      count: 3,
      ok: true
    });
  });

  it("honours Do Not Track: loads nothing and drops events", async () => {
    vi.stubGlobal("navigator", { ...navigator, doNotTrack: "1" });
    const { posthog, loaders } = fakeVendors();
    await initTelemetry({ env: ENV, loaders });
    track("mood_picked", { mood: "calm" });
    expect(loaders.posthog).not.toHaveBeenCalled();
    expect(posthog.capture).not.toHaveBeenCalled();
  });

  it("honours the in-app opt-out and stops an already-running SDK", async () => {
    const { posthog, loaders } = fakeVendors();
    await initTelemetry({ env: ENV, loaders });
    setTelemetryOptOut(true);
    track("mood_picked", { mood: "calm" });
    expect(posthog.capture).not.toHaveBeenCalled();
    expect(posthog.opt_out_capturing).toHaveBeenCalled();
  });

  it("initialises PostHog anonymously: no autocapture, no person profiles, no recordings", async () => {
    const { posthog, loaders } = fakeVendors();
    await initTelemetry({ env: ENV, loaders });
    const opts = posthog.init.mock.calls[0][1];
    expect(opts.autocapture).toBe(false);
    expect(opts.person_profiles).toBe("never");
    expect(opts.disable_session_recording).toBe(true);
  });

  it("initialises Sentry without default PII and without tracing, tagged with the release", async () => {
    const { sentry, loaders } = fakeVendors();
    await initTelemetry({ env: { ...ENV, VITE_RELEASE: "abc123" }, loaders });
    const opts = sentry.init.mock.calls[0][0];
    expect(opts.dsn).toBe(ENV.VITE_SENTRY_DSN);
    expect(opts.sendDefaultPii).toBe(false);
    expect(opts.tracesSampleRate).toBe(0);
    expect(opts.release).toBe("abc123");
  });

  it("forwards a caught error to Sentry with its boundary context, including errors captured before load", async () => {
    const { sentry, loaders } = fakeVendors();
    const err = new Error("render blew up");
    captureError(err, { area: "Community" });
    await initTelemetry({ env: ENV, loaders });
    expect(sentry.captureException).toHaveBeenCalledWith(err);
    expect(sentry.withScope).toHaveBeenCalled();
  });

  it("a vendor that fails to load never breaks the app", async () => {
    const loaders = { posthog: vi.fn(async () => { throw new Error("blocked by adblock"); }), sentry: vi.fn(async () => { throw new Error("blocked"); }) };
    await expect(initTelemetry({ env: ENV, loaders })).resolves.toBeUndefined();
    expect(() => track("mood_picked", { mood: "calm" })).not.toThrow();
  });
});

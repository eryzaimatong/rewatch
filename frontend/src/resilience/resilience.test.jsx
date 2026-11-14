import { describe, it, expect, afterEach, beforeEach, vi } from "vitest";
import { createRoot } from "react-dom/client";
import { act, useEffect } from "react";
import { useResilientQuery } from "./useResilientQuery";
import { warmUp, getServerStatus, __resetServerStatus } from "./serverStatus";
import { readCache, writeCache } from "./cache";

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

let roots = [];
const box = { current: undefined };
function Probe({ opts }) {
  const result = useResilientQuery(opts);
  useEffect(() => { box.current = result; });
  return null;
}
function mount(opts) {
  const el = document.createElement("div");
  const root = createRoot(el);
  act(() => root.render(<Probe opts={opts} />));
  roots.push(root);
}
const wait = (ms) => act(async () => { await new Promise((r) => setTimeout(r, ms)); });
function deferred() {
  let resolve, reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

beforeEach(() => { localStorage.clear(); __resetServerStatus(); });
afterEach(() => { roots.forEach((r) => act(() => r.unmount())); roots = []; vi.useRealTimers(); vi.restoreAllMocks(); });

describe("useResilientQuery", () => {
  it("shows the cached copy instantly, then swaps in fresh network data", async () => {
    writeCache("k", { v: "cached" });
    const d = deferred();
    mount({ key: "k", fetcher: () => d.promise, softTimeoutMs: 1000 });
    expect(box.current.status).toBe("ready");
    expect(box.current.source).toBe("cache");
    expect(box.current.data).toEqual({ v: "cached" });
    expect(box.current.isRevalidating).toBe(true);
    await act(async () => d.resolve({ v: "fresh" }));
    expect(box.current.source).toBe("network");
    expect(box.current.data).toEqual({ v: "fresh" });
    expect(readCache("k").value).toEqual({ v: "fresh" });
  });

  it("falls back at the soft timeout WITHOUT abandoning the request, then hot-swaps real data", async () => {
    const d = deferred();
    mount({ key: "slow", fetcher: () => d.promise, fallback: { v: "mock" }, softTimeoutMs: 20 });
    expect(box.current.status).toBe("loading");
    await wait(40);
    expect(box.current.status).toBe("ready");
    expect(box.current.isFallback).toBe(true);
    expect(box.current.data).toEqual({ v: "mock" });
    await act(async () => d.resolve({ v: "real" }));
    expect(box.current.isFallback).toBe(false);
    expect(box.current.data).toEqual({ v: "real" });
  });

  it("uses the fallback immediately when the request fails outright", async () => {
    mount({ key: "boom", fetcher: () => Promise.reject(new Error("net")), fallback: { v: "mock" }, softTimeoutMs: 5000 });
    await wait(0);
    expect(box.current.isFallback).toBe(true);
    expect(box.current.error).toBeTruthy();
  });

  it("treats a malformed payload as a failure instead of rendering blank cards", async () => {
    mount({ key: "bad", fetcher: async () => ({ nope: true }), validate: (d) => Array.isArray(d.items), fallback: { items: [1] }, softTimeoutMs: 5000 });
    await wait(0);
    expect(box.current.isFallback).toBe(true);
    expect(box.current.data).toEqual({ items: [1] });
  });

  it("with no cache and no fallback, ends in 'error' (never a hang) and retry recovers", async () => {
    const fetcher = vi.fn().mockRejectedValueOnce(new Error("net")).mockResolvedValueOnce({ ok: 1 });
    mount({ key: "retry", fetcher, softTimeoutMs: 5000 });
    await wait(0);
    expect(box.current.status).toBe("error");
    await act(async () => box.current.retry());
    await wait(0);
    expect(box.current.status).toBe("ready");
    expect(box.current.data).toEqual({ ok: 1 });
  });

  it("never lets cached data be overwritten by the fallback", async () => {
    writeCache("keep", { v: "cached" });
    mount({ key: "keep", fetcher: () => Promise.reject(new Error("net")), fallback: { v: "mock" }, softTimeoutMs: 5 });
    await wait(20);
    expect(box.current.data).toEqual({ v: "cached" });
    expect(box.current.isStale).toBe(true);
  });
});

describe("useResilientQuery without a fallback", () => {
  it("flags isSlow at the soft limit (honest wait message) and still lands the real data", async () => {
    const d = deferred();
    mount({ key: "slowless", fetcher: () => d.promise, softTimeoutMs: 20 });
    expect(box.current.isSlow).toBe(false);
    await wait(40);
    expect(box.current.status).toBe("loading");
    expect(box.current.isSlow).toBe(true);
    await act(async () => d.resolve({ v: 1 }));
    expect(box.current.isSlow).toBe(false);
    expect(box.current.data).toEqual({ v: 1 });
  });
});

describe("warmUp", () => {
  it("retries through a 503 (app up, DB asleep) until healthy, without throwing", async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: false, status: 503 })
      .mockRejectedValueOnce(new TypeError("offline"))
      .mockResolvedValueOnce({ ok: true, status: 200 });
    vi.stubGlobal("fetch", fetchMock);
    const p = warmUp();
    expect(getServerStatus().phase).toBe("waking");
    expect(warmUp()).toBe(p); // idempotent: one loop however many callers
    await vi.advanceTimersByTimeAsync(10_000);
    expect(await p).toBe(true);
    expect(getServerStatus().phase).toBe("awake");
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});

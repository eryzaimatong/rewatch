import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// canvasBrandMark.js builds a Path2D at import time; the test DOM has none.
vi.hoisted(() => {
  globalThis.Path2D = class {};
});

import jsQR from "jsqr";
import {
  CARD_FONT,
  CARD_FOOTER_TOP,
  CARD_H,
  CARD_W,
  compareUrl,
  displayUrl,
  drawCardFooter,
  drawQr,
  ensureCardFonts,
  shareOrDownloadBlob,
  toBlobSafe
} from "./shareCard";

/** A recording stand-in for CanvasRenderingContext2D — enough to replay a QR into pixels. */
function fakeCtx() {
  const calls = { fillRect: [], fillText: [] };
  const ctx = {
    fillStyle: "#000",
    font: "",
    textAlign: "left",
    fillRect(x, y, w, h) {
      calls.fillRect.push({ x, y, w, h, fill: ctx.fillStyle });
    },
    fillText(text, x, y) {
      calls.fillText.push({ text, x, y, font: ctx.font });
    },
    measureText(text) {
      return { width: text.length * 7 };
    },
    save() {},
    restore() {}
  };
  return { ctx, calls };
}

/** Rasterise recorded fillRects (white tile + dark modules) so a real QR decoder can read them. */
function rasterise(calls, width, height, scale = 4) {
  const w = width * scale;
  const h = height * scale;
  const data = new Uint8ClampedArray(w * h * 4).fill(0);
  const paint = (r) => {
    const light = /^(#fff|#ffffff|white)/i.test(r.fill);
    const v = light ? 255 : 0;
    for (let y = Math.floor(r.y * scale); y < Math.ceil((r.y + r.h) * scale); y++) {
      for (let x = Math.floor(r.x * scale); x < Math.ceil((r.x + r.w) * scale); x++) {
        if (x < 0 || y < 0 || x >= w || y >= h) continue;
        const i = (y * w + x) * 4;
        data[i] = data[i + 1] = data[i + 2] = v;
        data[i + 3] = 255;
      }
    }
  };
  calls.fillRect.forEach(paint);
  return { data, w, h };
}

describe("compareUrl / displayUrl", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("points at the public compare page on the configured site, tagged as coming from a card", () => {
    vi.stubEnv("VITE_SITE_URL", "https://rewatch-stories.vercel.app/");
    expect(compareUrl("mara", "card")).toBe("https://rewatch-stories.vercel.app/compare/mara?src=card");
  });

  it("encodes usernames so odd characters can't break the link", () => {
    vi.stubEnv("VITE_SITE_URL", "https://example.test");
    expect(compareUrl("a b/c")).toBe("https://example.test/compare/a%20b%2Fc");
  });

  it("falls back to the page origin when no site URL is configured", () => {
    vi.stubEnv("VITE_SITE_URL", "");
    expect(compareUrl("mara")).toBe(`${window.location.origin}/compare/mara`);
  });

  it("displays a clean, typeable URL without protocol or tracking params", () => {
    expect(displayUrl("https://rewatch-stories.vercel.app/compare/mara?src=card")).toBe(
      "rewatch-stories.vercel.app/compare/mara"
    );
  });
});

describe("ensureCardFonts", () => {
  let original;
  beforeEach(() => {
    original = Object.getOwnPropertyDescriptor(document, "fonts");
  });
  afterEach(() => {
    if (original) Object.defineProperty(document, "fonts", original);
    else delete document.fonts;
  });

  it("loads both weights of the brand font and waits for fonts.ready before resolving", async () => {
    const order = [];
    const load = vi.fn(async (spec) => {
      order.push(`load:${spec}`);
      return [];
    });
    Object.defineProperty(document, "fonts", {
      configurable: true,
      value: { load, ready: Promise.resolve().then(() => order.push("ready")) }
    });
    await expect(ensureCardFonts()).resolves.toBe(true);
    const specs = load.mock.calls.map((c) => c[0]);
    expect(specs.some((s) => s.startsWith("400") && s.includes("Inter Variable"))).toBe(true);
    expect(specs.some((s) => s.startsWith("700") && s.includes("Inter Variable"))).toBe(true);
    expect(order[order.length - 1]).toBe("ready");
  });

  it("never throws or hangs: a failing font load still lets the card export (returns false)", async () => {
    Object.defineProperty(document, "fonts", {
      configurable: true,
      value: { load: () => Promise.reject(new Error("blocked")), ready: Promise.resolve() }
    });
    await expect(ensureCardFonts()).resolves.toBe(false);
  });

  it("returns false when the Font Loading API does not exist", async () => {
    Object.defineProperty(document, "fonts", { configurable: true, value: undefined });
    await expect(ensureCardFonts()).resolves.toBe(false);
  });
});

describe("drawQr", () => {
  it("draws a QR code a real decoder can read back to the exact URL", () => {
    const url = "https://rewatch-stories.vercel.app/compare/mara?src=card";
    const { ctx, calls } = fakeCtx();
    drawQr(ctx, url, 10, 10, 120);
    const { data, w, h } = rasterise(calls, 140, 140, 4);
    const decoded = jsQR(data, w, h);
    expect(decoded?.data).toBe(url);
  });
});

describe("drawCardFooter", () => {
  it("puts a scannable link and the typeable URL on the card, in the brand font", () => {
    const url = "https://rewatch-stories.vercel.app/compare/mara?src=card";
    const { ctx, calls } = fakeCtx();
    drawCardFooter(ctx, { url });

    const texts = calls.fillText.map((c) => c.text);
    expect(texts).toContain("rewatch-stories.vercel.app/compare/mara");
    expect(calls.fillText.every((c) => c.font.includes(CARD_FONT))).toBe(true);

    const { data, w, h } = rasterise(calls, CARD_W, CARD_H, 2);
    expect(jsQR(data, w, h)?.data).toBe(url);
  });

  it("reserves enough room above it for the content area", () => {
    expect(CARD_FOOTER_TOP).toBeLessThan(CARD_H - 100);
  });
});

describe("shareOrDownloadBlob: the link survives platforms that strip it", () => {
  const phone = (share) => ({
    userAgent: "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148",
    canShare: vi.fn(() => true),
    share
  });
  const png = () => new Blob([new Uint8Array([137, 80, 78, 71])], { type: "image/png" });

  afterEach(() => vi.unstubAllGlobals());

  it("puts the compare link in BOTH the text and the url field next to the image", async () => {
    const share = vi.fn(async () => {});
    vi.stubGlobal("navigator", phone(share));
    const link = "https://rewatch-stories.vercel.app/compare/mara?src=share";

    const result = await shareOrDownloadBlob(png(), "mara-tastedna-card.png", {
      title: "My Re:Watch TasteDNA",
      text: "My TasteDNA archetype on Re:Watch: Professional Yearner",
      url: link,
      kind: "tastedna"
    });

    expect(result).toBe("shared");
    const payload = share.mock.calls[0][0];
    expect(payload.files).toHaveLength(1);
    expect(payload.url).toBe(link);
    expect(payload.text).toContain("Professional Yearner");
    expect(payload.text).toContain(link);
  });

  it("does not repeat the link if the caption already contains it", async () => {
    const share = vi.fn(async () => {});
    vi.stubGlobal("navigator", phone(share));
    const link = "https://example.test/compare/mara";
    await shareOrDownloadBlob(png(), "c.png", { text: `Compare with me: ${link}`, url: link });
    const text = share.mock.calls[0][0].text;
    expect(text.split(link).length - 1).toBe(1);
  });

  it("without a link it behaves exactly as before (no url field)", async () => {
    const share = vi.fn(async () => {});
    vi.stubGlobal("navigator", phone(share));
    await shareOrDownloadBlob(png(), "c.png", { title: "t", text: "caption only" });
    const payload = share.mock.calls[0][0];
    expect("url" in payload).toBe(false);
    expect(payload.text).toBe("caption only");
  });

  it("a person backing out of the share sheet is a cancel, not a download", async () => {
    const abort = Object.assign(new Error("cancelled"), { name: "AbortError" });
    vi.stubGlobal("navigator", phone(vi.fn(async () => { throw abort; })));
    await expect(shareOrDownloadBlob(png(), "c.png", { url: "https://example.test/x" })).resolves.toBe("cancelled");
  });
});

describe("toBlobSafe: Safari can return a null blob for a big canvas", () => {
  // A tiny valid PNG as a data URL (what toDataURL would give).
  const PNG_URL =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

  it("passes the real blob straight through when the browser provides one", async () => {
    const real = new Blob(["x"], { type: "image/png" });
    const canvas = { toBlob: (cb) => cb(real), toDataURL: () => PNG_URL };
    const got = await new Promise((resolve) => toBlobSafe(canvas, resolve));
    expect(got).toBe(real);
  });

  it("falls back to the canvas data URL when toBlob returns null", async () => {
    const canvas = { toBlob: (cb) => cb(null), toDataURL: () => PNG_URL };
    const got = await new Promise((resolve) => toBlobSafe(canvas, resolve));
    expect(got).toBeInstanceOf(Blob);
    expect(got.type).toBe("image/png");
    expect(got.size).toBeGreaterThan(8);
  });

  it("reports null (never a fake file) when nothing can be produced", async () => {
    const canvas = { toBlob: (cb) => cb(null), toDataURL: () => { throw new Error("canvas too large"); } };
    const got = await new Promise((resolve) => toBlobSafe(canvas, resolve));
    expect(got).toBeNull();
  });

  it("shareOrDownloadBlob refuses a null blob instead of exporting a file containing 'null'", async () => {
    await expect(shareOrDownloadBlob(null, "card.png", { kind: "tastedna" })).resolves.toBe("failed");
  });
});

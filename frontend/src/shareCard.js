// Shared plumbing for every canvas-exported 9:16 share card (Wrapped,
// TasteDNA, and the per-movie Match Card) — the part that's genuinely
// identical across all three: turning a finished blob into either a native
// share sheet or a download, and the background/brand-mark frame every card
// opens with. Card-specific content (what gets drawn inside the frame)
// stays in each component; only the truly shared plumbing lives here.
import qrcode from "qrcode-generator";
import { track } from "./telemetry";
import { drawBrandMark } from "./canvasBrandMark";

/**
 * The canvas font. It is the app's own bundled brand font (see main.jsx), not a
 * font that has to be fetched from somewhere: the cards used to name "Plus
 * Jakarta Sans", which nothing in the app ever loads, so every exported card
 * silently fell back to the system sans-serif.
 */
export const CARD_FONT = "'Inter Variable', Inter, system-ui, sans-serif";

/**
 * Canvas text does not wait for web fonts: drawing before a face has loaded
 * paints the fallback. Awaiting this first makes the exported image use the
 * real font. Never throws and never hangs: if fonts can't load, the card still
 * exports (returns false) rather than blocking the user's share.
 */
export function ensureCardFonts(timeoutMs = 3000) {
  const fonts = typeof document !== "undefined" ? document.fonts : undefined;
  if (!fonts || typeof fonts.load !== "function") return Promise.resolve(false);
  let timer;
  const loaded = Promise.all([fonts.load('400 14px "Inter Variable"'), fonts.load('700 14px "Inter Variable"')])
    .then(() => fonts.ready)
    .then(() => true)
    .catch(() => false);
  const timedOut = new Promise((resolve) => {
    timer = setTimeout(() => resolve(false), timeoutMs);
  });
  return Promise.race([loaded, timedOut]).finally(() => clearTimeout(timer));
}

/** Public origin of the site, without a trailing slash. */
export function siteOrigin() {
  const configured = (import.meta.env.VITE_SITE_URL || "").trim();
  const base = configured || (typeof window !== "undefined" ? window.location.origin : "");
  return base.replace(/\/+$/, "");
}

/** The no-account compare page a share card should send people to. `src` tags where the visit came from. */
export function compareUrl(username, src) {
  const url = `${siteOrigin()}/compare/${encodeURIComponent(username)}`;
  return src ? `${url}?src=${encodeURIComponent(src)}` : url;
}

/** What a person can read off the image and type: no protocol, no tracking params. */
export function displayUrl(url) {
  return url.replace(/^https?:\/\//, "").replace(/[?#].*$/, "").replace(/\/$/, "");
}

/**
 * `navigator.canShare({files})` reports true on several desktop Chromium/Edge
 * builds on Windows too, not just mobile — it hands off to the OS share
 * flyout, which for a freshly-created blob File (no thumbnail metadata) can
 * render with a blank preview and no working target, a dead end on desktop
 * web (confirmed live). Mobile OS share sheets handle the same file just
 * fine, so this gates the native path to where it actually works instead of
 * wherever the API merely claims to.
 */
function isMobileDevice() {
  return /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent || "");
}

export function downloadBlob(blob, filename) {
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  link.click();
  URL.revokeObjectURL(link.href);
}

/**
 * Native share sheet when the platform supports sharing files (mobile —
 * see isMobileDevice for why desktop is excluded even where the API claims
 * support) — this is the difference between "download a PNG, then open
 * Instagram, then manually attach it" and one tap straight into Stories/
 * Messages/TikTok. Falls back to a direct download everywhere else.
 */
export async function shareOrDownloadBlob(blob, filename, { title, text, kind } = {}) {
  const result = await shareOrDownloadInner(blob, filename, { title, text });
  track("card_exported", { kind: kind ?? "card", method: result });
  return result;
}

async function shareOrDownloadInner(blob, filename, { title, text }) {
  const file = new File([blob], filename, { type: blob.type });

  if (isMobileDevice() && navigator.canShare && navigator.canShare({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title, text });
      return "shared";
    } catch (err) {
      // AbortError = the user backed out of the share sheet themselves —
      // that's a real choice, not a failure to fall back from.
      if (err?.name === "AbortError") {
        return "cancelled";
      }
      // Any other failure (no matching share target, permission issue) does
      // fall back to a download rather than leaving the user with nothing.
    }
  }

  downloadBlob(blob, filename);
  return "downloaded";
}

/**
 * Loads an <img> and resolves once decoded — crossOrigin is required for any
 * image later drawn into a canvas that gets exported (an untainted canvas).
 *
 * The cache-busting query param is required, not cosmetic: every poster this
 * app displays is already loaded elsewhere (the feed grid, the modal) via a
 * plain <img> with no crossOrigin attribute. Browsers cache that response
 * keyed by URL, and a later crossOrigin="anonymous" request for the exact
 * same URL fails outright from that cache entry instead of transparently
 * re-fetching with CORS — confirmed directly (a same-session second load of
 * an already-displayed poster fired onerror 100% of the time). Appending a
 * throwaway query param makes it a different cache key, forcing a fresh,
 * correctly-CORS-negotiated fetch.
 */
export function loadImage(src, { crossOrigin = "anonymous" } = {}) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    if (crossOrigin) img.crossOrigin = crossOrigin;
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = crossOrigin ? `${src}${src.includes("?") ? "&" : "?"}_cb=${Date.now()}` : src;
  });
}

/**
 * Rasterizes a live DOM SVG element (e.g. TraitRadar's chart) into an <img>
 * for canvas drawImage — a serialized SVG loaded standalone has no access to
 * the page's external stylesheet, so the caller supplies literal (not
 * var(--x)) CSS for whatever classes the SVG relies on.
 */
export function svgElementToImage(svgElement, inlineCss) {
  return new Promise((resolve, reject) => {
    const clone = svgElement.cloneNode(true);
    if (inlineCss) {
      const style = document.createElementNS("http://www.w3.org/2000/svg", "style");
      style.textContent = inlineCss;
      clone.insertBefore(style, clone.firstChild);
    }
    const svgData = new XMLSerializer().serializeToString(clone);
    const svgBlob = new Blob([svgData], { type: "image/svg+xml;charset=utf-8" });
    const url = URL.createObjectURL(svgBlob);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      resolve(img);
    };
    img.onerror = (e) => {
      URL.revokeObjectURL(url);
      reject(e);
    };
    img.src = url;
  });
}

/** The 9:16 background + border + brand mark + eyebrow every card opens with — Stories/Reels/TikTok's native canvas at scale 2 exports exactly 1080x1920. */
export const CARD_SCALE = 2;
export const CARD_W = 540;
export const CARD_H = 960;

export function drawCardFrame(ctx, eyebrowText) {
  const grad = ctx.createLinearGradient(0, 0, CARD_W, CARD_H);
  grad.addColorStop(0, "#1f2029");
  grad.addColorStop(1, "#16233f");
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, CARD_W, CARD_H);
  ctx.strokeStyle = "rgba(69,197,180,0.5)";
  ctx.lineWidth = 2;
  ctx.strokeRect(1, 1, CARD_W - 2, CARD_H - 2);

  drawBrandMark(ctx, CARD_W / 2, 56, 16);

  ctx.textAlign = "center";
  ctx.fillStyle = "#45c5b4";
  ctx.font = `700 13px ${CARD_FONT}`;
  ctx.fillText(eyebrowText, CARD_W / 2, 100);
}

/** Where card content must stop: the footer (QR + link) needs the space below this line. */
export const CARD_FOOTER_TOP = CARD_H - 150;

/**
 * Draws a QR code for `text` inside a white tile (dark-on-light with a quiet
 * zone, which is what scanners need) filling a `size` square at (x, y).
 * Whole-pixel modules keep the edges crisp; uses fillRect only.
 */
export function drawQr(ctx, text, x, y, size) {
  const qr = qrcode(0, "M");
  qr.addData(text);
  qr.make();
  const count = qr.getModuleCount();
  const quiet = 3;
  const module = Math.max(1, Math.floor(size / (count + quiet * 2)));
  const tile = module * (count + quiet * 2);
  const left = x + (size - tile) / 2;
  const top = y + (size - tile) / 2;
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(left, top, tile, tile);
  ctx.fillStyle = "#0b1220";
  for (let row = 0; row < count; row++) {
    for (let col = 0; col < count; col++) {
      if (qr.isDark(row, col)) {
        ctx.fillRect(left + (col + quiet) * module, top + (row + quiet) * module, module, module);
      }
    }
  }
}

/**
 * The closing moment every card ends with: a scannable QR plus the same link
 * as text, so a screenshot posted anywhere carries people back into the app.
 * `url` is the full link encoded in the QR; the visible text drops the
 * protocol and tracking params.
 */
export function drawCardFooter(ctx, { url = siteOrigin() } = {}) {
  const qrSize = 84;
  const gap = 16;
  const rowTop = CARD_H - 128;
  const label = displayUrl(url);

  ctx.textAlign = "left";
  let urlPx = 13;
  ctx.font = `600 ${urlPx}px ${CARD_FONT}`;
  const maxText = CARD_W - 72 - qrSize - gap;
  while (ctx.measureText(label).width > maxText && urlPx > 9) {
    urlPx -= 1;
    ctx.font = `600 ${urlPx}px ${CARD_FONT}`;
  }
  const textWidth = Math.min(maxText, Math.max(ctx.measureText(label).width, 150));
  const startX = (CARD_W - (qrSize + gap + textWidth)) / 2;

  ctx.fillStyle = "rgba(255,255,255,0.12)";
  ctx.fillRect(CARD_W / 2 - 24, rowTop - 20, 48, 1);

  drawQr(ctx, url, startX, rowTop, qrSize);

  const textX = startX + qrSize + gap;
  ctx.fillStyle = "#ffffff";
  ctx.font = `700 15px ${CARD_FONT}`;
  ctx.fillText("Compare tastes with me", textX, rowTop + 28);
  ctx.fillStyle = "#45c5b4";
  ctx.font = `600 ${urlPx}px ${CARD_FONT}`;
  ctx.fillText(label, textX, rowTop + 50);
  ctx.fillStyle = "#71717a";
  ctx.font = `400 11px ${CARD_FONT}`;
  ctx.fillText("Scan it or type the link", textX, rowTop + 70);

  ctx.textAlign = "center";
  ctx.fillStyle = "#45c5b4";
  ctx.font = `700 12px ${CARD_FONT}`;
  ctx.fillText("RE:WATCH", CARD_W / 2, CARD_H - 18);
}

/**
 * Runs `drawContent` twice — once invisibly to measure how tall the
 * content block actually turns out, then for real with a vertical offset
 * that centers it in the space between the header and footer. Every card
 * that uses this draws a variable amount of content (2 roast receipts vs
 * 4, a radar chart plus movers vs a thin still-forming profile) into a
 * fixed 9:16 canvas — that fixed size is correct, not the bug (Stories/
 * Reels/TikTok expect exactly that ratio and crop anything else) — but
 * drawing top-down from a constant start Y left a dead, empty void at the
 * bottom of the exported image for any user with less than the maximum
 * amount of content. Confirmed live: real exported cards for Roast,
 * TasteDNA, and Wrapped all had 30-45% of the image as flat blank canvas.
 *
 * `drawContent(ctx, startY)` must return the final y it drew to, exactly
 * like wrapText already does — every existing card's draw function
 * already returns this, so wrapping them costs nothing structural.
 */
export function drawVerticallyCentered(ctx, startY, footerTop, drawContent) {
  ctx.save();
  ctx.globalAlpha = 0;
  const measuredEndY = drawContent(ctx, startY);
  ctx.restore();

  const available = footerTop - startY;
  const contentHeight = measuredEndY - startY;
  const offset = Math.max(0, (available - contentHeight) / 2);

  return drawContent(ctx, startY + offset);
}

export function wrapText(ctx, text, x, y, maxWidth, lineHeight) {
  const words = text.split(" ");
  let line = "";
  let cursorY = y;
  for (const word of words) {
    const test = line ? `${line} ${word}` : word;
    if (ctx.measureText(test).width > maxWidth && line) {
      ctx.fillText(line, x, cursorY);
      line = word;
      cursorY += lineHeight;
    } else {
      line = test;
    }
  }
  if (line) {
    ctx.fillText(line, x, cursorY);
  }
  return cursorY + lineHeight;
}

// Shared plumbing for every canvas-exported 9:16 share card (Wrapped,
// TasteDNA, and the per-movie Match Card) — the part that's genuinely
// identical across all three: turning a finished blob into either a native
// share sheet or a download, and the background/brand-mark frame every card
// opens with. Card-specific content (what gets drawn inside the frame)
// stays in each component; only the truly shared plumbing lives here.
import { drawBrandMark } from "./canvasBrandMark";

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
export async function shareOrDownloadBlob(blob, filename, { title, text } = {}) {
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
  grad.addColorStop(1, "#14151a");
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, CARD_W, CARD_H);
  ctx.strokeStyle = "rgba(168,85,247,0.5)";
  ctx.lineWidth = 2;
  ctx.strokeRect(1, 1, CARD_W - 2, CARD_H - 2);

  drawBrandMark(ctx, CARD_W / 2, 56, 16);

  ctx.textAlign = "center";
  ctx.fillStyle = "#a855f7";
  ctx.font = "bold 13px 'Plus Jakarta Sans', sans-serif";
  ctx.fillText(eyebrowText, CARD_W / 2, 100);
}

/** The closing brand moment every card ends with, anchored to the bottom. */
export function drawCardFooter(ctx) {
  ctx.textAlign = "center";
  ctx.fillStyle = "rgba(255,255,255,0.12)";
  ctx.fillRect(CARD_W / 2 - 24, CARD_H - 84, 48, 1);
  ctx.fillStyle = "#71717a";
  ctx.font = "12px 'Plus Jakarta Sans', sans-serif";
  ctx.fillText("Stories chosen for how you feel.", CARD_W / 2, CARD_H - 56);
  ctx.fillStyle = "#a855f7";
  ctx.font = "bold 13px 'Plus Jakarta Sans', sans-serif";
  ctx.fillText("RE:WATCH", CARD_W / 2, CARD_H - 36);
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

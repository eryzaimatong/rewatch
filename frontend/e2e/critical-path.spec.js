import { createRequire } from "node:module";
import { readFileSync } from "node:fs";
import { expect, mockBackend, test } from "./fixtures.js";

const require = createRequire(import.meta.url);
const JSQR_PATH = require.resolve("jsqr/dist/jsQR.js");

/**
 * The path every visitor who goes viral walks:
 *   sign in -> TasteDNA is generated -> pick a mood -> export the share card.
 * The exported card must be a real 1080x1920 PNG, drawn in the brand font, and
 * carry a QR code that scans back to this user's compare link.
 */
test("login -> TasteDNA generation -> mood click -> share card export", async ({ page }) => {
  // Keep a handle on the canvas behind the exported blob so we can read its pixels.
  await page.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.toBlob;
    HTMLCanvasElement.prototype.toBlob = function (...args) {
      window.__lastCardCanvas = this;
      return original.apply(this, args);
    };
  });
  const backend = await mockBackend(page);

  // 1. Sign in.
  await page.goto("/login");
  await page.getByLabel("Username").fill("mara");
  await page.getByLabel("Password", { exact: true }).fill("correct-horse-battery");
  await page.getByRole("button", { name: "Sign In" }).click();
  await expect(page.getByRole("heading", { name: /how do you want to feel tonight/i })).toBeVisible();
  await expect(page).toHaveURL(/\/$/);

  // 2. Mood click: the feed refetches for that mood and Ree takes on its colour.
  await page.getByRole("button", { name: /Romantic/i }).first().click();
  await expect(page.locator('.mood-room[data-mood="romantic"]')).toBeVisible();
  await expect.poll(() => backend.requests.some((r) => r.startsWith("GET /api/movies/popular") && r.includes("vibe=Romance"))).toBe(true);
  await expect(page.locator(".mood-room .ree-toned").first()).toHaveCSS("--ree-tint", "232,189,207");

  // 3. TasteDNA: a brand-new account reads as the undecided archetype...
  await page.locator('a[href="/profile"]:visible').first().click();
  await expect(page.locator(".taste-archetype")).toHaveText("The 'You Pick' Friend");
  await expect(page.locator(".taste-hero-blurb")).toContainText("algorithm can stop sweating");

  // ...and after the history is replayed it is generated into a real archetype.
  await page.getByRole("button", { name: "Rebuild my TasteDNA" }).click();
  await expect(page.locator(".taste-archetype")).toHaveText("Professional Yearner");

  // 4. Export the share card.
  await page.getByRole("button", { name: "Export taste card" }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toBeVisible();
  const [download] = await Promise.all([
    page.waitForEvent("download"),
    dialog.getByRole("button", { name: "Share Card" }).click()
  ]);

  // A real 1080x1920 PNG (Stories / Reels / TikTok native size).
  expect(download.suggestedFilename()).toBe("mara-tastedna-card.png");
  const png = readFileSync(await download.path());
  expect(png.subarray(1, 4).toString()).toBe("PNG");
  expect([png.readUInt32BE(16), png.readUInt32BE(20)]).toEqual([1080, 1920]);

  // Drawn in the brand font, not a fallback.
  expect(await page.evaluate(() => document.fonts.check('700 16px "Inter Variable"'))).toBe(true);

  // The viral loop closes: the QR in the footer scans back to this user's compare page.
  await page.addScriptTag({ path: JSQR_PATH });
  const scanned = await page.evaluate(() => {
    const canvas = window.__lastCardCanvas;
    const { width, height } = canvas;
    const footerTop = Math.round(height * 0.82);
    const image = canvas.getContext("2d").getImageData(0, footerTop, width, height - footerTop);
    return window.jsQR(image.data, image.width, image.height)?.data ?? null;
  });
  expect(scanned).toBe(`${new URL(page.url()).origin}/compare/mara?src=card`);
});

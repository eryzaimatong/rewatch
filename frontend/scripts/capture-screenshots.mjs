/**
 * Captures the README screenshots from the REAL app (real backend, real
 * database, production frontend build) with Playwright.
 *
 *   1. Start Postgres + the Spring Boot backend (see the root README)
 *   2. cd frontend && npm run build && npx vite preview --port 5173
 *   3. npm run screenshots          (first time: npx playwright install chromium)
 *
 * It creates a small demo cast through the public API (a demo viewer with real
 * ratings, plus two friends who follow them), so every page has believable
 * content, then saves PNGs into docs/screenshots/.
 *
 * Poster artwork: the sandbox/CI usually can't reach TMDB, so image requests are
 * answered with generated placeholder posters that show the title. The data,
 * layout and behaviour in the shots are real; only the artwork is a stand-in.
 *
 * Env: APP_URL (default http://localhost:5173), API_URL (default http://localhost:8080),
 *      OUT_DIR (default ../docs/screenshots)
 */
import { chromium } from "playwright";
import { mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const APP = process.env.APP_URL ?? "http://localhost:5173";
const API = process.env.API_URL ?? "http://localhost:8080";
const OUT =
  process.env.OUT_DIR ??
  path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../docs/screenshots");

const PASSWORD = "Demo-pass-123!";
const MOMENTS = ["Ending Payoff", "Character Dialogue", "Plot Twist", "Quiet Intimate Scene", "Music & Atmosphere"];

/* ------------------------------------------------------------------ API */

async function call(method, url, { token, body } = {}) {
  const res = await fetch(`${API}${url}`, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined
  });
  const data = await res.json().catch(() => null);
  return { ok: res.ok, status: res.status, data };
}

async function ensureUser(username) {
  const email = `${username}@demo.rewatch.local`;
  let r = await call("POST", "/api/auth/login", { body: { username, password: PASSWORD } });
  if (!r.ok) {
    await call("POST", "/api/auth/register", { body: { username, password: PASSWORD, email } });
    r = await call("POST", "/api/auth/login", { body: { username, password: PASSWORD } });
  }
  if (!r.ok) throw new Error(`Could not sign in as ${username}: ${r.status} ${JSON.stringify(r.data)}`);
  return r.data; // { userId, token, onboarded, ... }
}

async function rateSome(user, titles, offset) {
  for (let i = 0; i < titles.length; i++) {
    const t = titles[i];
    const overall = [5, 4, 5, 3, 4, 5, 4, 2, 5, 4, 3, 5][(i + offset) % 12];
    await call("POST", "/api/movies/rate", {
      token: user.token,
      body: {
        userId: user.userId,
        titleId: t.titleId,
        title: t.title,
        overall,
        chars: Math.min(5, overall + (i % 2)),
        ending: Math.max(1, overall - (i % 3 === 0 ? 1 : 0)),
        visuals: 3 + ((i + offset) % 3),
        story: Math.min(5, overall),
        rewatch: Math.max(1, overall - 1),
        moment: MOMENTS[(i + offset) % MOMENTS.length]
      }
    });
  }
}

async function seed() {
  const demo = await ensureUser("demo_viewer");
  const mira = await ensureUser("mira_watches");
  const jun = await ensureUser("jun_after_midnight");
  await ensureUser("fresh_arrival"); // never onboarded: used for the onboarding shot

  const popular = (await call("GET", "/api/movies/popular", { token: demo.token })).data ?? [];
  const titles = popular.filter((t) => t.titleId).slice(0, 40);
  if (titles.length < 12) throw new Error("The catalog is too small — seed the database first.");

  await call("POST", "/api/movies/onboard", {
    token: demo.token,
    body: { userId: demo.userId, favs: titles.slice(0, 5).map((t) => t.title) }
  });
  await rateSome(demo, titles.slice(0, 16), 0);
  await rateSome(mira, titles.slice(4, 18), 3);
  await rateSome(jun, titles.slice(8, 22), 6);

  for (const t of titles.slice(20, 25)) {
    await call("POST", "/api/watchlist/items", {
      token: demo.token,
      body: { userId: demo.userId, titleId: t.titleId, tmdbId: t.tmdbId ?? t.id, title: t.title }
    });
  }
  // Everyone follows everyone, so Community and the activity feed have content.
  for (const [a, b] of [[mira, demo], [jun, demo], [demo, mira], [demo, jun]]) {
    await call("POST", `/api/social/follow/${b.userId}`, { token: a.token });
  }
  return { demo, fresh: await ensureUser("fresh_arrival") };
}

/* -------------------------------------------------------- placeholder art */

// poster path -> title, built from the API before the browser starts so the
// image handler never has to wait on anything.
const ART = new Map();

async function warmArt(user) {
  const urls = [
    "/api/movies/popular",
    "/api/movies/trending",
    `/api/recommendations/${user.userId}`,
    ...["movie", "show", "anime"].map((b) => `/api/titles/picker?bucket=${b}`)
  ];
  for (const u of urls) {
    const r = await call("GET", u, { token: user.token });
    if (r.ok) collectArt(r.data, ART);
  }
}

function posterSvg(title, w, h) {
  let hash = 0;
  for (const c of title) hash = (hash * 31 + c.charCodeAt(0)) >>> 0;
  const h1 = hash % 360;
  const h2 = (h1 + 55) % 360;
  const words = title.split(" ");
  const lines = [];
  let cur = "";
  for (const word of words) {
    if ((cur + " " + word).trim().length > 12) { lines.push(cur.trim()); cur = word; } else cur += " " + word;
  }
  lines.push(cur.trim());
  const size = h * 0.07;
  const text = lines
    .slice(0, 4)
    .map((l, i) => `<text x="${w * 0.09}" y="${h * 0.7 + i * size * 1.15}" font-size="${size}" font-weight="800" fill="#fff" font-family="Inter,Arial,sans-serif">${l.replace(/&/g, "&amp;")}</text>`)
    .join("");
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl(${h1},50%,34%)"/><stop offset="1" stop-color="hsl(${h2},55%,16%)"/></linearGradient></defs><rect width="${w}" height="${h}" fill="url(#g)"/><circle cx="${w * 0.72}" cy="${h * 0.28}" r="${h * 0.19}" fill="rgba(255,255,255,.09)"/>${text}</svg>`;
}

function collectArt(node, map) {
  if (!node || typeof node !== "object") return;
  if (Array.isArray(node)) return node.forEach((n) => collectArt(n, map));
  const p = node.posterPath ?? node.poster;
  if (typeof p === "string" && typeof node.title === "string") map.set(p.replace(/^\//, ""), node.title);
  Object.values(node).forEach((v) => collectArt(v, map));
}

async function newContext(browser, { width, height, mobile = false, session }) {
  const context = await browser.newContext({
    viewport: { width, height },
    deviceScaleFactor: mobile ? 2 : 1,
    isMobile: mobile,
    hasTouch: mobile,
    serviceWorkers: "block"
  });
  if (session) {
    await context.addInitScript((s) => {
      for (const [k, v] of Object.entries(s)) if (v != null) localStorage.setItem(k, String(v));
    }, {
      token: session.token,
      userId: session.userId,
      username: session.username,
      accentColor: session.accentColor,
      onboarded: session.onboarded ? "yes" : null
    });
  }
  const page = await context.newPage();
  page.on("response", (r) => {
    // Fallback for titles the warm-up missed (e.g. discovery shelves).
    if (!r.url().startsWith(API) || !(r.headers()["content-type"] ?? "").includes("json")) return;
    r.json().then((body) => collectArt(body, ART)).catch(() => {});
  });
  await page.route(/image\.tmdb\.org|placehold\.co/, (route) => {
    const file = decodeURIComponent(route.request().url().split("/").pop() ?? "").split("?")[0];
    const title = ART.get(file) ?? "Re:Watch";
    return route.fulfill({ contentType: "image/svg+xml", body: posterSvg(title, 500, 750) });
  });
  await page.route(/youtube|ytimg|googleapis|gstatic/, (route) => route.abort());
  return { context, page };
}

/* ------------------------------------------------------------------ shots */

// Wait until every image on the page has finished loading (lazy ones included
// once scrolled into view), then give animations a moment to land.
async function settle(page, ms = 1200) {
  await page.evaluate(async () => {
    const imgs = [...document.images];
    await Promise.all(
      imgs.map((img) =>
        img.complete
          ? Promise.resolve()
          : new Promise((res) => { img.addEventListener("load", res, { once: true }); img.addEventListener("error", res, { once: true }); setTimeout(res, 4000); })
      )
    );
  }).catch(() => {});
  await page.waitForTimeout(ms);
}

async function main() {
  await mkdir(OUT, { recursive: true });
  const { demo, fresh } = await seed();
  await warmArt(demo);
  const browser = await chromium.launch();
  const saved = [];
  const snap = async (target, name) => {
    await target.screenshot({ path: path.join(OUT, `${name}.png`) });
    saved.push(name);
    console.log("  ✓", name);
  };

  // Desktop, signed out
  {
    const { context, page } = await newContext(browser, { width: 1440, height: 900 });
    await page.goto(APP);
    await page.waitForSelector(".auth-card");
    await settle(page);
    await snap(page, "01-sign-in");
    await context.close();
  }

  // Desktop, brand-new account -> onboarding
  {
    const { context, page } = await newContext(browser, { width: 1440, height: 1000, session: { ...fresh, onboarded: false } });
    await page.goto(APP);
    await page.waitForSelector(".onboard-fav-chip");
    await settle(page);
    const chips = await page.$$(".onboard-fav-chip");
    for (const i of [0, 2, 4]) await chips[i]?.click();
    await settle(page, 600);
    await snap(page, "02-onboarding");
    await context.close();
  }

  // Desktop, the demo viewer
  const desktop = await newContext(browser, { width: 1440, height: 900, session: { ...demo, onboarded: true } });
  {
    const page = desktop.page;
    await page.goto(APP);
    await page.waitForSelector(".cx-hero-stage");
    await settle(page, 2600);
    await snap(page, "03-home");

    await snap(page.locator(".cx-section").first(), "04-why-it-fits");
    await page.locator(".mood-room").scrollIntoViewIfNeeded();
    await page.locator(".mood-card", { hasText: "Calm" }).click();
    await settle(page, 2200);
    await snap(page.locator(".mood-room"), "05-mood-picker");
    await page.locator(".mood-card", { hasText: "Calm" }).click(); // toggle back off

    await page.locator(".cx-wheel").scrollIntoViewIfNeeded();
    await settle(page, 700);
    await snap(page.locator(".cx-wheel"), "06-selected-films");

    await page.evaluate(() => window.scrollTo(0, 0));
    await page.click(".cx-square");
    await page.waitForSelector(".modal-card");
    await settle(page, 1500);
    await page.setViewportSize({ width: 1440, height: 1000 });
    await snap(page, "07-match-explained");
    await page.keyboard.press("Escape");
    await page.setViewportSize({ width: 1440, height: 900 });

    for (const [route, name, selector, height] of [
      ["/profile", "08-taste-profile", ".taste-hero", 1500],
      ["/community", "09-community", ".community-page", 1000],
      ["/achievements", "10-achievements", ".page-hero", 1200],
      ["/watchlist", "11-watchlist", "h2", 1100],
      ["/settings", "12-settings", ".settings-nav", 1000]
    ]) {
      await page.setViewportSize({ width: 1440, height });
      await page.goto(APP + route);
      await page.waitForSelector(selector);
      await settle(page, 2200);
      await snap(page, name);
    }
  }
  await desktop.context.close();

  // Phone
  {
    const { context, page } = await newContext(browser, { width: 390, height: 844, mobile: true, session: { ...demo, onboarded: true } });
    await page.goto(APP);
    await page.waitForSelector(".cx-hero-stage");
    await settle(page, 2600);
    await snap(page, "13-mobile-home");
    await page.locator(".mood-room").scrollIntoViewIfNeeded();
    await settle(page, 600);
    await snap(page, "14-mobile-moods");
    await page.goto(APP + "/profile");
    await page.waitForSelector(".taste-hero");
    await settle(page, 2200);
    await snap(page, "15-mobile-profile");
    await context.close();
  }

  await browser.close();
  console.log(`\nSaved ${saved.length} screenshots to ${OUT}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

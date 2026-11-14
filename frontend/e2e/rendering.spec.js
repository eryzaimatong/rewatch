import { expect, mockBackend, test } from "./fixtures.js";

/**
 * Ree must never be sliced by a container: her shadow and aura have to bleed
 * into the page without a hard rectangular cut-off. For every Ree on the page
 * this measures her visual box (figure + drop-shadow + aura) against every
 * ancestor that clips overflow.
 */
async function reeClips(page) {
  return page.evaluate(() => {
    const clips = (el) => {
      const cs = getComputedStyle(el);
      return (cs.overflowX !== "visible" || cs.overflowY !== "visible") && el !== document.documentElement && el !== document.body;
    };
    const found = [];
    document.querySelectorAll(".ree-live, .ree--canonical").forEach((ree) => {
      const img = ree.matches("img") ? ree : ree.querySelector(".ree-img");
      if (!img) return;
      const r = img.getBoundingClientRect();
      if (r.width < 4) return;
      const aura = ree.querySelector(".ree-aura")?.getBoundingClientRect();
      const vis = { l: r.left - 14, r: r.right + 14, t: r.top - 4, b: r.bottom + 24 };
      if (aura) {
        vis.l = Math.min(vis.l, aura.left - 14);
        vis.r = Math.max(vis.r, aura.right + 14);
        vis.t = Math.min(vis.t, aura.top - 14);
        vis.b = Math.max(vis.b, aura.bottom + 14);
      }
      for (let el = ree.parentElement; el; el = el.parentElement) {
        if (!clips(el)) continue;
        const c = el.getBoundingClientRect();
        const cut = Math.max(c.left - vis.l, vis.r - c.right, c.top - vis.t, vis.b - c.bottom);
        if (cut > 1) found.push(`${el.tagName.toLowerCase()}.${[...el.classList].join(".")} cuts Ree by ${Math.round(cut)}px`);
      }
    });
    return found;
  });
}

test("signed-out home: Ree is not clipped by any container", async ({ page }) => {
  await mockBackend(page);
  await page.goto("/");
  await expect(page.locator(".ree-live").first()).toBeVisible();
  await page.getByRole("button", { name: /Calm/i }).first().click();
  await page.waitForTimeout(800);
  expect(await reeClips(page)).toEqual([]);
});

test("signed-in feed and TasteDNA: Ree is not clipped by any container", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("token", "e2e-token");
    localStorage.setItem("userId", "42");
    localStorage.setItem("username", "mara");
    localStorage.setItem("onboarded", "yes");
  });
  await mockBackend(page);
  for (const path of ["/", "/profile"]) {
    await page.goto(path);
    await expect(page.locator(".ree-live").first()).toBeVisible();
    await page.waitForTimeout(800);
    expect(await reeClips(page), `on ${path}`).toEqual([]);
  }
});

test("hero: Ree never covers a control (every hero button is reachable)", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("token", "e2e-token");
    localStorage.setItem("userId", "42");
    localStorage.setItem("username", "mara");
    localStorage.setItem("onboarded", "yes");
  });
  await mockBackend(page);
  await page.goto("/");
  await expect(page.locator(".cx-hero-ree")).toBeVisible();
  await page.waitForTimeout(800);
  // Only Ree covering a control is a failure here (a fixed nav sitting over the
  // fold is normal), so look at what sits on top of each button's centre.
  const blocked = await page.evaluate(() =>
    [...document.querySelectorAll(".cx-hero-stage button")]
      .filter((btn) => btn.getBoundingClientRect().width > 4)
      .filter((btn) => {
        btn.scrollIntoView({ block: "center" });
        const r = btn.getBoundingClientRect();
        const top = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
        return !!top?.closest(".ree-live");
      })
      .map((btn) => btn.getAttribute("aria-label") || btn.textContent.trim())
  );
  expect(blocked).toEqual([]);
});

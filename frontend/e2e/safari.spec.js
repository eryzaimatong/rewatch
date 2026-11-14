import { expect, mockBackend, test } from "./fixtures.js";

/**
 * Safari-specific traps that Chromium never shows.
 *
 * iOS Safari zooms the whole page when an input with a computed font size below
 * 16px gets focus, and doesn't zoom back: a jarring jump on every login, search
 * and settings field. Every text field on every screen must be at least 16px.
 */
async function smallInputs(page) {
  return page.evaluate(() =>
    [...document.querySelectorAll("input:not([type=checkbox]):not([type=radio]):not([type=hidden]):not([type=range]), textarea, select")]
      .filter((el) => el.getBoundingClientRect().width > 0)
      .map((el) => ({ el: `${el.tagName.toLowerCase()}#${el.id || ""}.${[...el.classList].join(".")}`, px: parseFloat(getComputedStyle(el).fontSize) }))
      .filter((x) => x.px < 16)
  );
}

test("no text field is smaller than 16px, so iOS never zooms on focus", async ({ page }) => {
  await mockBackend(page);

  await page.goto("/login");
  await expect(page.locator("#login-username")).toBeVisible();
  expect(await smallInputs(page), "login").toEqual([]);

  await page.addInitScript(() => {
    localStorage.setItem("token", "e2e-token");
    localStorage.setItem("userId", "42");
    localStorage.setItem("username", "mara");
    localStorage.setItem("onboarded", "yes");
  });
  for (const path of ["/", "/settings", "/community"]) {
    await page.goto(path);
    await page.waitForTimeout(800);
    expect(await smallInputs(page), path).toEqual([]);
  }
});

/**
 * WebKit renders `filter: drop-shadow()` wrongly (clipped to a rectangle, or
 * flickering) on an element that sits inside a 3D-transformed subtree. Ree's
 * head-turn is exactly that, so the filter must live on a wrapper OUTSIDE the
 * 3D-transformed element, never on it or on anything inside it.
 */
test("Ree: filters sit outside the 3D head-turn, never inside it", async ({ page, isMobile }) => {
  test.skip(isMobile, "the head-turn follows a mouse pointer");
  await mockBackend(page);
  await page.goto("/");
  await expect(page.locator(".ree-img").first()).toBeVisible();
  await page.mouse.move(40, 400);
  await page.waitForTimeout(900);

  const report = await page.evaluate(() => {
    const ree = document.querySelector(".ree-live");
    const look = ree.querySelector(".ree-look");
    const hasFilter = (el) => getComputedStyle(el).filter !== "none";
    const insideOrOn = [look, ree.querySelector(".ree-body"), ree.querySelector(".ree-hop"), ree.querySelector(".ree-img")];
    return {
      is3D: /matrix3d/.test(getComputedStyle(look).transform),
      filterOnOrInside3D: insideOrOn.filter(hasFilter).map((el) => el.className),
      filterOutside: !!look.parentElement && hasFilter(look.parentElement)
    };
  });

  expect(report.is3D, "the head-turn should actually be 3D at this pointer position").toBe(true);
  expect(report.filterOnOrInside3D).toEqual([]);
  expect(report.filterOutside).toBe(true);
});

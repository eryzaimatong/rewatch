import { describe, expect, it } from "vitest";
import { FAV_PAGE_SIZE, computeVisibleFavTitles } from "./onboardingUtils";

// Regression coverage for the fresh-account boot crash: a bucket with
// thousands of titles (Movies alone is 3000+) used to render every single
// one as a poster <img> + button, confirmed live to produce a ~95,000px-tall
// Step 1. This asserts the invariant that actually prevents that: an
// unfiltered result never exceeds FAV_PAGE_SIZE, no matter how large the
// source bucket is.
function bucket(n) {
  return Array.from({ length: n }, (_, i) => ({
    title: `Title ${i}`,
    popularity: n - i
  }));
}

describe("computeVisibleFavTitles", () => {
  it("never renders more than the page size for a large unfiltered bucket", () => {
    const result = computeVisibleFavTitles(bucket(3000), "", []);
    expect(result.length).toBeLessThanOrEqual(FAV_PAGE_SIZE);
  });

  it("keeps the most popular titles on the first page", () => {
    const result = computeVisibleFavTitles(bucket(200), "", []);
    expect(result[0].title).toBe("Title 0");
    expect(result).toHaveLength(FAV_PAGE_SIZE);
  });

  it("keeps a title already picked visible even once it falls outside the page", () => {
    const list = bucket(200);
    const cutOffPick = list[150].title; // well past FAV_PAGE_SIZE=60 by popularity rank
    const result = computeVisibleFavTitles(list, "", [cutOffPick]);
    expect(result.map((t) => t.title)).toContain(cutOffPick);
  });

  it("search still reaches titles outside the default page", () => {
    const list = bucket(200);
    const buriedTitle = list[150].title;
    const result = computeVisibleFavTitles(list, buriedTitle, []);
    expect(result.map((t) => t.title)).toContain(buriedTitle);
  });

  it("an empty bucket returns an empty page", () => {
    expect(computeVisibleFavTitles([], "", [])).toEqual([]);
  });
});

// Rendering every title in a bucket at once (a type can run into the
// thousands) meant an unfiltered onboarding step was quietly putting
// thousands of poster <img> nodes on the page simultaneously — confirmed
// live: a fresh account's Step 1 rendered a ~95,000px-tall page. Capped to
// a page of well-known titles instead; search still reaches the rest of
// the bucket, and a title already picked stays visible even if it would
// otherwise fall outside the cap, so choosing it never makes it disappear.
export const FAV_PAGE_SIZE = 60;

export function computeVisibleFavTitles(list, query, favs, pageSize = FAV_PAGE_SIZE) {
  const q = query.trim().toLowerCase();
  const matches = q ? list.filter((t) => t.title.toLowerCase().includes(q)) : list;
  const sorted = [...matches].sort((a, b) => (b.popularity ?? 0) - (a.popularity ?? 0));
  const page = sorted.slice(0, pageSize);
  const pickedButCutOff = sorted.slice(pageSize).filter((t) => favs.includes(t.title));
  return [...page, ...pickedButCutOff];
}

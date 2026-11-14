// Ree artwork URL maps (Vite resolves each import.meta.glob at build time).
const poseUrls = import.meta.glob("./assets/ree/expr-*.webp", { eager: true, query: "?url", import: "default" });
const moodUrls = import.meta.glob("./assets/ree/mood-*.webp", { eager: true, query: "?url", import: "default" });
export const canonicalUrl = Object.values(
  import.meta.glob("./assets/ree/ree-canonical.webp", { eager: true, query: "?url", import: "default" })
)[0];
export const iconUrl = Object.values(
  import.meta.glob("./assets/ree/ree-icon.webp", { eager: true, query: "?url", import: "default" })
)[0];

const byName = (map, prefix) =>
  Object.fromEntries(
    Object.entries(map).map(([path, url]) => [path.split(`/${prefix}-`)[1].replace(".webp", ""), url])
  );

export const POSES = byName(poseUrls, "expr");
export const MOOD_POSES = byName(moodUrls, "mood");


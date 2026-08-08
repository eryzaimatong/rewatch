import Rey from "./Rey";

const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w200";

function posterUrl(path) {
  if (!path) return null;
  return path.startsWith("http") ? path : `${TMDB_IMAGE_BASE_URL}${path}`;
}

/**
 * A collection's cover, auto-built from up to 4 real poster thumbnails
 * already returned alongside it (SocialService.collectionSummary's
 * previewPosters — no new backend work, no generated/cached image file,
 * just a live layout of what's actually in the list right now). Layout
 * mirrors the familiar "4-up" collaborative-playlist tile: 1 poster fills
 * the frame, 2 split it in half, 3 is one big + two stacked, 4 is a full grid.
 */
export default function CollageCover({ posters = [], size = 64, className = "" }) {
  const urls = posters.map(posterUrl).filter(Boolean).slice(0, 4);

  if (urls.length === 0) {
    return (
      <div
        className={`collage-cover collage-cover--empty ${className}`}
        style={{ width: size, height: size }}
      >
        <Rey mood="empty" size={Math.round(size * 0.6)} />
      </div>
    );
  }

  return (
    <div
      className={`collage-cover collage-cover--${urls.length} ${className}`}
      style={{ width: size, height: size }}
    >
      {urls.map((u, i) => (
        <img key={i} src={u} alt="" loading="lazy" />
      ))}
    </div>
  );
}

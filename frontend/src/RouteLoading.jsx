import BrandMark from "./BrandMark";

/**
 * Suspense fallback for every lazy-loaded route — deliberately light (brand
 * mark + the same aperture spinner used everywhere else in the app, see
 * .loading-orb in App.css) rather than a heavy skeleton, since this is
 * usually a sub-100ms chunk fetch on a warm cache. It used to render nothing
 * at all: a blank frame on a slow connection pulling a lazy chunk cold reads
 * as broken before the page underneath even gets a chance to make its own
 * (unrelated) backend call, so this always shows something, however brief.
 */
export default function RouteLoading() {
  return (
    <div
      role="status"
      aria-label="Loading page"
      style={{
        minHeight: "40vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: "var(--sp-3)"
      }}
    >
      <BrandMark size={32} />
      <div className="loading-orb" />
    </div>
  );
}

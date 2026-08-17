/** Suspense fallback for lazy-loaded routes — deliberately minimal so switching pages doesn't flash a heavy skeleton for what's usually a sub-100ms chunk fetch on a warm cache. */
export default function RouteLoading() {
  return (
    <div
      role="status"
      aria-label="Loading page"
      style={{ minHeight: "40vh", display: "grid", placeItems: "center" }}
    />
  );
}

const LINE_WIDTHS = ["wide", "mid", "narrow"];

export function SkeletonPosterGrid({ count = 4, gridClassName = "recommendation-grid", lines = 3 }) {
  return (
    <div className={gridClassName}>
      {Array.from({ length: count }, (_, i) => (
        <div className="skeleton-card" key={i}>
          <div className="skeleton-poster" />
          <div className="skeleton-body">
            {Array.from({ length: lines }, (_, j) => (
              <div key={j} className={`skeleton-line skeleton-line--${LINE_WIDTHS[j] || "mid"}`} />
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}

export function SkeletonPersonGrid({ count = 4 }) {
  return (
    <div className="dna-match-grid">
      {Array.from({ length: count }, (_, i) => (
        <div className="dna-match-card" key={i}>
          <div className="skeleton-avatar" />
          <div className="skeleton-body skeleton-body--inline">
            <div className="skeleton-line skeleton-line--wide" />
            <div className="skeleton-line skeleton-line--narrow" />
          </div>
        </div>
      ))}
    </div>
  );
}

/** Mirrors .movie-card / .recommendation-grid cards: poster 2:3 + title + meta. */
export function SkeletonMovieRow({ count = 6 }) {
  return <SkeletonPosterGrid count={count} lines={2} />;
}

/** Mirrors TasteProfile's radar card + two-column trait grid, so nothing jumps. */
export function TasteSkeleton({ traitCount = 6 }) {
  return (
    <div role="status" aria-label="Loading your TasteDNA" aria-busy="true">
      <div className="taste-grid">
        <section className="taste-card taste-radar-card skeleton-radar-card">
          <div className="skeleton-radar" />
        </section>
        <section className="taste-card">
          <div className="skeleton-line skeleton-line--wide skeleton-title" />
          <div className="taste-trait-grid">
            {Array.from({ length: traitCount }, (_, i) => (
              <div className="trait-card skeleton-trait" key={i}>
                <div className="skeleton-line skeleton-line--wide" />
                <div className="skeleton-line skeleton-bar" />
                <div className="skeleton-line skeleton-line--narrow" />
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}

/** Mirrors DailyDoubleFeature's two poster buttons. */
export function DoubleFeatureSkeleton() {
  return (
    <section className="feed-section daily-double-feature" aria-busy="true" aria-label="Loading today's double feature">
      <div className="skeleton-line skeleton-line--narrow" />
      <div className="skeleton-line skeleton-line--wide skeleton-title" />
      <div className="df-grid">
        {[0, 1].map((i) => (
          <div className="df-card skeleton-card" key={i}>
            <div className="skeleton-poster" />
            <div className="skeleton-body">
              <div className="skeleton-line skeleton-line--wide" />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

/** Mirrors AchievementCard rows inside .achievement-grid. */
export function SkeletonAchievementGrid({ count = 6 }) {
  return (
    <section className="feed-section" aria-busy="true" aria-label="Loading your achievements">
      <div className="skeleton-line skeleton-line--narrow" style={{ marginBottom: "var(--sp-2)" }} />
      <div className="achievement-grid">
        {Array.from({ length: count }, (_, i) => (
          <div className="skeleton-card" key={i}>
            <div className="skeleton-body">
              <div className="skeleton-avatar" />
              <div className="skeleton-line skeleton-line--wide" />
              <div className="skeleton-line skeleton-line--mid" />
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

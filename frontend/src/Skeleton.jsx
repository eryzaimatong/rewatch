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

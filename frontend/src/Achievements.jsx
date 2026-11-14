import { useResilientQuery, fetchJson } from "./resilience/useResilientQuery";
import StatusBanner from "./resilience/StatusBanner";
import ErrorState from "./ErrorState";
import { SkeletonAchievementGrid } from "./Skeleton";
import Ree from "./Ree";
import "./App.css";

const CATEGORY_LABELS = {
  streak: "Watch Streak",
  evidence: "Rating Milestones",
  tastedna: "TasteDNA Confidence",
  social: "Social",
  collecting: "Collecting"
};

const CATEGORY_ORDER = ["streak", "evidence", "tastedna", "social", "collecting"];

function groupByCategory(achievements) {
  const groups = new Map();
  for (const a of achievements) {
    if (!groups.has(a.category)) groups.set(a.category, []);
    groups.get(a.category).push(a);
  }
  return CATEGORY_ORDER.filter((c) => groups.has(c)).map((c) => [c, groups.get(c)]);
}

function AchievementCard({ achievement }) {
  const { title, description, unlocked, current, target } = achievement;
  const pct = target > 0 ? Math.min(100, Math.round((current / target) * 100)) : 0;

  return (
    <div className={`achievement-card${unlocked ? " is-unlocked" : ""}`}>
      <div className="achievement-card-row">
        <span className="achievement-badge" aria-hidden="true">{unlocked ? "✦" : "✧"}</span>
        <div className="achievement-card-body">
          <p className="achievement-title">{title}</p>
          <p className="achievement-desc">{description}</p>
        </div>
      </div>
      {!unlocked && (
        <div className="achievement-progress">
          <div className="achievement-progress-track">
            <div className="achievement-progress-fill" style={{ width: `${pct}%` }} />
          </div>
          <span className="achievement-progress-label mono">{current} / {target}</span>
        </div>
      )}
    </div>
  );
}

export default function Achievements() {
  const userid = Number(localStorage.getItem("userId")) || 1;

  const { data: summary, status, source, isStale, isRevalidating, isSlow, retry } = useResilientQuery({
    key: `achievements:${userid}`,
    fetcher: (signal) => fetchJson(`/api/tastedna/achievements/${userid}`, { signal }),
    validate: (d) => !!d && Array.isArray(d.achievements)
  });
  const loading = status === "loading";

  return (
    <div className="page-shell page-shell--wide">
      <div className="page-panel taste-panel">
        <div className="page-hero">
          <div className="page-hero-copy">
            <span className="eyebrow">Achievements</span>
            <h1>
              {summary
                ? <>
                    <span className="mono">{summary.unlockedCount}</span> of <span className="mono">{summary.totalCount}</span> unlocked
                  </>
                : "Your Milestones"}
            </h1>
            {summary && summary.totalCount > 0 && (
              <div
                className="page-hero-meter"
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={summary.totalCount}
                aria-valuenow={summary.unlockedCount}
                aria-label="Achievements unlocked"
              >
                <i style={{ width: `${Math.round((summary.unlockedCount / summary.totalCount) * 100)}%` }} />
              </div>
            )}
            <p className="page-hero-sub">Little things you do, noticed. Ree keeps count.</p>
          </div>
          <Ree pose="celebrating" size={120} className="page-hero-ree" />
        </div>

        <StatusBanner source={source} isStale={isStale} isRevalidating={isRevalidating} isSlow={isSlow} onRetry={retry} subject="your achievements" />

        {loading && <SkeletonAchievementGrid />}

        {status === "error" && (
          <ErrorState
            title="Your milestones are still warming up"
            message="We couldn't reach the server just now. Nothing you've unlocked is lost."
            onRetry={retry}
            escapeTo="/"
            escapeLabel="Pick a film by mood"
          />
        )}

        {!loading && summary && groupByCategory(summary.achievements).map(([category, items]) => (
          <section key={category} className="feed-section">
            <p className="section-eyebrow">{CATEGORY_LABELS[category] || category}</p>
            <div className="achievement-grid">
              {items.map((a) => (
                <AchievementCard key={a.key} achievement={a} />
              ))}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}

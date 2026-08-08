import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import TraitRadar from "./TraitRadar";
import MatchRing from "./MatchRing";
import Avatar from "./Avatar";
import { authHeaders } from "./auth";
import { BASE, blockUser, fileReport } from "./api";
import ConfirmDialog from "./ConfirmDialog";
import ReportDialog from "./ReportDialog";
import EmptyState from "./EmptyState";
import ReviewInteractions from "./ReviewInteractions";
import CollageCover from "./CollageCover";
import "./App.css";

// dnaMatches()/publicProfile() send raw centred-cosine similarity in [-1, 1];
// MatchRing expects a 0-100 score, same scale as the movie-match rings and
// Community's DNA-match list — see Community.jsx's identical helper.
function toMatchPercent(cosine) {
  return Math.round(((cosine + 1) / 2) * 100);
}

const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w200";
const FALLBACK_POSTER = "https://placehold.co/200x300/191a21/a855f7?text=Re:Watch";

const TRAIT_ORDER = [
  "nostalgia", "family", "growth", "comfort", "pacing",
  "hope", "bitter", "humor", "romance", "intensity"
];

// Matches the validated pair in TraitRadar.jsx: purple (primary) vs
// --cyan (#0284c7, secondary) — same color MovieModal uses for its own/movie
// comparison radar, kept consistent across every "your DNA vs X" chart.
const COMPARE_COLOR = "var(--cyan)";

// Below this, a profile is still mostly the neutral 0.5 default (see
// SocialService.dnaMatches for the same threshold) — comparing against it
// would just show "you differ from a flat line", not a real taste signal.
const MIN_RATINGS_FOR_COMPARISON = 3;

function poster(path) {
  if (!path) return FALLBACK_POSTER;
  if (path.startsWith("http")) return path;
  return TMDB_IMAGE_BASE_URL + (path.startsWith("/") ? path : "/" + path);
}

export default function SocialProfile() {
  const { userId } = useParams();
  const navigate = useNavigate();
  const [profile, setprofile] = useState(null);
  const [lists, setlists] = useState([]);
  const [reviews, setreviews] = useState([]);
  const [myProfile, setmyProfile] = useState(null);
  const [loading, setloading] = useState(true);
  const [err, seterr] = useState("");
  const [followBusy, setfollowBusy] = useState(false);
  const [showblockconfirm, setshowblockconfirm] = useState(false);
  const [showreport, setshowreport] = useState(false);
  const [reportSubmitting, setreportSubmitting] = useState(false);
  const [reportDone, setreportDone] = useState(false);

  async function load() {
    setloading(true);
    seterr("");
    const ownUserId = localStorage.getItem("userId");
    const [profileRes, listsRes, reviewsRes, myProfileRes] = await Promise.all([
      fetch(`${BASE}/api/social/profile/${userId}`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/social/${userId}/lists`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/social/${userId}/reviews`, { headers: authHeaders() }).catch(() => null),
      ownUserId
        ? fetch(`${BASE}/api/tastedna/profile/${ownUserId}`, { headers: authHeaders() }).catch(() => null)
        : Promise.resolve(null)
    ]);

    if (profileRes && profileRes.ok) {
      setprofile(await profileRes.json());
    } else {
      seterr("Could not find that profile.");
    }
    if (listsRes && listsRes.ok) setlists(await listsRes.json());
    if (reviewsRes && reviewsRes.ok) setreviews(await reviewsRes.json());
    if (myProfileRes && myProfileRes.ok) setmyProfile(await myProfileRes.json());
    setloading(false);
  }

  useEffect(() => {
    // See EvolutionTimeline.jsx for why this needs no further change despite
    // the linter's static analysis of dependency-triggered fetches.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [userId]);

  async function toggleFollow() {
    if (!profile || followBusy) return;
    setfollowBusy(true);
    const method = profile.isFollowing ? "DELETE" : "POST";
    const res = await fetch(`${BASE}/api/social/follow/${userId}`, {
      method,
      headers: authHeaders()
    }).catch(() => null);
    if (res && res.ok) {
      // Refetch rather than hand-patch isFollowing/followerCount locally —
      // this was previously optimistic-only, which could drift from the
      // server's actual state (e.g. a race with another tab) until the next
      // full page reload. A targeted profile refetch (not the full load(),
      // which would also flash the lists/reviews sections back to loading)
      // keeps this instant-feeling while staying honest to the server.
      const freshRes = await fetch(`${BASE}/api/social/profile/${userId}`, { headers: authHeaders() }).catch(() => null);
      if (freshRes && freshRes.ok) {
        setprofile(await freshRes.json());
      }
    }
    setfollowBusy(false);
  }

  async function confirmBlock() {
    setshowblockconfirm(false);
    await blockUser(userId);
    // Blocking is mutual-hiding — this profile will 404 on any refetch after
    // this, so there's nothing to stay on. Community is the same landing spot
    // used elsewhere after a destructive social action.
    navigate("/community");
  }

  async function submitReport(reason, details) {
    setreportSubmitting(true);
    const res = await fileReport(userId, reason, details);
    setreportSubmitting(false);
    if (res?.status === "success") {
      setshowreport(false);
      setreportDone(true);
    }
  }

  if (loading) {
    return <div className="feed-state">Loading profile...</div>;
  }

  if (!profile) {
    return <div className="feed-state">{err || "Profile not found."}</div>;
  }

  const radarTraits = TRAIT_ORDER.map((key) => ({ key, label: profile.traits?.[key]?.label || key }));
  const radarValues = {};
  TRAIT_ORDER.forEach((key) => { radarValues[key] = profile.traits?.[key]?.val ?? 0.5; });

  const canCompare = !profile.isSelf && (myProfile?.ratingCount ?? 0) >= MIN_RATINGS_FOR_COMPARISON;
  let myValues = null;
  if (canCompare) {
    myValues = {};
    TRAIT_ORDER.forEach((key) => { myValues[key] = myProfile.traits?.[key]?.val ?? 0.5; });
  }

  return (
    <div className="social-profile-page" data-profile-theme={profile.profileTheme || "cinema"}>
      <section className="feed-section">
        <div className="social-profile-header">
          <Avatar username={profile.username} avatarUrl={profile.avatarUrl} avatarFrame={profile.avatarFrame} large size={64} />
          <div>
            <h2 style={{ margin: 0 }}>{profile.username}</h2>
            {profile.nickname && <p className="social-profile-nickname">{profile.nickname}</p>}
            <span className="share-card-badge">{profile.archetype}</span>
            <div className="social-profile-counts">
              <span>{profile.ratingCount} rated</span>
              <span>{profile.followerCount} followers</span>
              <span>{profile.followingCount} following</span>
            </div>
          </div>
          {!profile.isSelf && profile.compatibilityScore != null && (
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <MatchRing score={toMatchPercent(profile.compatibilityScore)} size={40} />
              <span style={{ fontSize: "0.78rem", color: "var(--text-muted)" }}>
                {toMatchPercent(profile.compatibilityScore)}% compatible
              </span>
            </div>
          )}
          {!profile.isSelf && (
            <div style={{ marginLeft: "auto", display: "flex", flexDirection: "column", gap: "6px", alignItems: "flex-end" }}>
              <button
                type="button"
                className={profile.isFollowing ? "btn-block" : "btn-primary btn-block"}
                style={{ maxWidth: "160px" }}
                onClick={toggleFollow}
                disabled={followBusy}
              >
                {profile.isFollowing ? "Following" : "Follow"}
              </button>
              <div style={{ display: "flex", gap: "10px" }}>
                <button
                  type="button"
                  className="auth-toggle-link"
                  style={{ fontSize: "0.76rem", background: "none", border: "none" }}
                  onClick={() => setshowreport(true)}
                >
                  Report
                </button>
                <button
                  type="button"
                  className="auth-toggle-link"
                  style={{ fontSize: "0.76rem", background: "none", border: "none", color: "#f87171" }}
                  onClick={() => setshowblockconfirm(true)}
                >
                  Block
                </button>
              </div>
            </div>
          )}
        </div>
        <p style={{ color: "var(--text-muted)" }}>"{profile.archetypeBlurb}"</p>
        {reportDone && (
          <p className="status-message status-message--success">Report submitted. Thanks for letting us know.</p>
        )}

        <TraitRadar
          traits={radarTraits}
          primary={{ label: profile.username, color: "#a855f7", values: radarValues }}
          secondary={canCompare ? { label: "You", color: COMPARE_COLOR, values: myValues } : undefined}
          size={280}
        />
        {!profile.isSelf && !canCompare && (
          <p style={{ color: "var(--text-faint)", fontSize: "0.78rem", marginTop: "8px" }}>
            Rate a few more titles to compare your own TasteDNA against {profile.username}'s.
          </p>
        )}
      </section>

      {(profile.pinnedTitles?.length > 0 || profile.pinnedReview || profile.pinnedCollection) && (
        <section className="feed-section">
          <p className="section-eyebrow">Pinned</p>

          {profile.pinnedTitles?.length > 0 && (
            <div style={{ marginBottom: "var(--sp-3)" }}>
              <h3 style={{ marginBottom: "8px", fontSize: "1rem" }}>Identity Films</h3>
              <div className="mini-card-grid">
                {profile.pinnedTitles.map((t) => (
                  <article className="mini-card" key={t.titleId}>
                    <div className="mini-card-poster">
                      <img src={poster(t.poster)} alt={`${t.title} poster`} loading="lazy" />
                      <div className="poster-gradient" />
                    </div>
                    <div className="mini-card-body">
                      <h4>{t.title}</h4>
                    </div>
                  </article>
                ))}
              </div>
            </div>
          )}

          {profile.pinnedReview && (
            <div style={{ marginBottom: "var(--sp-3)" }}>
              <h3 style={{ marginBottom: "8px", fontSize: "1rem" }}>Pinned Review</h3>
              <div className="activity-item">
                <img src={poster(profile.pinnedReview.poster)} alt="" className="activity-poster" />
                <div className="activity-body">
                  <div className="activity-meta">
                    <span className="activity-stars">{"★".repeat(profile.pinnedReview.overall || 0)}</span>
                  </div>
                  <div className="activity-title">{profile.pinnedReview.title}</div>
                  <p className="activity-moment">Key moment: {profile.pinnedReview.moment}</p>
                </div>
              </div>
            </div>
          )}

          {profile.pinnedCollection && (
            <div>
              <h3 style={{ marginBottom: "8px", fontSize: "1rem" }}>Pinned List</h3>
              <div className="trait-card" style={{ display: "flex", gap: "var(--sp-2)" }}>
                <CollageCover posters={profile.pinnedCollection.previewPosters} size={56} />
                <div>
                  <span className="trait-name">{profile.pinnedCollection.name}</span>
                  <div className="trait-meta-row">
                    <span>{profile.pinnedCollection.itemCount} title{profile.pinnedCollection.itemCount === 1 ? "" : "s"}</span>
                  </div>
                </div>
              </div>
            </div>
          )}
        </section>
      )}

      {lists.length > 0 && (
        <section className="feed-section">
          <p className="section-eyebrow">Curated shelves</p>
          <h2>{profile.username}'s Lists</h2>
          {lists.map((list) => (
            <div key={list.folderId} style={{ marginBottom: "var(--sp-4)" }}>
              <h3 style={{ marginBottom: "8px" }}>{list.name} <span style={{ color: "var(--text-faint)", fontWeight: 400 }}>({list.itemCount})</span></h3>
              <div className="mini-card-grid">
                {list.items.map((item) => (
                  <article className="mini-card" key={item.titleId}>
                    <div className="mini-card-poster">
                      <img src={poster(item.poster)} alt={`${item.title} poster`} loading="lazy" />
                      <div className="poster-gradient" />
                    </div>
                    <div className="mini-card-body">
                      <h4>{item.title}</h4>
                    </div>
                  </article>
                ))}
              </div>
            </div>
          ))}
        </section>
      )}

      <section className="feed-section">
        <p className="section-eyebrow">Reviews</p>
        <h2>What {profile.username} Thought</h2>
        {reviews.length === 0 ? (
          <EmptyState message="No reviews written yet." />
        ) : (
          <div className="activity-feed">
            {reviews.map((r) => (
              <div className="activity-item" key={r.ratingId}>
                <img src={poster(r.poster)} alt="" className="activity-poster" />
                <div className="activity-body">
                  <div className="activity-meta">
                    <span className="activity-stars">{"★".repeat(r.overall || 0)}</span>
                  </div>
                  <div className="activity-title">{r.title}</div>
                  {/* `moment` is a fixed-option category the rater picked, not
                      free-written text — see Community.jsx for the same fix. */}
                  <p className="activity-moment">Key moment: {r.moment}</p>
                  <ReviewInteractions review={r} />
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {showblockconfirm && (
        <ConfirmDialog
          title={`Block ${profile.username}?`}
          message="You won't see each other's profiles, activity, or DNA matches anymore. You can unblock them later from Settings."
          confirmLabel="Block"
          onConfirm={confirmBlock}
          onCancel={() => setshowblockconfirm(false)}
        />
      )}

      {showreport && (
        <ReportDialog
          username={profile.username}
          submitting={reportSubmitting}
          onSubmit={submitReport}
          onCancel={() => setshowreport(false)}
        />
      )}
    </div>
  );
}

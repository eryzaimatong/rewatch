import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import TraitRadar from "./TraitRadar";
import { authHeaders } from "./auth";
import "./App.css";

const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w200";
const FALLBACK_POSTER = "https://placehold.co/200x300/191a21/a855f7?text=Re:Watch";

const TRAIT_ORDER = [
  "nostalgia", "family", "growth", "comfort", "pacing",
  "hope", "bitter", "humor", "romance", "intensity"
];

function poster(path) {
  if (!path) return FALLBACK_POSTER;
  if (path.startsWith("http")) return path;
  return TMDB_IMAGE_BASE_URL + (path.startsWith("/") ? path : "/" + path);
}

export default function SocialProfile() {
  const { userId } = useParams();
  const [profile, setprofile] = useState(null);
  const [lists, setlists] = useState([]);
  const [reviews, setreviews] = useState([]);
  const [loading, setloading] = useState(true);
  const [err, seterr] = useState("");
  const [followBusy, setfollowBusy] = useState(false);

  async function load() {
    setloading(true);
    seterr("");
    const [profileRes, listsRes, reviewsRes] = await Promise.all([
      fetch(`http://localhost:8080/api/social/profile/${userId}`, { headers: authHeaders() }).catch(() => null),
      fetch(`http://localhost:8080/api/social/${userId}/lists`, { headers: authHeaders() }).catch(() => null),
      fetch(`http://localhost:8080/api/social/${userId}/reviews`, { headers: authHeaders() }).catch(() => null)
    ]);

    if (profileRes && profileRes.ok) {
      setprofile(await profileRes.json());
    } else {
      seterr("Could not find that profile.");
    }
    if (listsRes && listsRes.ok) setlists(await listsRes.json());
    if (reviewsRes && reviewsRes.ok) setreviews(await reviewsRes.json());
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
    const res = await fetch(`http://localhost:8080/api/social/follow/${userId}`, {
      method,
      headers: authHeaders()
    }).catch(() => null);
    if (res && res.ok) {
      setprofile((p) => ({
        ...p,
        isFollowing: !p.isFollowing,
        followerCount: p.followerCount + (p.isFollowing ? -1 : 1)
      }));
    }
    setfollowBusy(false);
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

  return (
    <div>
      <section className="feed-section">
        <div className="social-profile-header">
          <div className="dna-match-avatar dna-match-avatar--large">
            {profile.username.slice(0, 1).toUpperCase()}
          </div>
          <div>
            <h2 style={{ margin: 0 }}>{profile.username}</h2>
            <span className="share-card-badge">{profile.archetype}</span>
            <div className="social-profile-counts">
              <span>{profile.ratingCount} rated</span>
              <span>{profile.followerCount} followers</span>
              <span>{profile.followingCount} following</span>
            </div>
          </div>
          {!profile.isSelf && (
            <button
              type="button"
              className={profile.isFollowing ? "btn-block" : "btn-primary btn-block"}
              style={{ marginLeft: "auto", maxWidth: "160px" }}
              onClick={toggleFollow}
              disabled={followBusy}
            >
              {profile.isFollowing ? "Following" : "Follow"}
            </button>
          )}
        </div>
        <p style={{ color: "var(--text-muted)" }}>"{profile.archetypeBlurb}"</p>

        <TraitRadar
          traits={radarTraits}
          primary={{ label: profile.username, color: "#a855f7", values: radarValues }}
          size={280}
        />
      </section>

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
          <div className="feed-state">No reviews written yet.</div>
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
                  <p className="activity-moment">"{r.moment}"</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

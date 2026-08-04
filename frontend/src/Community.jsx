import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { authHeaders } from "./auth";
import "./App.css";

const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w200";
const FALLBACK_POSTER = "https://placehold.co/200x300/191a21/a855f7?text=Re:Watch";

function poster(path) {
  if (!path) return FALLBACK_POSTER;
  if (path.startsWith("http")) return path;
  return TMDB_IMAGE_BASE_URL + (path.startsWith("/") ? path : "/" + path);
}

function formatWhen(iso) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return d.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export default function Community() {
  const userid = localStorage.getItem("userId");
  const [matches, setmatches] = useState([]);
  const [feed, setfeed] = useState([]);
  const [loading, setloading] = useState(true);
  const [err, seterr] = useState("");

  async function load() {
    setloading(true);
    seterr("");
    const [matchesRes, feedRes] = await Promise.all([
      fetch(`http://localhost:8080/api/social/dna-matches/${userid}`, { headers: authHeaders() }).catch(() => null),
      fetch(`http://localhost:8080/api/social/${userid}/activity-feed`, { headers: authHeaders() }).catch(() => null)
    ]);
    if (matchesRes && matchesRes.ok) {
      setmatches(await matchesRes.json());
    }
    if (feedRes && feedRes.ok) {
      setfeed(await feedRes.json());
    } else {
      seterr("Could not load your community feed right now.");
    }
    setloading(false);
  }

  useEffect(() => {
    // See EvolutionTimeline.jsx for why this needs no further change despite
    // the linter's static analysis of mount-time fetches that set state.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, []);

  if (loading) {
    return <div className="feed-state">Reading the room...</div>;
  }

  return (
    <div>
      <section className="feed-section">
        <p className="section-eyebrow">People like you</p>
        <h2>Similar TasteDNA</h2>
        <p style={{ color: "var(--text-muted)", marginTop: "-8px", marginBottom: "var(--sp-3)" }}>
          Ranked by how closely their whole TasteDNA shape matches yours — the same vector math behind your recommendations, applied person to person.
        </p>

        {err && <div className="status-message status-message--error">{err}</div>}

        {matches.length === 0 ? (
          <div className="feed-state">
            Rate a few more titles to unlock DNA matches — at least 3 ratings are needed to compare shapes meaningfully.
          </div>
        ) : (
          <div className="dna-match-grid">
            {matches.map((m) => (
              <Link to={`/social/${m.userId}`} className="dna-match-card" key={m.userId}>
                <div className="dna-match-avatar">{m.username.slice(0, 1).toUpperCase()}</div>
                <div>
                  <div className="dna-match-name">{m.username}</div>
                  <div className="dna-match-archetype">{m.archetype}</div>
                </div>
                {m.following && <span className="dna-match-following-badge">Following</span>}
              </Link>
            ))}
          </div>
        )}
      </section>

      <section className="feed-section">
        <p className="section-eyebrow">Activity</p>
        <h2>From People You Follow</h2>

        {feed.length === 0 ? (
          <div className="feed-state">
            Follow someone from their public profile to see what they're watching here.
          </div>
        ) : (
          <div className="activity-feed">
            {feed.map((item) => (
              <div className="activity-item" key={item.ratingId}>
                <img src={poster(item.poster)} alt="" className="activity-poster" />
                <div className="activity-body">
                  <div className="activity-meta">
                    <Link to={`/social/${item.userId}`} className="activity-username">{item.username}</Link>
                    <span className="activity-stars">{"★".repeat(item.overall || 0)}</span>
                    <span className="activity-when">{formatWhen(item.createdAt)}</span>
                  </div>
                  <div className="activity-title">{item.title}</div>
                  <p className="activity-moment">"{item.moment}"</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

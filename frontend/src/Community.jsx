import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { authHeaders } from "./auth";
import { BASE, searchUsers, followUser, unfollowUser, discoverCollections, followCollection, unfollowCollection } from "./api";
import EmptyState from "./EmptyState";
import CollageCover from "./CollageCover";
import { SkeletonPersonGrid } from "./Skeleton";
import MatchRing from "./MatchRing";
import Avatar from "./Avatar";
import ReviewInteractions from "./ReviewInteractions";
import "./App.css";

// dnaMatches() sends raw centred-cosine similarity in [-1, 1]; MatchRing
// expects a 0-100 score, same scale as the movie-match rings elsewhere.
function toMatchPercent(cosine) {
  return Math.round(((cosine + 1) / 2) * 100);
}

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

function PersonCard({ user, onToggleFollow, busy, showMatch }) {
  return (
    <div className="dna-match-card">
      <Link to={`/social/${user.userId}`} style={{ display: "flex", alignItems: "center", gap: "12px", flex: 1, minWidth: 0 }}>
        <Avatar username={user.username} avatarUrl={user.avatarUrl} />
        <div style={{ minWidth: 0 }}>
          <div className="dna-match-name">{user.username}</div>
          {user.archetype && <div className="dna-match-archetype">{user.archetype}</div>}
        </div>
      </Link>
      {showMatch && (
        <div style={{ flex: "0 0 auto" }}>
          <MatchRing score={toMatchPercent(user.matchScore)} size={32} />
        </div>
      )}
      <button
        type="button"
        className={`pill${user.following ? " active" : ""}`}
        onClick={() => onToggleFollow(user)}
        disabled={busy}
        style={{ flex: "0 0 auto" }}
      >
        {busy ? "..." : user.following ? "Following" : "Follow"}
      </button>
    </div>
  );
}

export default function Community() {
  const userid = localStorage.getItem("userId");
  const [matches, setmatches] = useState([]);
  const [feed, setfeed] = useState([]);
  const [collections, setcollections] = useState([]);
  const [loading, setloading] = useState(true);
  const [err, seterr] = useState("");
  const [collectionBusyIds, setcollectionBusyIds] = useState(new Set());

  const [searchquery, setsearchquery] = useState("");
  const [searchresults, setsearchresults] = useState([]);
  const [searchloading, setsearchloading] = useState(false);
  const [searched, setsearched] = useState(false);
  const [busyIds, setbusyIds] = useState(new Set());

  async function load() {
    setloading(true);
    seterr("");
    const [matchesRes, feedRes] = await Promise.all([
      fetch(`${BASE}/api/social/dna-matches/${userid}`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/social/${userid}/activity-feed`, { headers: authHeaders() }).catch(() => null)
    ]);
    if (matchesRes && matchesRes.ok) {
      setmatches(await matchesRes.json());
    }
    if (feedRes && feedRes.ok) {
      setfeed(await feedRes.json());
    } else {
      seterr("Could not load your community feed right now.");
    }
    setcollections(await discoverCollections());
    setloading(false);
  }

  useEffect(() => {
    // See EvolutionTimeline.jsx for why this needs no further change despite
    // the linter's static analysis of mount-time fetches that set state.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, []);

  async function handlesearch(e) {
    e.preventDefault();
    if (!searchquery.trim()) return;
    setsearchloading(true);
    setsearched(true);
    setsearchresults(await searchUsers(searchquery.trim()));
    setsearchloading(false);
  }

  async function togglefollow(user) {
    setbusyIds((current) => new Set(current).add(user.userId));
    const res = user.following ? await unfollowUser(user.userId) : await followUser(user.userId);
    if (res?.status === "success") {
      const nowFollowing = !user.following;
      // Search results aren't refetched from anywhere else, so they still
      // get the local patch. The DNA-match list is refetched instead of
      // hand-patched — the previous optimistic-only patch could drift from
      // server truth (e.g. a race with another tab) until a full reload.
      // A targeted refetch (not the full load(), which would also flash the
      // whole page back to its skeleton) keeps this feeling instant.
      setsearchresults((list) => list.map((u) => (u.userId === user.userId ? { ...u, following: nowFollowing } : u)));
      const matchesRes = await fetch(`${BASE}/api/social/dna-matches/${userid}`, { headers: authHeaders() }).catch(() => null);
      if (matchesRes && matchesRes.ok) {
        setmatches(await matchesRes.json());
      }
    }
    setbusyIds((current) => {
      const next = new Set(current);
      next.delete(user.userId);
      return next;
    });
  }

  async function togglecollectionfollow(collection) {
    setcollectionBusyIds((current) => new Set(current).add(collection.folderId));
    const res = collection.isFollowing
      ? await unfollowCollection(collection.folderId)
      : await followCollection(collection.folderId);
    if (res?.status === "success") {
      setcollections((list) => list.map((c) => (
        c.folderId === collection.folderId
          ? { ...c, isFollowing: res.following, followerCount: c.followerCount + (res.following ? 1 : -1) }
          : c
      )));
    }
    setcollectionBusyIds((current) => {
      const next = new Set(current);
      next.delete(collection.folderId);
      return next;
    });
  }

  if (loading) {
    return (
      <div>
        <section className="feed-section">
          <p className="section-eyebrow">Find people</p>
          <h2>Search by Username</h2>
        </section>
        <section className="feed-section">
          <p className="section-eyebrow">People like you</p>
          <h2>Similar TasteDNA</h2>
          <SkeletonPersonGrid count={3} />
        </section>
      </div>
    );
  }

  return (
    <div>
      <section className="feed-section">
        <p className="section-eyebrow">Find people</p>
        <h2>Search by Username</h2>
        <form onSubmit={handlesearch} style={{ display: "flex", gap: "10px", marginBottom: "var(--sp-2)" }}>
          <input
            type="text"
            placeholder="Search for a friend's username"
            value={searchquery}
            onChange={(e) => setsearchquery(e.target.value)}
            style={{ margin: 0 }}
          />
          <button type="submit" className="btn-primary" style={{ flex: "0 0 auto" }} disabled={searchloading}>
            {searchloading ? "..." : "Search"}
          </button>
        </form>

        {searched && !searchloading && searchresults.length === 0 && (
          <EmptyState message="No one found with that username." />
        )}

        {searchresults.length > 0 && (
          <div className="dna-match-grid">
            {searchresults.map((u) => (
              <PersonCard key={u.userId} user={u} onToggleFollow={togglefollow} busy={busyIds.has(u.userId)} />
            ))}
          </div>
        )}
      </section>

      <section className="feed-section">
        <p className="section-eyebrow">People like you</p>
        <h2>Similar TasteDNA</h2>
        <p style={{ color: "var(--text-muted)", marginTop: "-8px", marginBottom: "var(--sp-3)" }}>
          Ranked by how closely their whole TasteDNA shape matches yours — the same vector math behind your recommendations, applied person to person.
        </p>

        {err && <div className="status-message status-message--error">{err}</div>}

        {matches.length === 0 ? (
          <EmptyState message="Rate a few more titles to unlock DNA matches — at least 3 ratings are needed to compare shapes meaningfully." />
        ) : (
          <div className="dna-match-grid">
            {matches.map((m) => (
              <PersonCard key={m.userId} user={m} onToggleFollow={togglefollow} busy={busyIds.has(m.userId)} showMatch />
            ))}
          </div>
        )}
      </section>

      <section className="feed-section">
        <p className="section-eyebrow">Discover</p>
        <h2>Collections</h2>
        <p style={{ color: "var(--text-muted)", marginTop: "-8px", marginBottom: "var(--sp-3)" }}>
          Public shelves other people have curated and made shareable — follow one to keep it in your own Watchlist page.
        </p>

        {collections.length === 0 ? (
          <EmptyState message="No public collections yet — be the first: make a watchlist shelf public from your Watchlist page." />
        ) : (
          <div className="trait-list">
            {collections.map((c) => (
              <div key={c.folderId} className="trait-card" style={{ display: "flex", gap: "var(--sp-2)" }}>
                <CollageCover posters={c.previewPosters} size={56} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className="trait-card-row">
                    <Link to={`/social/${c.ownerUserId}`} className="trait-name" style={{ color: "var(--text)" }}>
                      {c.name} <span style={{ color: "var(--text-faint)", fontWeight: 400 }}>by {c.ownerUsername}</span>
                    </Link>
                    <button
                      type="button"
                      className={`pill${c.isFollowing ? " active" : ""}`}
                      onClick={() => togglecollectionfollow(c)}
                      disabled={collectionBusyIds.has(c.folderId)}
                    >
                      {collectionBusyIds.has(c.folderId) ? "..." : c.isFollowing ? "Following" : "Follow"}
                    </button>
                  </div>
                  <div className="trait-meta-row">
                    <span>{c.itemCount} title{c.itemCount === 1 ? "" : "s"} · {c.followerCount} follower{c.followerCount === 1 ? "" : "s"}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="feed-section">
        <p className="section-eyebrow">Activity</p>
        <h2>From People You Follow</h2>

        {feed.length === 0 ? (
          <EmptyState message="Follow someone from their public profile to see what they're watching here." />
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
                  {/* `moment` is a fixed-option category the rater picked ("Which
                      moment stayed with you the most?"), not free-written text —
                      rendering it in quotes as if it were a verbatim excerpt was
                      misleading. */}
                  <p className="activity-moment">Key moment: {item.moment}</p>
                  <ReviewInteractions review={item} />
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

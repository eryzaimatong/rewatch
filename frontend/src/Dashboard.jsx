import { useState, useEffect } from "react";
import { gettastedna, BASE } from "./api";
import { authHeaders } from "./auth";
import MatchRing from "./MatchRing";
import MovieModal from "./MovieModal";
import EvolutionTimeline from "./EvolutionTimeline";
import ConfirmDialog from "./ConfirmDialog";
import UndoToast from "./UndoToast";
import "./App.css";

const UNDO_WINDOW_MS = 5000;

const FALLBACK_POSTER = "https://placehold.co/500x750/191a21/a855f7?text=Re:Watch";
const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

const TRAIT_ORDER = [
  "nostalgia", "family", "growth", "comfort", "pacing",
  "hope", "bitter", "humor", "romance", "intensity"
];

function resolveposter(movie) {
  if (movie.posterUrl && movie.posterUrl.startsWith("http")) {
    return movie.posterUrl;
  }
  const path = movie.posterPath || movie.poster;
  if (typeof path === "string" && path.startsWith("http")) {
    return path;
  }
  if (typeof path === "string" && path.trim()) {
    return TMDB_IMAGE_BASE_URL + (path.startsWith("/") ? path : "/" + path);
  }
  return FALLBACK_POSTER;
}

function MiniCard({ movie, onOpen, action }) {
  return (
    <article className="mini-card">
      <button type="button" className="mini-card-poster" onClick={() => onOpen(movie)}>
        <img
          src={resolveposter(movie)}
          alt={`${movie.title} poster`}
          loading="lazy"
          onError={(e) => {
            e.currentTarget.onerror = null;
            e.currentTarget.src = FALLBACK_POSTER;
          }}
        />
        <div className="poster-gradient" />
        <div className="match-ring-badge">
          <MatchRing score={movie.matchScore} size={36} />
        </div>
      </button>
      <div className="mini-card-body">
        <h4>{movie.title}</h4>
        {action}
      </div>
    </article>
  );
}

export default function Dashboard() {
  const [items, setitems] = useState([]);
  const [folders, setfolders] = useState([]);
  const [activefolder, setactivefolder] = useState("all");
  const [newfoldername, setnewfoldername] = useState("");
  const [showfolderinput, setshowfolderinput] = useState(false);

  const [loading, setloading] = useState(true);
  const [errmsg, seterrmsg] = useState("");
  const [archetype, setarchetype] = useState("Emotional Storyteller");
  const [archetypeBlurb, setarchetypeBlurb] = useState("");
  const [confidence, setconfidence] = useState(0);
  const [ratedcount, setratedcount] = useState(0);
  const [personalized, setpersonalized] = useState(false);
  const [traits, settraits] = useState({});

  const [todayspick, settodayspick] = useState(null);
  const [discoveries, setdiscoveries] = useState([]);
  const [selectedmovie, setselectedmovie] = useState(null);

  const [confirmingRemoveId, setconfirmingRemoveId] = useState(null);
  const [pendingRemoval, setpendingRemoval] = useState(null); // { item, timerId }

  const username = localStorage.getItem("username") || "friend";
  const userid = localStorage.getItem("userId");

  async function loadprofiledata() {
    setloading(true);
    seterrmsg("");

    if (!userid) {
      seterrmsg("Log in to see your dashboard.");
      setloading(false);
      return;
    }

    const dnaprofile = await gettastedna(userid);
    if (dnaprofile) {
      setarchetype(dnaprofile.archetype || "Emotional Storyteller");
      setarchetypeBlurb(dnaprofile.archetypeBlurb || "");
      setconfidence(Math.round((dnaprofile.meanConfidence || 0) * 100));
      setratedcount(dnaprofile.ratingCount || 0);
      setpersonalized(!!dnaprofile.personalized);
      settraits(dnaprofile.traits || {});
    }

    const [watchlistRes, foldersRes, recsRes, gemsRes] = await Promise.all([
      fetch(`${BASE}/api/watchlist/${userid}`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/watchlist/${userid}/folders`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/recommendations/${userid}?limit=1`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/discovery/hidden-gems/${userid}?limit=4`, { headers: authHeaders() }).catch(() => null)
    ]);

    if (watchlistRes && watchlistRes.ok) {
      setitems(await watchlistRes.json());
    } else {
      seterrmsg("Could not load your watchlist right now.");
    }

    if (foldersRes && foldersRes.ok) {
      setfolders(await foldersRes.json());
    }

    if (recsRes && recsRes.ok) {
      const recs = await recsRes.json();
      settodayspick(Array.isArray(recs) && recs.length > 0 ? recs[0] : null);
    }

    if (gemsRes && gemsRes.ok) {
      setdiscoveries(await gemsRes.json());
    }

    setloading(false);
  }

  useEffect(() => {
    // Standard mount-time fetch; see EvolutionTimeline.jsx for why this
    // pattern needs no further change despite the linter's static analysis.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadprofiledata();
  }, []);

  // WatchlistItemDTO.id is the watchlist row's own id — distinct from the
  // tmdbId MovieModal expects as `movie.id` (see MovieFeed.jsx's normalizeMovie
  // for the same convention). Remap before opening, or the modal's rate/match
  // calls would silently target the wrong thing.
  function openWatchlistItem(item) {
    setselectedmovie({ ...item, id: item.tmdbId ?? item.titleId });
  }

  function requestremove(itemId) {
    setconfirmingRemoveId(itemId);
  }

  // Optimistic remove with a delayed commit: the item disappears immediately
  // and an undo toast appears; the actual DELETE only fires if the toast
  // expires without Undo being clicked. No backend soft-delete needed for this.
  function confirmremove() {
    const itemId = confirmingRemoveId;
    setconfirmingRemoveId(null);
    if (!itemId) return;

    const item = items.find((i) => i.id === itemId);
    if (!item) return;

    // Only one pending-removal slot: removing a second item while the first's
    // undo window is still open flushes the first immediately rather than
    // juggling multiple timers.
    if (pendingRemoval) {
      clearTimeout(pendingRemoval.timerId);
      doRemoveOnBackend(pendingRemoval.item.id);
    }

    setitems((current) => current.filter((i) => i.id !== itemId));

    const timerId = setTimeout(() => {
      doRemoveOnBackend(itemId);
      setpendingRemoval(null);
    }, UNDO_WINDOW_MS);

    setpendingRemoval({ item, timerId });
  }

  function undoremove() {
    if (!pendingRemoval) return;
    clearTimeout(pendingRemoval.timerId);
    setitems((current) => [pendingRemoval.item, ...current]);
    setpendingRemoval(null);
  }

  async function doRemoveOnBackend(itemId) {
    if (!userid) return;
    // No rollback UI on failure — matches this component's existing
    // error-handling depth for background mutations (moveitem/createfolder
    // also don't surface a failure message).
    await fetch(`${BASE}/api/watchlist/items/${itemId}?userId=${userid}`, {
      method: "DELETE",
      headers: authHeaders()
    }).catch(() => null);
  }

  async function moveitem(itemId, folderId) {
    if (!userid) return;
    const res = await fetch(`${BASE}/api/watchlist/items/${itemId}/folder`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ userId: userid, folderId })
    }).catch(() => null);
    if (res && res.ok) {
      const updated = await res.json();
      setitems((current) => current.map((item) => (item.id === itemId ? updated : item)));
    }
  }

  async function createfolder() {
    if (!userid || !newfoldername.trim()) return;
    const res = await fetch(`${BASE}/api/watchlist/folders`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({ userId: userid, name: newfoldername.trim() })
    }).catch(() => null);
    if (res && res.ok) {
      const folder = await res.json();
      setfolders((current) => (current.some((f) => f.id === folder.id) ? current : [...current, folder]));
      setnewfoldername("");
      setshowfolderinput(false);
    }
  }

  const visibleitems = activefolder === "all"
    ? items
    : items.filter((item) => item.folderId === activefolder);

  const traitList = TRAIT_ORDER
    .filter((key) => traits[key])
    .map((key) => ({ key, label: traits[key].label, trend: traits[key].trend, val: traits[key].val }));
  const rising = traitList.filter((t) => t.trend > 0).slice(0, 2);
  const falling = traitList.filter((t) => t.trend < 0).slice(0, 2);
  const topThreeKeys = [...traitList].sort((a, b) => b.val - a.val).slice(0, 3).map((t) => t.key);

  return (
    <div className="page-shell page-shell--wide">
      <div className="page-panel" style={{ marginBottom: "var(--sp-4)" }}>
        <span className="eyebrow">Storytelling Dashboard</span>
        <h1 style={{ fontSize: "1.9rem", fontWeight: "var(--fw-hero)", margin: "8px 0 var(--sp-2)" }}>
          Hi {username}
        </h1>

        <div className="stat-grid">
          <div className="stat-card">
            <span className="stat-label">Active Archetype</span>
            <strong className="stat-value stat-value--accent">{archetype}</strong>
          </div>

          <div className="stat-card">
            <span className="stat-label">TasteDNA Confidence</span>
            <strong className="stat-value">{personalized ? `${confidence}%` : "Not yet rated"}</strong>
          </div>

          <div className="stat-card">
            <span className="stat-label">Stories Rated</span>
            <strong className="stat-value">{ratedcount}</strong>
          </div>

          <div className="stat-card">
            <span className="stat-label">Watchlist Queue</span>
            <strong className="stat-value">{items.length} Saved</strong>
          </div>
        </div>

        {archetypeBlurb && (
          <p style={{ marginTop: "var(--sp-2)", marginBottom: 0, fontSize: "0.85rem" }}>
            {archetypeBlurb}
          </p>
        )}

        {(rising.length > 0 || falling.length > 0) && (
          <div className="mood-trend-row">
            {rising.map((t) => (
              <span key={t.key} className="mood-trend-chip mood-trend-chip--up">↑ {t.label}</span>
            ))}
            {falling.map((t) => (
              <span key={t.key} className="mood-trend-chip mood-trend-chip--down">↓ {t.label}</span>
            ))}
          </div>
        )}
      </div>

      {todayspick && (
        <div className="page-panel today-pick" style={{ marginBottom: "var(--sp-4)" }}>
          <span className="eyebrow">Today's Pick</span>
          <div className="today-pick-body">
            <button type="button" className="today-pick-poster" onClick={() => setselectedmovie(todayspick)}>
              <img
                src={resolveposter(todayspick)}
                alt={`${todayspick.title} poster`}
                onError={(e) => { e.currentTarget.onerror = null; e.currentTarget.src = FALLBACK_POSTER; }}
              />
            </button>
            <div style={{ flex: 1 }}>
              <h2 style={{ margin: "0 0 6px", fontSize: "1.3rem" }}>{todayspick.title}</h2>
              <p style={{ fontSize: "0.85rem", marginBottom: "var(--sp-2)" }}>
                {todayspick.explanation?.summary || todayspick.synopsis}
              </p>
              <div style={{ display: "flex", alignItems: "center", gap: "var(--sp-2)" }}>
                <MatchRing score={todayspick.matchScore} />
                <button type="button" className="btn-primary" onClick={() => setselectedmovie(todayspick)}>
                  See Why
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {personalized && (
        <div style={{ marginBottom: "var(--sp-4)" }}>
          <EvolutionTimeline userId={userid} allTraits={traitList} defaultKeys={topThreeKeys} />
        </div>
      )}

      {discoveries.length > 0 && (
        <div style={{ marginBottom: "var(--sp-5)" }}>
          <p className="section-eyebrow">Off the Beaten Path</p>
          <h2 style={{ fontSize: "1.4rem", margin: "0 0 var(--sp-2)" }}>Discover Something New</h2>
          <div className="mini-card-grid">
            {discoveries.map((m) => (
              <MiniCard key={m.id} movie={m} onOpen={setselectedmovie} />
            ))}
          </div>
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "var(--sp-1)", marginBottom: "var(--sp-2)" }}>
        <h2 style={{ fontSize: "1.4rem", fontWeight: "var(--fw-section)", margin: 0 }}>
          Your Watchlist
        </h2>

        <div className="pill-row">
          <button
            type="button"
            className={`pill${activefolder === "all" ? " active" : ""}`}
            onClick={() => setactivefolder("all")}
          >
            All
          </button>
          {folders.map((f) => (
            <button
              key={f.id}
              type="button"
              className={`pill${activefolder === f.id ? " active" : ""}`}
              onClick={() => setactivefolder(f.id)}
            >
              {f.name}
            </button>
          ))}
          {showfolderinput ? (
            <span style={{ display: "inline-flex", gap: "6px" }}>
              <input
                type="text"
                placeholder="Shelf name"
                value={newfoldername}
                onChange={(e) => setnewfoldername(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && createfolder()}
                style={{ margin: 0, padding: "6px 10px", width: "140px", borderRadius: "999px" }}
                autoFocus
              />
              <button type="button" className="pill" onClick={createfolder}>Add</button>
            </span>
          ) : (
            <button type="button" className="pill" onClick={() => setshowfolderinput(true)}>+ New Shelf</button>
          )}
        </div>
      </div>

      {loading && (
        <div className="feed-state">
          <div className="loading-orb" />
          <p>Loading your saved queue...</p>
        </div>
      )}

      {!loading && errmsg && (
        <div className="feed-state feed-error">
          <h3>Could not load watchlist</h3>
          <p>{errmsg}</p>
          <button type="button" onClick={loadprofiledata}>Try again</button>
        </div>
      )}

      {!loading && !errmsg && visibleitems.length === 0 && (
        <div className="feed-state">
          <h3>Let's build your comfort shelf.</h3>
          <p>Save a few titles from the Home feed and they'll show up here.</p>
        </div>
      )}

      {!loading && visibleitems.length > 0 && (
        <div className="recommendation-grid">
          {visibleitems.map((item) => (
            <article className="recommendation-card" key={item.id}>
              <button
                type="button"
                className="recommendation-poster"
                style={{ border: "none", padding: 0, width: "100%", display: "block" }}
                onClick={() => openWatchlistItem(item)}
              >
                <img
                  src={resolveposter(item)}
                  alt={`${item.title} poster`}
                  loading="lazy"
                  onError={(e) => { e.currentTarget.onerror = null; e.currentTarget.src = FALLBACK_POSTER; }}
                />
                <div className="poster-gradient" />
                <div className="match-ring-badge">
                  <MatchRing score={item.matchScore} />
                </div>
                <div className="poster-metadata">
                  <span>{item.year || "—"}</span>
                  {item.folderName && <span>{item.folderName}</span>}
                </div>
              </button>

              <div className="recommendation-body">
                <h3>{item.title}</h3>

                <p className="movie-synopsis">
                  {item.synopsis || "Saved to your personal storytelling queue."}
                </p>

                <div className="auth-field" style={{ marginBottom: "10px" }}>
                  <select
                    value={item.folderId ?? ""}
                    onChange={(e) => moveitem(item.id, e.target.value ? Number(e.target.value) : null)}
                    style={{ fontSize: "0.78rem", padding: "8px 10px" }}
                  >
                    <option value="">Unsorted</option>
                    {folders.map((f) => (
                      <option key={f.id} value={f.id}>{f.name}</option>
                    ))}
                  </select>
                </div>

                <div className="movie-actions">
                  <button
                    type="button"
                    className="trailer-button"
                    onClick={() => {
                      const q = encodeURIComponent(`${item.title} official trailer`);
                      window.open(`https://www.youtube.com/results?search_query=${q}`, "_blank");
                    }}
                  >
                    ▶ Watch Trailer
                  </button>
                  <button type="button" className="details-button" onClick={() => requestremove(item.id)}>
                    Remove
                  </button>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}

      {selectedmovie && (
        <MovieModal movie={selectedmovie} onClose={() => setselectedmovie(null)} />
      )}

      {confirmingRemoveId && (
        <ConfirmDialog
          title="Remove this from your watchlist?"
          message="You'll have a few seconds to undo this after removing."
          confirmLabel="Remove"
          onConfirm={confirmremove}
          onCancel={() => setconfirmingRemoveId(null)}
        />
      )}

      {pendingRemoval && (
        <UndoToast message={`Removed "${pendingRemoval.item.title}"`} onUndo={undoremove} />
      )}
    </div>
  );
}

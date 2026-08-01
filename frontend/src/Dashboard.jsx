import { useState, useEffect } from "react";
import { gettitles, gettastedna } from "./api";
import "./App.css";

const POSTERS = {
  "Interstellar": "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
  "Parasite": "https://image.tmdb.org/t/p/w500/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg",
  "Arrival": "https://image.tmdb.org/t/p/w500/x2FJsf1ElAgr63Y3PNPtJrcmpoe.jpg",
  "Reply 1988": "https://image.tmdb.org/t/p/w500/3EwozHAGKvg8q4R6M33A9fI5Ggg.jpg",
  "Hospital Playlist": "https://image.tmdb.org/t/p/w500/vGk4SffgokMWhK2xS2786BnpwPj.jpg",
  "Our Beloved Summer": "https://image.tmdb.org/t/p/w500/w70rP9c7B8H7S8h0P0z5S7S0K0.jpg",
  "Twenty Five Twenty One": "https://image.tmdb.org/t/p/w500/eWeuKntQ1x4S9H6VbQ7Z9xV3bM0.jpg",
  "My Liberation Notes": "https://image.tmdb.org/t/p/w500/xR4aL3S5Z6J1qA5Vv9W8kG9qQ0n.jpg",
  "The Glory": "https://image.tmdb.org/t/p/w500/6jOqZ9A1A1u7WwS6A1Bw7V9S2E.jpg",
  "La La Land": "https://image.tmdb.org/t/p/w500/uDO8zWDhfWwoFdKS4fzkUJt0Rf0.jpg"
};

const FALLBACK_POSTER = "https://placehold.co/500x750/191a21/a855f7?text=Re:Watch";

function resolveposter(movie) {
  const title = typeof movie === "string" ? movie : movie.title;
  if (POSTERS[title]) {
    return POSTERS[title];
  }
  if (movie.posterUrl && movie.posterUrl.startsWith("http")) {
    return movie.posterUrl;
  }
  if (movie.posterPath) {
    const path = movie.posterPath.startsWith("/") ? movie.posterPath : "/" + movie.posterPath;
    return "https://image.tmdb.org/t/p/w500" + path;
  }
  return FALLBACK_POSTER;
}

export default function Dashboard() {
  const [watchlist, setwatchlist] = useState([]);
  const [loading, setloading] = useState(true);
  const [errmsg, seterrmsg] = useState("");
  const [archetype, setarchetype] = useState("Emotional Storyteller");

  const username = localStorage.getItem("username") || "friend";
  const userid = localStorage.getItem("userId");

  useEffect(() => {
    loadprofiledata();
  }, []);

  async function loadprofiledata() {
    setloading(true);
    seterrmsg("");

    if (userid) {
      const dnaprofile = await gettastedna(userid);
      if (dnaprofile) {
        if (dnaprofile.sadness > 70) {
          setarchetype("Night Thinker 🌙");
        } else if (dnaprofile.comfort > 70) {
          setarchetype("Cozy Romantic ✨");
        } else if (dnaprofile.slowBurn > 70 || dnaprofile.slowburn > 70) {
          setarchetype("Slow Burn Connoisseur ⏳");
        }
      }
    }

    const alltitles = await gettitles();
    if (alltitles && alltitles.length > 0) {
      const saved = [];
      const count = Math.min(alltitles.length, 4);
      for (let i = 0; i < count; i++) {
        const item = alltitles[i];
        if (typeof item === "string") {
          saved.push({
            id: "watch-" + i,
            title: item,
            year: 2024,
            synopsis: "Saved to your personal storytelling queue.",
            matchScore: 92
          });
        } else {
          saved.push(item);
        }
      }
      setwatchlist(saved);
    } else {
      seterrmsg("couldnt load your watchlist bro");
    }

    setloading(false);
  }

  function removeitem(id) {
    const updated = [];
    for (let i = 0; i < watchlist.length; i++) {
      if (watchlist[i].id !== id) {
        updated.push(watchlist[i]);
      }
    }
    setwatchlist(updated);
  }

  return (
    <div style={{ maxWidth: "1180px", margin: "0 auto", paddingBottom: "70px" }}>
      {/* PROFILE STATS BANNER */}
      <div 
        style={{
          background: "linear-gradient(135deg, rgba(20, 21, 26, 0.95) 0%, rgba(35, 15, 60, 0.85) 100%)",
          border: "1px solid rgba(168, 85, 247, 0.25)",
          borderRadius: "28px",
          padding: "36px",
          marginBottom: "36px",
          boxShadow: "0 20px 50px rgba(0,0,0,0.4)"
        }}
      >
        <span style={{ fontSize: "12px", fontWeight: "800", textTransform: "uppercase", letterSpacing: "2px", color: "#a855f7" }}>
          STORYTELLING DASHBOARD
        </span>
        <h1 style={{ fontSize: "34px", fontWeight: "800", margin: "8px 0 16px 0", color: "#fff" }}>
          Hi {username} 👋
        </h1>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "16px", marginTop: "20px" }}>
          <div style={{ background: "rgba(11, 12, 16, 0.6)", border: "1px solid rgba(255, 255, 255, 0.08)", padding: "18px", borderRadius: "16px" }}>
            <span style={{ fontSize: "12px", color: "#a1a1aa", display: "block", marginBottom: "4px" }}>Active Archetype</span>
            <strong style={{ fontSize: "18px", color: "#c084fc" }}>{archetype}</strong>
          </div>
          <div style={{ background: "rgba(11, 12, 16, 0.6)", border: "1px solid rgba(255, 255, 255, 0.08)", padding: "18px", borderRadius: "16px" }}>
            <span style={{ fontSize: "12px", color: "#a1a1aa", display: "block", marginBottom: "4px" }}>Watchlist Queue</span>
            <strong style={{ fontSize: "18px", color: "#fff" }}>{watchlist.length} Titles Saved</strong>
          </div>
          <div style={{ background: "rgba(11, 12, 16, 0.6)", border: "1px solid rgba(255, 255, 255, 0.08)", padding: "18px", borderRadius: "16px" }}>
            <span style={{ fontSize: "12px", color: "#a1a1aa", display: "block", marginBottom: "4px" }}>Favorite Vibe</span>
            <strong style={{ fontSize: "18px", color: "#fff" }}>Bittersweet Drama</strong>
          </div>
        </div>
      </div>

      {/* WATCHLIST SECTION */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "20px" }}>
        <h2 style={{ fontSize: "24px", fontWeight: "800", margin: 0, color: "#fff" }}>
          📌 Your Watchlist
        </h2>
        <span style={{ fontSize: "13px", color: "#a1a1aa" }}>Ready for tonight</span>
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

      {!loading && watchlist.length === 0 && (
        <div className="feed-state">
          <h3>Your watchlist is empty bro.</h3>
          <p>Head over to the Discover tab or Home feed and save a few titles first.</p>
        </div>
      )}

      {!loading && watchlist.length > 0 && (
        <div className="recommendation-grid">
          {watchlist.map((m, idx) => {
            const title = typeof m === "string" ? m : m.title;
            const poster = resolveposter(m);

            return (
              <article className="recommendation-card" key={m.id || idx}>
                <div className="recommendation-poster">
                  <img
                    src={poster}
                    alt={`${title} poster`}
                    loading="lazy"
                    onError={(e) => {
                      e.currentTarget.onerror = null;
                      e.currentTarget.src = FALLBACK_POSTER;
                    }}
                  />
                  <div className="poster-gradient" />
                  <span className="match-badge">Saved ✓</span>
                  <div className="poster-metadata">
                    <span>{m.year || 2024}</span>
                    <span>Watchlist</span>
                  </div>
                </div>

                <div className="recommendation-body">
                  <div className="title-row">
                    <h3>{title}</h3>
                    <button
                      type="button"
                      className="save-icon-button is-saved"
                      onClick={() => removeitem(m.id)}
                      title="Remove from watchlist"
                    >
                      ✕
                    </button>
                  </div>

                  <p className="movie-synopsis">
                    {m.synopsis || m.overview || "Saved to your personal storytelling queue."}
                  </p>

                  <div className="movie-actions">
                    <button
                      type="button"
                      className="trailer-button"
                      onClick={() => {
                        const q = encodeURIComponent(`${title} official trailer`);
                        window.open(`https://www.youtube.com/results?search_query=${q}`, "_blank");
                      }}
                    >
                      ▶ Watch Trailer
                    </button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
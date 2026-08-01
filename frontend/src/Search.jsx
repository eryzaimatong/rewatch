import { useState, useEffect } from "react";
import { searchmovies, gettitles } from "./api";
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

const MOODS = [
  { label: "🌱 Heal me", keyword: "Hospital Playlist", desc: "Warm, comforting stories that restore your faith in people." },
  { label: "😭 Break me more", keyword: "Reply 1988", desc: "Heavy emotional payoffs that will leave you staring at the ceiling." },
  { label: "🌌 Mind-bending", keyword: "Interstellar", desc: "High-concept sci-fi and mysteries that demand your full attention." },
  { label: "🔥 Slow burn romance", keyword: "Our Beloved Summer", desc: "Tension and character growth that take time to earn." }
];

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

export default function Search() {
  const [searchquery, setsearchquery] = useState("");
  const [movielist, setmovielist] = useState([]);
  const [loading, setloading] = useState(false);
  const [errmsg, seterrmsg] = useState("");
  const [activemood, setactivemood] = useState(null);

  useEffect(() => {
    loaddefaults();
  }, []);

  async function loaddefaults() {
    setloading(true);
    seterrmsg("");
    const res = await gettitles();
    if (res && res.length > 0) {
      const formatted = [];
      for (let i = 0; i < res.length; i++) {
        const item = res[i];
        if (typeof item === "string") {
          formatted.push({
            id: "default-" + i,
            title: item,
            year: 2024,
            synopsis: "A curated storytelling pick matching your emotional profile.",
            matchScore: 85
          });
        } else {
          formatted.push(item);
        }
      }
      setmovielist(formatted);
    } else {
      seterrmsg("could not load default titles bro");
    }
    setloading(false);
  }

  async function runsearch(e) {
    if (e) {
      e.preventDefault();
    }
    if (!searchquery || searchquery.trim() === "") {
      loaddefaults();
      return;
    }
    setloading(true);
    seterrmsg("");
    setactivemood(null);

    const res = await searchmovies(searchquery);
    if (res && res.length > 0) {
      setmovielist(res);
    } else {
      setmovielist([]);
      seterrmsg("No movies found matching that title.");
    }
    setloading(false);
  }

  function pickmood(mood) {
    setactivemood(mood.label);
    setsearchquery(mood.keyword);
    setloading(true);
    seterrmsg("");

    searchmovies(mood.keyword).then((res) => {
      if (res && res.length > 0) {
        setmovielist(res);
      } else {
        const fallbacklist = [{
          id: "mood-1",
          title: mood.keyword,
          year: 2023,
          synopsis: mood.desc,
          matchScore: 94
        }];
        setmovielist(fallbacklist);
      }
      setloading(false);
    });
  }

  function handlepostererr(e) {
    e.currentTarget.onerror = null;
    e.currentTarget.src = FALLBACK_POSTER;
  }

  return (
    <div style={{ maxWidth: "1180px", margin: "0 auto", paddingBottom: "70px" }}>
      <div 
        style={{
          background: "linear-gradient(135deg, rgba(20, 21, 26, 0.95) 0%, rgba(35, 15, 60, 0.85) 100%)",
          border: "1px solid rgba(168, 85, 247, 0.25)",
          borderRadius: "28px",
          padding: "40px",
          marginBottom: "36px",
          boxShadow: "0 20px 50px rgba(0,0,0,0.4)"
        }}
      >
        <span style={{ fontSize: "12px", fontWeight: "800", textTransform: "uppercase", letterSpacing: "2px", color: "#a855f7" }}>
          DISCOVER STORIES
        </span>
        <h1 style={{ fontSize: "36px", fontWeight: "800", margin: "10px 0 16px 0", color: "#fff" }}>
          What kind of emotional ruin are we looking for?
        </h1>

        <form onSubmit={runsearch} style={{ display: "flex", gap: "12px", marginBottom: "24px" }}>
          <input
            type="text"
            placeholder="Search by title, director, or feeling..."
            value={searchquery}
            onChange={(e) => setsearchquery(e.target.value)}
            style={{
              flex: 1,
              margin: 0,
              padding: "16px 20px",
              borderRadius: "14px",
              background: "rgba(11, 12, 16, 0.8)",
              border: "1px solid rgba(255, 255, 255, 0.15)",
              color: "#fff",
              fontSize: "15px"
            }}
          />
          <button
            type="submit"
            style={{
              padding: "0 28px",
              borderRadius: "14px",
              background: "#a855f7",
              color: "#fff",
              fontWeight: "800",
              fontSize: "15px",
              border: "none",
              cursor: "pointer"
            }}
          >
            Search
          </button>
        </form>

        <div>
          <span style={{ fontSize: "12px", color: "#a1a1aa", fontWeight: "700", marginRight: "12px" }}>
            QUICK VIBE CHECK:
          </span>
          <div style={{ display: "inline-flex", flexWrap: "wrap", gap: "8px", marginTop: "8px" }}>
            {MOODS.map((m, idx) => (
              <button
                key={idx}
                type="button"
                onClick={() => pickmood(m)}
                style={{
                  padding: "8px 14px",
                  borderRadius: "999px",
                  background: activemood === m.label ? "#a855f7" : "rgba(255, 255, 255, 0.07)",
                  border: "1px solid rgba(255, 255, 255, 0.15)",
                  color: "#fff",
                  fontSize: "13px",
                  fontWeight: "600",
                  cursor: "pointer",
                  minHeight: "auto"
                }}
              >
                {m.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "24px" }}>
        <h2 style={{ fontSize: "24px", fontWeight: "800", margin: 0, color: "#fff" }}>
          {activemood ? `Curated for: ${activemood}` : searchquery ? `Results for "${searchquery}"` : "🔥 Trending & Popular This Week"}
        </h2>
        <span style={{ fontSize: "13px", color: "#a1a1aa" }}>
          {movielist.length} {movielist.length === 1 ? "story found" : "stories found"}
        </span>
      </div>

      {loading && (
        <div className="feed-state">
          <div className="loading-orb" />
          <p>Scanning cinematic archives...</p>
        </div>
      )}

      {!loading && errmsg && (
        <div className="feed-state feed-error">
          <h3>No matches found</h3>
          <p>{errmsg}</p>
          <button type="button" onClick={loaddefaults}>
            Reset Search
          </button>
        </div>
      )}

      {!loading && movielist.length > 0 && (
        <div className="recommendation-grid">
          {movielist.map((m, index) => {
            const score = m.matchScore || 88;
            const poster = resolveposter(m);
            const title = typeof m === "string" ? m : m.title;

            return (
              <article className="recommendation-card" key={m.id || index}>
                <div className="recommendation-poster">
                  <img
                    src={poster}
                    alt={`${title} poster`}
                    loading="lazy"
                    onError={handlepostererr}
                  />
                  <div className="poster-gradient" />
                  <span className="match-badge">
                    {Math.round(score)}% match
                  </span>
                  <div className="poster-metadata">
                    <span>{m.year || 2024}</span>
                    <span>Cinematic</span>
                  </div>
                </div>

                <div className="recommendation-body">
                  <div className="title-row">
                    <h3>{title}</h3>
                    <button
                      type="button"
                      className="save-icon-button"
                      onClick={() => alert(`Saved ${title} to your watchlist!`)}
                    >
                      +
                    </button>
                  </div>

                  <p className="movie-synopsis">
                    {m.synopsis || m.overview || "An unforgettable exploration of character growth, human connection, and emotional depth."}
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
                    <button
                      type="button"
                      className="details-button"
                      onClick={() => alert(`Details for ${title} coming up!`)}
                    >
                      Details
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
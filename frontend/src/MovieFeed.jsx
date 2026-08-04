import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { getrecs, gettitles, BASE } from "./api";
import { authHeaders } from "./auth";
import MovieModal from "./MovieModal";
import MatchRing from "./MatchRing";
import "./App.css";

const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";
const FALLBACK_POSTER = "https://placehold.co/500x750/191a21/a855f7?text=Re:Watch";

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

const CHIPS = [
  "Comfort", "Slice of Life", "Nostalgia", "Family", "Hopeful Ending", "Slow Burn"
];

const FILTER_PILLS = [
  "All", "Comfort", "Slow Burn", "Found Family", "Bittersweet", "Romance"
];

function getYear(date) {
  if (!date || typeof date !== "string") {
    return null;
  }
  const year = Number(date.slice(0, 4));
  return Number.isFinite(year) ? year : null;
}

function normalizeReasons(item) {
  if (Array.isArray(item?.reasons) && item.reasons.length > 0) {
    return item.reasons;
  }
  if (Array.isArray(item?.explanations) && item.explanations.length > 0) {
    return item.explanations;
  }
  if (typeof item?.reason === "string" && item.reason.trim()) {
    return [item.reason];
  }
  return ["Balanced storytelling fit"];
}

function normalizeMovie(item, index) {
  if (typeof item === "string") {
    return {
      id: `title-${index}`,
      title: item,
      year: null,
      synopsis: "",
      posterPath: null,
      posterUrl: null,
      backdropPath: null,
      matchScore: 60,
      reasons: ["Balanced storytelling fit"],
      explanation: null,
      genres: [],
      trailerUrl: null,
    };
  }

  const nestedTitle = item?.title && typeof item.title === "object" ? item.title : null;
  const source = nestedTitle ?? item ?? {};

  const title =
    source.title ??
    source.name ??
    (typeof item?.title === "string" ? item.title : "Untitled");

  const genres = Array.isArray(source.genres)
    ? source.genres
        .map((genre) => {
          if (typeof genre === "string") {
            return genre;
          }
          return genre?.name ?? null;
        })
        .filter(Boolean)
    : [];

  return {
    id: source.id ?? source.tmdbId ?? item?.titleId ?? `title-${index}`,
    // The local catalog row id — distinct from `id` above, which prefers the
    // TMDB id. Needed to call /api/titles/{id}/match, which looks up by the
    // local database id, not the TMDB id.
    titleId: source.titleId ?? item?.titleId ?? null,
    title,
    year:
      source.year ??
      source.releaseYear ??
      item?.year ??
      getYear(
        source.releaseDate ??
          source.release_date ??
          source.firstAirDate ??
          source.first_air_date
      ),
    synopsis:
      source.synopsis ??
      source.overview ??
      item?.synopsis ??
      item?.overview ??
      "",
    posterPath:
      source.posterPath ??
      source.poster_path ??
      item?.posterPath ??
      item?.poster_path ??
      null,
    posterUrl:
      source.posterUrl ??
      source.poster_url ??
      item?.posterUrl ??
      item?.poster_url ??
      null,
    backdropPath:
      source.backdropPath ??
      source.backdrop_path ??
      item?.backdropPath ??
      item?.backdrop_path ??
      null,
    matchScore: Number(
      item?.matchScore ?? item?.score ?? source.matchScore ?? 60
    ),
    reasons: normalizeReasons(item),
    // Signed driver/tension breakdown, when the backend included it — see
    // MatchExplanation.java. Falls back to the flat `reasons` list above
    // when absent (e.g. a shape that hasn't been scored yet).
    explanation: item?.explanation ?? source.explanation ?? null,
    genres,
    trailerUrl:
      source.trailerUrl ??
      source.trailer_url ??
      item?.trailerUrl ??
      item?.trailer_url ??
      null,
  };
}

function getPosterUrl(movie) {
  if (POSTERS[movie.title]) {
    return POSTERS[movie.title];
  }
  if (typeof movie.posterUrl === "string" && movie.posterUrl.startsWith("http")) {
    return movie.posterUrl;
  }
  if (typeof movie.posterPath === "string" && movie.posterPath.trim()) {
    const normalizedPath = movie.posterPath.startsWith("/")
      ? movie.posterPath
      : `/${movie.posterPath}`;
    return `${TMDB_IMAGE_BASE_URL}${normalizedPath}`;
  }
  return FALLBACK_POSTER;
}

function cleanReason(reason) {
  if (typeof reason !== "string") {
    return "Matches your current taste";
  }
  return reason.replace(/^✓\s*/, "").replace(/_/g, " ").trim();
}

function timeOfDayGreeting() {
  const hour = new Date().getHours();
  if (hour < 5) return "Still up";
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

const cardVariants = {
  hidden: { opacity: 0, y: 18 },
  visible: (i) => ({
    opacity: 1,
    y: 0,
    transition: { delay: Math.min(i, 8) * 0.045, duration: 0.35, ease: "easeOut" }
  })
};

function SkeletonGrid({ count = 4 }) {
  return (
    <div className="recommendation-grid">
      {Array.from({ length: count }, (_, i) => (
        <div className="skeleton-card" key={i}>
          <div className="skeleton-poster" />
          <div className="skeleton-body">
            <div className="skeleton-line skeleton-line--wide" />
            <div className="skeleton-line skeleton-line--mid" />
            <div className="skeleton-line skeleton-line--narrow" />
          </div>
        </div>
      ))}
    </div>
  );
}

export default function MovieFeed() {
  const [movies, setMovies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  // Maps movie.id (tmdbId-preferring, see normalizeMovie) -> the persisted
  // watchlist row id, so a click can DELETE the right row. Replaces the old
  // local-only savedIds array, which never called the server at all and
  // reset to empty on every page refresh.
  const [saveditemsbymovieid, setsaveditemsbymovieid] = useState({});
  const [selectedmovie, setselectedmovie] = useState(null);
  const [query, setquery] = useState("");
  const [suggestions, setsuggestions] = useState([]);
  const [showsuggestions, setshowsuggestions] = useState(false);
  const [activechips, setactivechips] = useState(["Comfort", "Hopeful Ending"]);
  const [vibe, setvibe] = useState("All");
  const [moodline, setmoodline] = useState("");
  const [trending, settrending] = useState([]);
  const [becauseyouloved, setbecauseyouloved] = useState([]);
  const [similardna, setsimilardna] = useState([]);

  const username = localStorage.getItem("username") || "friend";

  let hook = "It feels like you're looking for warmth and comfort after a long week.";
  if (vibe === "Bittersweet") {
    hook = "Tonight feels like you want a story that lingers and leaves something unresolved.";
  }
  if (vibe === "Slow Burn") {
    hook = "You have the patience tonight for character growth over instant plot twists.";
  }
  if (vibe === "Found Family") {
    hook = "You're leaning toward chosen-family bonds and quiet camaraderie tonight.";
  }
  if (vibe === "Romance") {
    hook = "You're looking for slow-building chemistry and emotional intimacy tonight.";
  }

  async function loadMood() {
    const userId = localStorage.getItem("userId");
    const greeting = timeOfDayGreeting();
    if (!userId) {
      setmoodline(`${greeting}. Let's find your first story.`);
      return;
    }

    const res = await fetch(`${BASE}/api/tastedna/profile/${userId}`, { headers: authHeaders() }).catch(() => null);
    const data = res && res.ok ? await res.json() : null;

    if (!data || !data.personalized || !data.traits) {
      setmoodline(`${greeting}, ${username}. Rate a few titles and this will start reading your mood.`);
      return;
    }

    const entries = Object.values(data.traits);
    const top = entries.reduce((best, t) => (t.val > (best?.val ?? -1) ? t : best), null);
    setmoodline(
      top ? `${greeting}, ${username}. Tonight leans ${top.label.toLowerCase()}.` : `${greeting}, ${username}.`
    );
  }

  async function loadWatchlist() {
    const userId = localStorage.getItem("userId");
    if (!userId) return;

    const res = await fetch(`${BASE}/api/watchlist/${userId}`, { headers: authHeaders() }).catch(() => null);
    const items = res && res.ok ? await res.json() : null;
    if (!Array.isArray(items)) return;

    const map = {};
    items.forEach((item) => {
      const key = String(item.tmdbId ?? item.titleId);
      map[key] = item.id;
    });
    setsaveditemsbymovieid(map);
  }

  async function loadDiscovery() {
    const userId = localStorage.getItem("userId");
    if (!userId) return;

    const [trendingRes, lovedRes, dnaRes] = await Promise.all([
      fetch(`${BASE}/api/movies/trending`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/discovery/because-you-loved/${userId}?limit=4`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/discovery/similar-dna/${userId}?limit=4`, { headers: authHeaders() }).catch(() => null)
    ]);

    if (trendingRes && trendingRes.ok) {
      const data = await trendingRes.json();
      settrending(Array.isArray(data) ? data.slice(0, 4).map((m, i) => normalizeMovie(m, i)) : []);
    }
    if (lovedRes && lovedRes.ok) {
      const data = await lovedRes.json();
      setbecauseyouloved(Array.isArray(data) ? data.map((m, i) => normalizeMovie(m, i)) : []);
    }
    if (dnaRes && dnaRes.ok) {
      const data = await dnaRes.json();
      setsimilardna(Array.isArray(data) ? data.map((m, i) => normalizeMovie(m, i)) : []);
    }
  }

  useEffect(() => {
    // See EvolutionTimeline.jsx for why this needs a targeted disable: a
    // mount-time fetch that sets loading state is exactly what an effect is
    // for; the rule's static analysis flags it regardless. Block-scoped
    // (rather than eslint-disable-next-line on each call) because the rule
    // only ever flags whichever of the two calls it reaches first.
    /* eslint-disable react-hooks/set-state-in-effect */
    loadData();
    loadMood();
    loadWatchlist();
    loadDiscovery();
    /* eslint-enable react-hooks/set-state-in-effect */
  }, []);

  async function loadData() {
    setLoading(true);
    setError("");

    const userId = localStorage.getItem("userId");
    // The backend derives the personalizing id from the JWT (see TmdbController),
    // not a query param — a permitAll route that trusted `?userId=` would let
    // anyone read anyone's personalized feed without logging in.
    const tmdbRes = await fetch(`${BASE}/api/movies/popular`, { headers: authHeaders() }).catch(() => null);

    if (tmdbRes && tmdbRes.ok) {
      const liveData = await tmdbRes.json();
      if (Array.isArray(liveData) && liveData.length > 0) {
        const normalizedMovies = liveData.map((item, index) => normalizeMovie(item, index));
        setMovies(normalizedMovies);
        setLoading(false);
        return;
      }
    }

    let response = [];

    if (userId) {
      response = await getrecs(userId);
    }

    if (!Array.isArray(response) || response.length === 0) {
      const titles = await gettitles();
      response = Array.isArray(titles) ? titles : [];
    }

    const normalizedMovies = response.map((item, index) => normalizeMovie(item, index));
    setMovies(normalizedMovies);
    setLoading(false);
  }

  useEffect(() => {
    // Debounced regardless of branch (rather than an early synchronous
    // setState for the too-short case) — see EvolutionTimeline.jsx for why a
    // dependency-triggered effect that sets state gets flagged either way;
    // routing every branch through the same timeout keeps this one clean of
    // it without a manual disable.
    const handle = setTimeout(() => {
      if (query.trim().length < 2) {
        setsuggestions([]);
        return;
      }
      fetch(`${BASE}/api/movies/search-suggestions?query=${encodeURIComponent(query)}`, { headers: authHeaders() })
        .then((res) => (res.ok ? res.json() : []))
        .then((data) => setsuggestions(Array.isArray(data) ? data : []))
        .catch(() => setsuggestions([]));
    }, 250);
    return () => clearTimeout(handle);
  }, [query]);

  async function dosearch(overrideQuery) {
    const q = (overrideQuery ?? query).trim();
    setshowsuggestions(false);

    if (q === "") {
      loadData();
      return;
    }

    setLoading(true);
    setError("");

    let res = await fetch(`${BASE}/api/movies/nlp-search?query=` + encodeURIComponent(q), { headers: authHeaders() }).catch(() => null);

    if (!res || !res.ok) {
      res = await fetch(`${BASE}/api/movies/search?query=` + encodeURIComponent(q), { headers: authHeaders() }).catch(() => null);
    }

    if (!res || !res.ok) {
      setError("Could not reach the search server. Please try again.");
      setLoading(false);
      return;
    }

    const data = await res.json();
    if (Array.isArray(data)) {
      const normalizedMovies = data.map((item, index) => normalizeMovie(item, index));
      setMovies(normalizedMovies);
    } else {
      setMovies([]);
    }
    setLoading(false);
  }

  function togglechip(chip) {
    const next = [];
    let found = false;
    for (let i = 0; i < activechips.length; i++) {
      if (activechips[i] === chip) {
        found = true;
      } else {
        next.push(activechips[i]);
      }
    }
    if (!found) {
      next.push(chip);
    }
    setactivechips(next);
  }

  function handlePosterError(event) {
    event.currentTarget.onerror = null;
    event.currentTarget.src = FALLBACK_POSTER;
  }

  function handleTrailer(movie) {
    if (movie.trailerUrl) {
      window.open(movie.trailerUrl, "_blank", "noopener,noreferrer");
      return;
    }

    const searchQuery = encodeURIComponent(`${movie.title} official trailer`);
    window.open(
      `https://www.youtube.com/results?search_query=${searchQuery}`,
      "_blank",
      "noopener,noreferrer"
    );
  }

  async function handleSave(movie) {
    const userId = localStorage.getItem("userId");
    if (!userId) {
      return;
    }
    const key = String(movie.id);
    const existingItemId = saveditemsbymovieid[key];

    if (existingItemId) {
      const res = await fetch(`${BASE}/api/watchlist/items/${existingItemId}?userId=${userId}`, {
        method: "DELETE",
        headers: authHeaders()
      }).catch(() => null);
      if (res && res.ok) {
        setsaveditemsbymovieid((current) => {
          const next = { ...current };
          delete next[key];
          return next;
        });
      }
      return;
    }

    const res = await fetch(`${BASE}/api/watchlist/items`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeaders() },
      body: JSON.stringify({
        userId,
        tmdbId: movie.id,
        titleId: movie.titleId ?? undefined,
        title: movie.title
      })
    }).catch(() => null);

    if (res && res.ok) {
      const saved = await res.json();
      setsaveditemsbymovieid((current) => ({ ...current, [key]: saved.id }));
    }
  }

  function handleDetails(movie) {
    setselectedmovie(movie);
  }

  const visiblemovies = [];
  for (let i = 0; i < movies.length; i++) {
    const m = movies[i];
    if (vibe === "All") {
      visiblemovies.push(m);
    } else {
      let match = false;
      for (let j = 0; j < m.reasons.length; j++) {
        if (m.reasons[j].toLowerCase().includes(vibe.toLowerCase())) {
          match = true;
        }
      }
      for (let k = 0; k < m.genres.length; k++) {
        if (m.genres[k].toLowerCase().includes(vibe.toLowerCase())) {
          match = true;
        }
      }
      if (m.title.toLowerCase().includes(vibe.toLowerCase())) {
        match = true;
      }
      if (match) {
        visiblemovies.push(m);
      }
    }
  }

  const shortlist = visiblemovies.slice(0, 4);
  const comfortlist = [];
  const bitterlist = [];

  for (let i = 0; i < visiblemovies.length; i++) {
    const m = visiblemovies[i];
    const text = (m.reasons.join(" ") + " " + m.genres.join(" ") + " " + m.title).toLowerCase();

    if (comfortlist.length < 4 && (text.includes("comfort") || text.includes("family") || text.includes("slice"))) {
      comfortlist.push(m);
    }
    if (bitterlist.length < 4 && (text.includes("bitter") || text.includes("drama") || text.includes("slow"))) {
      bitterlist.push(m);
    }
  }

  function renderRow(title, eyebrow, subtitle, items) {
    if (items.length === 0) {
      return null;
    }

    return (
      <div style={{ marginBottom: "var(--sp-5)" }}>
        <div style={{ marginBottom: "var(--sp-2)" }}>
          <p className="section-eyebrow">{eyebrow}</p>
          <h2 style={{ fontSize: "1.4rem", margin: "0 0 4px 0" }}>{title}</h2>
          <p style={{ margin: 0, fontSize: "0.82rem" }}>{subtitle}</p>
        </div>

        <div className="recommendation-grid">
          {items.map((movie, index) => {
            const isSaved = Boolean(saveditemsbymovieid[String(movie.id)]);
            const drivers = movie.explanation?.drivers ?? [];
            const tensions = movie.explanation?.tensions ?? [];
            const hasExplanation = drivers.length > 0 || tensions.length > 0;

            return (
              <motion.article
                className="recommendation-card"
                key={`${title}-${movie.id}`}
                custom={index}
                initial="hidden"
                whileInView="visible"
                viewport={{ once: true, margin: "-40px" }}
                variants={cardVariants}
                whileHover={{ y: -6 }}
                transition={{ type: "spring", stiffness: 320, damping: 24 }}
              >
                <div className="recommendation-poster">
                  <img
                    src={getPosterUrl(movie)}
                    alt={`${movie.title} poster`}
                    loading="lazy"
                    onError={handlePosterError}
                  />
                  <div className="poster-gradient" />

                  <div className="match-ring-badge">
                    <MatchRing score={movie.matchScore} />
                  </div>

                  <div className="poster-quick-actions">
                    <button
                      type="button"
                      className={`save-icon-button ${isSaved ? "is-saved" : ""}`}
                      onClick={() => handleSave(movie)}
                      aria-label={
                        isSaved
                          ? `Remove ${movie.title} from watchlist`
                          : `Save ${movie.title} to watchlist`
                      }
                      title={
                        isSaved ? "Remove from watchlist" : "Save to watchlist"
                      }
                    >
                      {isSaved ? "✓" : "+"}
                    </button>
                  </div>

                  <div className="poster-metadata">
                    {movie.year && <span>{movie.year}</span>}
                    {movie.genres.slice(0, 2).map((genre) => (
                      <span key={genre}>{genre}</span>
                    ))}
                  </div>
                </div>

                <div className="recommendation-body">
                  <h3>{movie.title}</h3>

                  <p className="movie-synopsis">
                    {movie.synopsis || "No synopsis is available for this title yet."}
                  </p>

                  <div className="fit-panel">
                    <p className="fit-label">Why this fits</p>

                    {hasExplanation ? (
                      <ul className="contribution-list">
                        {drivers.slice(0, 2).map((d) => (
                          <li key={d.trait} className="contribution-row is-driver">
                            <span className="contribution-label">{d.label}</span>
                            <span className="contribution-value">+{d.contribution.toFixed(1)}</span>
                          </li>
                        ))}
                        {tensions.slice(0, 1).map((t) => (
                          <li key={t.trait} className="contribution-row is-tension">
                            <span className="contribution-label">{t.label}</span>
                            <span className="contribution-value">{t.contribution.toFixed(1)}</span>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <ul>
                        {movie.reasons.slice(0, 3).map((reason, reasonIndex) => (
                          <li key={`${movie.id}-${reasonIndex}`}>
                            {cleanReason(reason)}
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>

                  <div className="movie-actions">
                    <button
                      type="button"
                      className="trailer-button"
                      onClick={() => handleTrailer(movie)}
                    >
                      <span aria-hidden="true">▶</span>
                      Watch trailer
                    </button>

                    <button
                      type="button"
                      className="details-button"
                      onClick={() => handleDetails(movie)}
                    >
                      Details
                    </button>
                  </div>
                </div>
              </motion.article>
            );
          })}
        </div>
      </div>
    );
  }

  return (
    <main className="movie-feed">
      <section className="feed-hero" style={{ textAlign: "center" }}>
        <p className="hero-eyebrow">
          TONIGHT'S STORY MATCH
        </p>
        <h1 style={{ fontSize: "clamp(1.8rem, 4vw, 2.4rem)", fontWeight: "var(--fw-hero)", margin: "6px 0" }}>
          {moodline || `Good evening, ${username}.`}
        </h1>
        <p className="hero-description">
          "{hook}"
        </p>

        <div style={{ position: "relative", maxWidth: "600px", margin: "var(--sp-3) auto" }}>
          <div style={{ display: "flex", gap: "10px" }}>
            <input
              type="text"
              placeholder="e.g. something comforting after exams..."
              value={query}
              onChange={(e) => setquery(e.target.value)}
              onFocus={() => setshowsuggestions(true)}
              onBlur={() => setTimeout(() => setshowsuggestions(false), 150)}
              onKeyDown={(e) => e.key === "Enter" && dosearch()}
              style={{ flex: 1, borderRadius: "24px", margin: 0 }}
            />
            <button onClick={() => dosearch()} className="btn-primary" style={{ borderRadius: "24px" }}>
              Search
            </button>
          </div>

          {showsuggestions && suggestions.length > 0 && (
            <div className="search-suggestions">
              {suggestions.map((s) => (
                <button
                  key={`${s.type}-${s.text}`}
                  type="button"
                  className="search-suggestion-row"
                  onMouseDown={() => {
                    setquery(s.text);
                    dosearch(s.text);
                  }}
                >
                  <span className="search-suggestion-icon">{s.type === "mood" ? "✦" : "▤"}</span>
                  {s.text}
                </button>
              ))}
            </div>
          )}
        </div>

        <div>
          <span style={{ fontSize: "0.86rem", marginRight: "10px" }}>We understood:</span>
          <div className="chip-row" style={{ display: "inline-flex", justifyContent: "center", marginTop: "10px" }}>
            {CHIPS.map((c) => (
              <button
                key={c}
                onClick={() => togglechip(c)}
                className={`chip${activechips.includes(c) ? " active" : ""}`}
              >
                {activechips.includes(c) ? "✓ " : "+ "} {c}
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="feed-section">
        <div className="feed-heading-row" style={{ alignItems: "center", flexWrap: "wrap", gap: "16px", marginBottom: "var(--sp-3)" }}>
          <div>
            <p className="section-eyebrow">Your nightly shortlist</p>
            <h2>Recommended for you</h2>
          </div>

          <div className="pill-row">
            {FILTER_PILLS.map((pill) => (
              <button
                key={pill}
                type="button"
                onClick={() => setvibe(pill)}
                className={`pill${vibe === pill ? " active" : ""}`}
              >
                {pill}
              </button>
            ))}
          </div>
        </div>

        {loading && <SkeletonGrid count={4} />}

        {!loading && error && (
          <div className="feed-state feed-error">
            <h3>Something went off-script.</h3>
            <p>{error}</p>
            <button type="button" onClick={loadData}>
              Try again
            </button>
          </div>
        )}

        {!loading && !error && visiblemovies.length === 0 && (
          <div className="feed-state">
            <h3>We searched every universe.</h3>
            <p>Nothing matched "{vibe}". Try lowering one emotional filter or switching back to "All".</p>
            <button type="button" onClick={() => setvibe("All")} className="btn-primary">
              Reset to All Vibes
            </button>
          </div>
        )}

        {!loading && !error && visiblemovies.length > 0 && (
          <div>
            {renderRow(
              "Tonight's Top Matches",
              "Personal TasteDNA™ Match",
              "High-confidence recommendations based on your Found-Family & Comfort weights.",
              shortlist
            )}

            {renderRow(
              "Low-Anxiety & Comforting",
              "Warm & Healing Worlds",
              "Gentle pacing, slice-of-life camaraderie, and zero stress after a long day.",
              comfortlist
            )}

            {renderRow(
              "Bittersweet & Unresolved",
              "High Emotional Payoff",
              "Endings that linger in your thoughts. Emotional recovery time: 2–3 days.",
              bitterlist
            )}

            {renderRow(
              "Trending This Week",
              "Trending Worldwide",
              "What's popular right now, scored against your TasteDNA — not the other way around.",
              trending
            )}

            {becauseyouloved.length > 0 && renderRow(
              `Because You Loved ${becauseyouloved[0]?.reasons?.[0]?.replace("Because you loved ", "") || "That"}`,
              "More Like Your Favorite",
              "Titles whose own storytelling shape is closest to the one you rated highest.",
              becauseyouloved
            )}

            {renderRow(
              "Similar Emotional DNA",
              "Whole-Profile Shape Match",
              "Ranked by overall trait-vector similarity to you — a different lens than the weighted match score above.",
              similardna
            )}
          </div>
        )}
      </section>

      {selectedmovie && (
        <MovieModal
          movie={selectedmovie}
          onClose={() => setselectedmovie(null)}
        />
      )}
    </main>
  );
}

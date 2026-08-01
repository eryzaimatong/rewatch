import { useEffect, useState } from "react";
import { getrecs, gettitles } from "./api";
import MovieModal from "./MovieModal";
import "./App.css";

const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";
const FALLBACK_POSTER = "https://placehold.co/500x750/191a21/a855f7?text=Re:Watch";

// Built-in official TMDB poster URLs for your movie catalog
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

  if (
    Array.isArray(item?.explanations) &&
    item.explanations.length > 0
  ) {
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
      genres: [],
      trailerUrl: null,
    };
  }

  const nestedTitle =
    item?.title && typeof item.title === "object"
      ? item.title
      : null;

  const source = nestedTitle ?? item ?? {};

  const title =
    source.title ??
    source.name ??
    (typeof item?.title === "string"
      ? item.title
      : "Untitled");

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
    id:
      source.id ??
      source.tmdbId ??
      item?.titleId ??
      `title-${index}`,

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
      item?.matchScore ??
        item?.score ??
        source.matchScore ??
        60
    ),

    reasons: normalizeReasons(item),

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
  // 1. Check our built-in POSTERS map first by movie title
  if (POSTERS[movie.title]) {
    return POSTERS[movie.title];
  }

  // 2. Check if the backend sent an absolute HTTP url
  if (
    typeof movie.posterUrl === "string" &&
    movie.posterUrl.startsWith("http")
  ) {
    return movie.posterUrl;
  }

  // 3. Check if the backend sent a TMDB posterPath
  if (
    typeof movie.posterPath === "string" &&
    movie.posterPath.trim()
  ) {
    const normalizedPath = movie.posterPath.startsWith("/")
      ? movie.posterPath
      : `/${movie.posterPath}`;

    return `${TMDB_IMAGE_BASE_URL}${normalizedPath}`;
  }

  // 4. Return reliable online placeholder instead of broken svg
  return FALLBACK_POSTER;
}

function cleanReason(reason) {
  if (typeof reason !== "string") {
    return "Matches your current taste";
  }

  return reason
    .replace(/^✓\s*/, "")
    .replace(/_/g, " ")
    .trim();
}

export default function MovieFeed() {
  const [movies, setMovies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [savedIds, setSavedIds] = useState([]);
  const [selectedmovie, setselectedmovie] = useState(null);

  const username =
    localStorage.getItem("username") || "friend";

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    setLoading(true);
    setError("");

    const userId = localStorage.getItem("userId");
    let response = [];

    if (userId) {
      response = await getrecs(userId);
    }

    if (!Array.isArray(response) || response.length === 0) {
      const titles = await gettitles();
      response = Array.isArray(titles) ? titles : [];
    }

    const normalizedMovies = response.map(
      (item, index) => normalizeMovie(item, index)
    );

    setMovies(normalizedMovies);
    setLoading(false);
  }

  function handlePosterError(event) {
    event.currentTarget.onerror = null;
    event.currentTarget.src = FALLBACK_POSTER;
  }

  function handleTrailer(movie) {
    if (movie.trailerUrl) {
      window.open(
        movie.trailerUrl,
        "_blank",
        "noopener,noreferrer"
      );

      return;
    }

    const query = encodeURIComponent(
      `${movie.title} official trailer`
    );

    window.open(
      `https://www.youtube.com/results?search_query=${query}`,
      "_blank",
      "noopener,noreferrer"
    );
  }

  function handleSave(movie) {
    setSavedIds((currentIds) => {
      if (currentIds.includes(movie.id)) {
        return currentIds.filter(
          (savedId) => savedId !== movie.id
        );
      }

      return [...currentIds, movie.id];
    });
  }

  function handleDetails(movie) {
    setselectedmovie(movie);
  }

  return (
    <main className="movie-feed">
      <section className="feed-hero">
        <p className="hero-eyebrow">
          Curated for tonight
        </p>

        <h1>Good evening, {username}.</h1>

        <p className="hero-description">
          Ready to ruin your sleep again? These picks were
          selected from your TasteDNA.
        </p>
      </section>

      <section className="feed-section">
        <div className="feed-heading-row">
          <div>
            <p className="section-eyebrow">
              Your nightly shortlist
            </p>

            <h2>Recommended for you</h2>
          </div>

          <span className="sort-label">
            Sorted by TasteDNA match
          </span>
        </div>

        {loading && (
          <div className="feed-state">
            <div className="loading-orb" />

            <p>
              Synthesizing your cinematic universe…
            </p>
          </div>
        )}

        {!loading && error && (
          <div className="feed-state feed-error">
            <h3>Something went off-script.</h3>

            <p>{error}</p>

            <button type="button" onClick={loadData}>
              Try again
            </button>
          </div>
        )}

        {!loading &&
          !error &&
          movies.length === 0 && (
            <div className="feed-state">
              <h3>No recommendations yet.</h3>

              <p>
                Set your TasteDNA or rate a few titles
                first.
              </p>
            </div>
          )}

        {!loading &&
          !error &&
          movies.length > 0 && (
            <div className="recommendation-grid">
              {movies.map((movie) => {
                const isSaved = savedIds.includes(
                  movie.id
                );

                return (
                  <article
                    className="recommendation-card"
                    key={movie.id}
                  >
                    <div className="recommendation-poster">
                      <img
                        src={getPosterUrl(movie)}
                        alt={`${movie.title} poster`}
                        loading="lazy"
                        onError={handlePosterError}
                      />

                      <div className="poster-gradient" />

                      <span className="match-badge">
                        {Math.round(movie.matchScore)}%
                        match
                      </span>

                      <div className="poster-metadata">
                        {movie.year && (
                          <span>{movie.year}</span>
                        )}

                        {movie.genres
                          .slice(0, 2)
                          .map((genre) => (
                            <span key={genre}>
                              {genre}
                            </span>
                          ))}
                      </div>
                    </div>

                    <div className="recommendation-body">
                      <div className="title-row">
                        <h3>{movie.title}</h3>

                        <button
                          type="button"
                          className={`save-icon-button ${
                            isSaved
                              ? "is-saved"
                              : ""
                          }`}
                          onClick={() =>
                            handleSave(movie)
                          }
                          aria-label={
                            isSaved
                              ? `Remove ${movie.title} from watchlist`
                              : `Save ${movie.title} to watchlist`
                          }
                          title={
                            isSaved
                              ? "Remove from watchlist"
                              : "Save to watchlist"
                          }
                        >
                          {isSaved ? "✓" : "+"}
                        </button>
                      </div>

                      <p className="movie-synopsis">
                        {movie.synopsis ||
                          "No synopsis is available for this title yet."}
                      </p>

                      <div className="fit-panel">
                        <p className="fit-label">
                          Why this fits
                        </p>

                        <ul>
                          {movie.reasons
                            .slice(0, 3)
                            .map(
                              (
                                reason,
                                reasonIndex
                              ) => (
                                <li
                                  key={`${movie.id}-${reasonIndex}`}
                                >
                                  {cleanReason(
                                    reason
                                  )}
                                </li>
                              )
                            )}
                        </ul>
                      </div>

                      <div className="movie-actions">
                        <button
                          type="button"
                          className="trailer-button"
                          onClick={() =>
                            handleTrailer(movie)
                          }
                        >
                          <span aria-hidden="true">
                            ▶
                          </span>

                          Watch trailer
                        </button>

                        <button
                          type="button"
                          className="details-button"
                          onClick={() =>
                            handleDetails(movie)
                          }
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
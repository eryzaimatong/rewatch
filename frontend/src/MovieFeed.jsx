import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { getrecs, gettitles, BASE, getWatchStatuses, setWatchStatus } from "./api";
import { authHeaders } from "./auth";
import MovieModal from "./MovieModal";
import MatchRing from "./MatchRing";
import EmptyState from "./EmptyState";
import ErrorState from "./ErrorState";
import { SkeletonPosterGrid } from "./Skeleton";
import { IconEye, IconFlame, IconShuffle, IconFilmCheck } from "./Icons";
import "./App.css";

const TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";
const FALLBACK_POSTER = "https://placehold.co/500x750/191a21/a855f7?text=Re:Watch";

// ISO 639-1 -> display name, for whatever originalLanguage codes actually
// turn up in the catalog. Falls back to the raw code for anything unmapped
// rather than hiding it — real data stays visible even if unlabeled.
const LANGUAGE_NAMES = {
  en: "English", ko: "Korean", ja: "Japanese", es: "Spanish", fr: "French",
  de: "German", zh: "Chinese", hi: "Hindi", it: "Italian", pt: "Portuguese",
  th: "Thai", ru: "Russian"
};

function languageLabel(code) {
  return LANGUAGE_NAMES[code] || code.toUpperCase();
}

const RECENT_SEARCHES_KEY = "recentSearches";
const MAX_RECENT_SEARCHES = 6;

function loadRecentSearches() {
  try {
    const raw = JSON.parse(localStorage.getItem(RECENT_SEARCHES_KEY) || "[]");
    return Array.isArray(raw) ? raw : [];
  } catch {
    return [];
  }
}

function pushRecentSearch(q) {
  const trimmed = q.trim();
  if (!trimmed) return loadRecentSearches();
  const next = [trimmed, ...loadRecentSearches().filter((s) => s.toLowerCase() !== trimmed.toLowerCase())]
    .slice(0, MAX_RECENT_SEARCHES);
  localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(next));
  return next;
}

// Which chip label a trait shows depends on which SIDE of neutral it landed
// on — "pacing" high reads as "Slow Burn", low reads as "Fast-Paced". A few
// axes have no natural low-side label (growth/romance); those just don't
// contribute a chip when the query pushed them down instead of up.
const TRAIT_CHIP_LABELS = {
  comfort: { high: "Comfort", low: "Edgy" },
  nostalgia: { high: "Nostalgia", low: "Fresh & Modern" },
  family: { high: "Family", low: "Solo Journey" },
  hope: { high: "Hopeful Ending", low: null },
  bitter: { high: "Bittersweet", low: "Tidy Ending" },
  pacing: { high: "Slow Burn", low: "Fast-Paced" },
  growth: { high: "Character Growth", low: null },
  humor: { high: "Lighthearted", low: "Heavier Tone" },
  romance: { high: "Romance", low: null },
  intensity: { high: "Intense", low: "Cozy" }
};

// Top few axes the search actually deviated on, translated into the same
// chip vocabulary the default "We understood" row already uses — real
// labels instead of a hardcoded default that never changed no matter what
// was searched.
function chipsFromUnderstood(understood) {
  if (!understood || typeof understood !== "object") return null;
  const deviations = Object.entries(understood)
    .map(([key, val]) => ({ key, val, deviation: Math.abs(val - 0.5) }))
    .filter((d) => d.deviation > 0.08)
    .sort((a, b) => b.deviation - a.deviation)
    .slice(0, 3);

  const chips = [];
  for (const d of deviations) {
    const labels = TRAIT_CHIP_LABELS[d.key];
    if (!labels) continue;
    const label = d.val >= 0.5 ? labels.high : labels.low;
    if (label && !chips.includes(label)) chips.push(label);
  }
  return chips.length > 0 ? chips : null;
}

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

function numberOrUndefined(v) {
  if (v === null || v === undefined) return undefined;
  const n = Number(v);
  return Number.isNaN(n) ? undefined : n;
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
      matchScore: undefined,
      reasons: ["Balanced storytelling fit"],
      explanation: null,
      storyVector: null,
      genres: [],
      trailerUrl: null,
      originalLanguage: null,
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
    // No fallback default here on purpose — a fabricated score (this used to
    // default to 60) is worse than an honestly missing one. MatchRing renders
    // nothing rather than a fake number when this is undefined.
    matchScore: numberOrUndefined(item?.matchScore ?? item?.score ?? source.matchScore),
    reasons: normalizeReasons(item),
    // Signed driver/tension breakdown, when the backend included it — see
    // MatchExplanation.java. Falls back to the flat `reasons` list above
    // when absent (e.g. a shape that hasn't been scored yet).
    explanation: item?.explanation ?? source.explanation ?? null,
    // Keyed by Trait.java's wire keys (comfort, intensity, bitter, ...),
    // 0..1. Powers the mood-row split below — without this, "Comforting" and
    // "Bittersweet" had nothing but reason/genre text to filter on.
    storyVector: item?.storyVector ?? source.storyVector ?? null,
    genres,
    trailerUrl:
      source.trailerUrl ??
      source.trailer_url ??
      item?.trailerUrl ??
      item?.trailer_url ??
      null,
    originalLanguage:
      source.originalLanguage ??
      source.original_language ??
      item?.originalLanguage ??
      item?.original_language ??
      null,
  };
}

function getPosterUrl(movie) {
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
  if (hour < 5) return "You're still awake";
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

export default function MovieFeed() {
  const [movies, setMovies] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  // Maps movie.id (tmdbId-preferring, see normalizeMovie) -> the persisted
  // watchlist row id, so a click can DELETE the right row. Replaces the old
  // local-only savedIds array, which never called the server at all and
  // reset to empty on every page refresh.
  const [saveditemsbymovieid, setsaveditemsbymovieid] = useState({});
  const [watchstatusbymovieid, setwatchstatusbymovieid] = useState({});
  const [selectedmovie, setselectedmovie] = useState(null);
  const [query, setquery] = useState("");
  const [suggestions, setsuggestions] = useState([]);
  const [showsuggestions, setshowsuggestions] = useState(false);
  const [recentsearches, setrecentsearches] = useState(() => loadRecentSearches());
  const [searchunderstood, setsearchunderstood] = useState(null);
  const [searchhook, setsearchhook] = useState(null);
  // Persisted so switching moods sticks across a reload/re-login instead of
  // silently resetting to "All" every time — it's a real choice, not
  // ephemeral render state.
  const [vibe, setvibeRaw] = useState(() => localStorage.getItem("vibe") || "All");
  function setvibe(next) {
    setvibeRaw(next);
    localStorage.setItem("vibe", next);
  }
  const [languagefilter, setlanguagefilterRaw] = useState(() => localStorage.getItem("languageFilter") || "All");
  function setlanguagefilter(next) {
    setlanguagefilterRaw(next);
    localStorage.setItem("languageFilter", next);
  }
  const [genrefilter, setgenrefilterRaw] = useState(() => localStorage.getItem("genreFilter") || "All");
  function setgenrefilter(next) {
    setgenrefilterRaw(next);
    localStorage.setItem("genreFilter", next);
  }
  const [moodline, setmoodline] = useState("");
  const [streak, setstreak] = useState(null);
  const [trending, settrending] = useState([]);
  const [becauseyouloved, setbecauseyouloved] = useState([]);
  const [similardna, setsimilardna] = useState([]);
  const [hiddengems, sethiddengems] = useState([]);
  const [rediscover, setrediscover] = useState([]);
  const [collections, setcollections] = useState([]);
  const [selectedcollection, setselectedcollection] = useState(null);
  const [collectionitems, setcollectionitems] = useState([]);
  const [collectionloading, setcollectionloading] = useState(false);

  const [dealbreakerhiddencount, setdealbreakerhiddencount] = useState(0);
  const [dealbreakerhidden, setdealbreakerhidden] = useState(null);
  const [dealbreakerhiddenloading, setdealbreakerhiddenloading] = useState(false);

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
  // A real search's understood traits take over the hero line entirely —
  // more specific than the generic vibe-pill hook above.
  if (searchhook) {
    hook = searchhook;
  }
  const understoodChips = searchunderstood;

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

  async function loadWatchStatuses() {
    const userId = localStorage.getItem("userId");
    if (!userId) return;
    const statuses = await getWatchStatuses(userId);
    if (statuses && typeof statuses === "object") {
      setwatchstatusbymovieid(statuses);
    }
  }

  async function handleSetWatchStatus(movie) {
    const userId = localStorage.getItem("userId");
    if (!userId || !movie.titleId) return;
    const key = String(movie.titleId);
    const current = watchstatusbymovieid[key];
    // Cycle: unset -> Watching -> Dropped -> unset. "WATCHED" is derived from
    // a real rating and never reaches here (see the read-only badge below).
    const next = current === "WATCHING" ? "DROPPED" : current === "DROPPED" ? null : "WATCHING";
    const result = await setWatchStatus(userId, movie.titleId, next);
    if (result?.status === "success") {
      setwatchstatusbymovieid((prev) => {
        const copy = { ...prev };
        if (next) {
          copy[key] = next;
        } else {
          delete copy[key];
        }
        return copy;
      });
    }
  }

  async function loadDiscovery() {
    const userId = localStorage.getItem("userId");
    if (!userId) return;

    const [trendingRes, lovedRes, dnaRes, collectionsRes, gemsRes, rediscoverRes] = await Promise.all([
      fetch(`${BASE}/api/movies/trending`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/discovery/because-you-loved/${userId}?limit=4`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/discovery/similar-dna/${userId}?limit=4`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/discovery/collections`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/discovery/hidden-gems/${userId}?limit=4`, { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/discovery/rediscover/${userId}?limit=4`, { headers: authHeaders() }).catch(() => null)
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
    if (collectionsRes && collectionsRes.ok) {
      setcollections(await collectionsRes.json());
    }
    if (gemsRes && gemsRes.ok) {
      const data = await gemsRes.json();
      sethiddengems(Array.isArray(data) ? data.map((m, i) => normalizeMovie(m, i)) : []);
    }
    if (rediscoverRes && rediscoverRes.ok) {
      const data = await rediscoverRes.json();
      setrediscover(Array.isArray(data) ? data.map((m, i) => normalizeMovie(m, i)) : []);
    }
  }

  async function selectCollection(slug) {
    const userId = localStorage.getItem("userId");
    if (!userId) return;
    setselectedcollection(slug);
    setcollectionloading(true);
    const res = await fetch(`${BASE}/api/discovery/collections/${slug}/${userId}?limit=12`, { headers: authHeaders() }).catch(() => null);
    if (res && res.ok) {
      const data = await res.json();
      setcollectionitems(Array.isArray(data) ? data.map((m, i) => normalizeMovie(m, i)) : []);
    } else {
      setcollectionitems([]);
    }
    setcollectionloading(false);
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
    loadWatchStatuses();
    loadDiscovery();
    loadDealbreakerHiddenCount();
    loadStreak();
    /* eslint-enable react-hooks/set-state-in-effect */
  }, []);

  async function loadStreak() {
    const userId = localStorage.getItem("userId");
    if (!userId) return;
    const res = await fetch(`${BASE}/api/streak/${userId}`, { headers: authHeaders() }).catch(() => null);
    if (res && res.ok) {
      setstreak(await res.json().catch(() => null));
    }
  }

  async function loadDealbreakerHiddenCount() {
    const userId = localStorage.getItem("userId");
    if (!userId) return;
    const res = await fetch(`${BASE}/api/recommendations/${userId}/dealbreaker-hidden-count`, { headers: authHeaders() }).catch(() => null);
    if (res && res.ok) {
      const data = await res.json();
      setdealbreakerhiddencount(typeof data.count === "number" ? data.count : 0);
    }
  }

  async function showDealbreakerHidden() {
    const userId = localStorage.getItem("userId");
    if (!userId || dealbreakerhiddenloading) return;
    setdealbreakerhiddenloading(true);
    const res = await fetch(`${BASE}/api/recommendations/${userId}/dealbreaker-hidden?limit=12`, { headers: authHeaders() }).catch(() => null);
    if (res && res.ok) {
      const data = await res.json();
      setdealbreakerhidden(Array.isArray(data) ? data.map((m, i) => normalizeMovie(m, i)) : []);
    }
    setdealbreakerhiddenloading(false);
  }

  async function loadData() {
    setLoading(true);
    setError("");

    // getrecs()/gettitles() only guard against a non-2xx response — a genuine
    // network failure (offline, DNS, an aborted request) makes fetch() itself
    // reject, which used to propagate straight out of this function and skip
    // every statement below, including setLoading(false) — the feed was stuck
    // on the skeleton loader forever with no error shown and no way to retry.
    // Wrapping the whole load in try/catch/finally is what actually surfaces
    // ErrorState (with its retry button, already wired to call this function
    // again) instead of a permanent spinner.
    try {
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
    } catch {
      setError("Couldn't load your feed. Check your connection and try again.");
    } finally {
      setLoading(false);
    }
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
      setsearchunderstood(null);
      setsearchhook(null);
      loadData();
      return;
    }

    setLoading(true);
    setError("");

    const [res, understandRes] = await Promise.all([
      fetch(`${BASE}/api/movies/nlp-search?query=` + encodeURIComponent(q), { headers: authHeaders() }).catch(() => null),
      fetch(`${BASE}/api/movies/nlp-search/understand?query=` + encodeURIComponent(q), { headers: authHeaders() }).catch(() => null)
    ]);

    let finalRes = res;
    if (!finalRes || !finalRes.ok) {
      finalRes = await fetch(`${BASE}/api/movies/search?query=` + encodeURIComponent(q), { headers: authHeaders() }).catch(() => null);
    }

    if (!finalRes || !finalRes.ok) {
      setError("Could not reach the search server. Please try again.");
      setLoading(false);
      return;
    }

    // Only trust the parse for what it was actually parsed FROM — if the
    // primary nlp-search call failed and this fell back to plain title
    // search, the "understood" chips would be describing a query that
    // wasn't the one actually used to rank these results.
    if (res && res.ok && understandRes && understandRes.ok) {
      const { understood } = await understandRes.json();
      const chips = chipsFromUnderstood(understood);
      setsearchunderstood(chips);
      setsearchhook(
        chips ? `You searched for "${q}" — we read that as ${chips.join(", ").toLowerCase()}.` : null
      );
    } else {
      setsearchunderstood(null);
      setsearchhook(null);
    }

    const data = await finalRes.json();
    if (Array.isArray(data)) {
      const normalizedMovies = data.map((item, index) => normalizeMovie(item, index));
      setMovies(normalizedMovies);
    } else {
      setMovies([]);
    }
    setrecentsearches(pushRecentSearch(q));
    setLoading(false);
  }

  function handlePosterError(event) {
    event.currentTarget.onerror = null;
    event.currentTarget.src = FALLBACK_POSTER;
  }

  // "Zero filters, one high-confidence pick" — a random draw from the real,
  // already-scored top of the feed (no vibe/language filter applied), never
  // a separate lower-bar pool. The randomness is which of your best matches
  // you see, not a lowered bar for what counts as a match.
  const SURPRISE_POOL_SIZE = 10;
  async function surpriseMe() {
    let pool = movies.slice(0, SURPRISE_POOL_SIZE);
    if (pool.length === 0) {
      await loadData();
      return;
    }
    const pick = pool[Math.floor(Math.random() * pool.length)];
    setselectedmovie(pick);
  }

  async function handleTrailer(movie) {
    if (movie.trailerUrl) {
      window.open(movie.trailerUrl, "_blank", "noopener,noreferrer");
      return;
    }

    // The feed's own list responses never carry a real trailer URL (fetching
    // it for every card in a feed would be a TMDB call per row) — fetch it
    // lazily, only for the one card actually clicked.
    if (movie.titleId) {
      const res = await fetch(`${BASE}/api/titles/${movie.titleId}/details`, { headers: authHeaders() }).catch(() => null);
      const data = res && res.ok ? await res.json() : null;
      if (data?.trailerUrl) {
        window.open(data.trailerUrl, "_blank", "noopener,noreferrer");
        return;
      }
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

  // Every language actually present in the loaded results — not a fixed
  // list, so the filter never offers an option with zero matches.
  const availablelanguages = [...new Set(
    movies.map((m) => m.originalLanguage).filter(Boolean)
  )].sort();

  // Real TMDB genre taxonomy (Horror, Documentary, ...) — distinct from the
  // "vibe" pills above (Comfort, Bittersweet, ...), which are curated
  // emotional categories, not genres. The backend used to never resolve
  // genre ids to names at all (MovieDTO had no genres field), so this row
  // had nothing real to filter by even though this exact parsing already
  // existed in normalizeMovie waiting for data that never arrived.
  const availablegenres = [...new Set(
    movies.flatMap((m) => m.genres || [])
  )].sort();

  const visiblemovies = [];
  for (let i = 0; i < movies.length; i++) {
    const m = movies[i];
    if (languagefilter !== "All" && m.originalLanguage !== languagefilter) {
      continue;
    }
    if (genrefilter !== "All" && !(m.genres || []).includes(genrefilter)) {
      continue;
    }
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

  // Was keyword-matching against each title's reasons/genres/title text for
  // "comfort"/"family"/"slice" vs. "bitter"/"drama"/"slow" — but "drama" and
  // "family" are common enough genre words that both lists degenerately
  // matched the same already-top-ranked titles as shortlist, in the same
  // order, making three "different" mood rows render identically. These now
  // read the title's own storyVector (Trait.java's comfort/intensity/bitter
  // axes, 0..1, movie-side — see MovieDTO.storyVector) instead of scanning
  // display text for lucky substring hits.
  const comfortlist = visiblemovies
    .filter((m) => (m.storyVector?.comfort ?? 0) >= 0.55 && (m.storyVector?.intensity ?? 1) <= 0.5)
    .slice(0, 4);
  const bitterlist = visiblemovies
    .filter((m) => (m.storyVector?.bitter ?? 0) >= 0.55)
    .slice(0, 4);

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
            const watchStatus = movie.titleId ? watchstatusbymovieid[String(movie.titleId)] : undefined;
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
                    {movie.titleId && watchStatus === "WATCHED" && (
                      <span
                        className="save-icon-button is-watched"
                        title="Watched"
                        aria-label={`${movie.title}: watched`}
                      >
                        <IconFilmCheck />
                      </span>
                    )}
                    {movie.titleId && watchStatus !== "WATCHED" && (
                      <button
                        type="button"
                        className={`save-icon-button ${watchStatus ? "is-saved" : ""}`}
                        onClick={() => handleSetWatchStatus(movie)}
                        aria-label={
                          watchStatus === "WATCHING"
                            ? `Mark ${movie.title} as dropped`
                            : watchStatus === "DROPPED"
                              ? `Clear watch status for ${movie.title}`
                              : `Mark ${movie.title} as currently watching`
                        }
                        title={
                          watchStatus === "WATCHING"
                            ? "Currently watching"
                            : watchStatus === "DROPPED"
                              ? "Dropped"
                              : "Mark as watching"
                        }
                      >
                        {watchStatus === "WATCHING" ? <IconEye /> : watchStatus === "DROPPED" ? "✕" : "▷"}
                      </button>
                    )}
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

        {streak && streak.current > 0 && (
          <div
            className={`streak-badge${streak.activeToday ? "" : " streak-badge--at-risk"}`}
            title={
              streak.activeToday
                ? `Longest streak: ${streak.longest} day${streak.longest === 1 ? "" : "s"}`
                : "Rate something today to keep it alive"
            }
          >
            <IconFlame /> {streak.current}-day streak
            {!streak.activeToday && <span className="streak-badge-nudge">rate something today</span>}
          </div>
        )}
        <h1 style={{
          fontSize: "clamp(2.2rem, 5.5vw, 3.4rem)",
          fontWeight: "var(--fw-hero)",
          letterSpacing: "-0.02em",
          lineHeight: 1.05,
          margin: "6px 0"
        }}>
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

          {movies.length > 0 && (
            <button
              type="button"
              onClick={surpriseMe}
              className="pill surprise-me-btn"
              title="One random pick from your real top matches — no lowered bar, just less choosing."
            >
              <IconShuffle /> Surprise Me
            </button>
          )}

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

          {showsuggestions && query.trim() === "" && recentsearches.length > 0 && (
            <div className="search-suggestions">
              <p className="search-suggestions-label">Recent searches</p>
              {recentsearches.map((s) => (
                <button
                  key={s}
                  type="button"
                  className="search-suggestion-row"
                  onMouseDown={() => {
                    setquery(s);
                    dosearch(s);
                  }}
                >
                  <span className="search-suggestion-icon">↺</span>
                  {s}
                </button>
              ))}
            </div>
          )}
        </div>

        {understoodChips && understoodChips.length > 0 && (
          <div>
            <span style={{ fontSize: "0.86rem", marginRight: "10px" }}>Your vibe:</span>
            {/* Read-only readout, not a filter toggle — these used to be
                clickable but the click handler never actually did anything
                downstream. Reflects the real parsed query now, so pretending
                they're interactive would be its own "presented wrong" bug.
                Only rendered once a real search has actually parsed something —
                showing this before any search ran was itself fabricated data.
                Cyan, not the usual active-chip purple — this isn't a filter
                choice being reflected back at you, it's the algorithm telling
                you something it inferred (this app's --cyan hue, same "here's
                an insight" role it plays on the TasteDNA comparison charts). */}
            <div className="chip-row" style={{ display: "inline-flex", justifyContent: "center", marginTop: "10px" }}>
              {understoodChips.map((c) => (
                <span key={c} className="chip chip-insight">
                  ✓ {c}
                </span>
              ))}
            </div>
          </div>
        )}

        {movies.length > 0 && (
          <div className="tonight-pick-card">
            <img
              src={getPosterUrl(movies[0])}
              alt=""
              aria-hidden="true"
              className="tonight-pick-backdrop"
              onError={handlePosterError}
            />
            <div className="tonight-pick-overlay">
              <div className="tonight-pick-body">
                <p className="section-eyebrow" style={{ textAlign: "left" }}>Tonight's Pick</p>
                <h2>{movies[0].title}</h2>
                <p className="movie-synopsis">
                  {movies[0].synopsis || "No synopsis is available for this title yet."}
                </p>
                <div className="movie-actions">
                  <button type="button" className="trailer-button" onClick={() => handleTrailer(movies[0])}>
                    <span aria-hidden="true">▶</span> Watch trailer
                  </button>
                  <button type="button" className="details-button" onClick={() => handleDetails(movies[0])}>
                    Why this?
                  </button>
                  <button
                    type="button"
                    className={`save-icon-button ${saveditemsbymovieid[String(movies[0].id)] ? "is-saved" : ""}`}
                    onClick={() => handleSave(movies[0])}
                  >
                    {saveditemsbymovieid[String(movies[0].id)] ? "✓ Saved" : "+ Save"}
                  </button>
                </div>
              </div>
              <div className="tonight-pick-ring">
                <MatchRing score={movies[0].matchScore} size={56} />
              </div>
            </div>
          </div>
        )}
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

        {availablegenres.length > 1 && (
          <div className="pill-row" style={{ marginBottom: "var(--sp-2)" }}>
            <button
              type="button"
              onClick={() => setgenrefilter("All")}
              className={`pill${genrefilter === "All" ? " active" : ""}`}
            >
              All genres
            </button>
            {availablegenres.map((g) => (
              <button
                key={g}
                type="button"
                onClick={() => setgenrefilter(g)}
                className={`pill${genrefilter === g ? " active" : ""}`}
              >
                {g}
              </button>
            ))}
          </div>
        )}

        {availablelanguages.length > 1 && (
          <div className="pill-row" style={{ marginBottom: "var(--sp-3)" }}>
            <button
              type="button"
              onClick={() => setlanguagefilter("All")}
              className={`pill${languagefilter === "All" ? " active" : ""}`}
            >
              All languages
            </button>
            {availablelanguages.map((code) => (
              <button
                key={code}
                type="button"
                onClick={() => setlanguagefilter(code)}
                className={`pill${languagefilter === code ? " active" : ""}`}
              >
                {languageLabel(code)}
              </button>
            ))}
          </div>
        )}

        {!loading && dealbreakerhiddencount > 0 && !dealbreakerhidden && (
          <p style={{ fontSize: "0.82rem", color: "var(--text-faint)", marginBottom: "var(--sp-2)" }}>
            {/* --danger is this app's warnings/hard-exclusion hue (see palette
                discipline notes) — tinting just the count, not the sentence,
                keeps it a rare accent rather than a wall of red text. */}
            <strong style={{ color: "var(--danger)" }}>{dealbreakerhiddencount}</strong>{" "}
            title{dealbreakerhiddencount === 1 ? "" : "s"} hidden by your dealbreakers —{" "}
            <button
              type="button"
              onClick={showDealbreakerHidden}
              disabled={dealbreakerhiddenloading}
              style={{ background: "none", border: "none", padding: 0, color: "var(--primary-light)", textDecoration: "underline", cursor: "pointer" }}
            >
              {dealbreakerhiddenloading ? "loading..." : "show anyway"}
            </button>
          </p>
        )}

        {dealbreakerhidden && dealbreakerhidden.length > 0 && (
          <div style={{ marginBottom: "var(--sp-3)" }}>
            <p style={{ fontSize: "0.82rem", color: "var(--text-faint)", marginBottom: "var(--sp-2)" }}>
              Hidden by your dealbreakers — shown because you asked:
            </p>
            <div className="recommendation-grid" style={{ opacity: 0.75 }}>
              {dealbreakerhidden.map((movie) => (
                <article className="recommendation-card" key={`hidden-${movie.id}`}>
                  <div className="recommendation-poster">
                    <img src={getPosterUrl(movie)} alt={`${movie.title} poster`} loading="lazy" onError={handlePosterError} />
                    <div className="poster-gradient" />
                    <div className="match-ring-badge">
                      <MatchRing score={movie.matchScore} />
                    </div>
                  </div>
                  <div className="recommendation-body">
                    <h3>{movie.title}</h3>
                    <p className="movie-synopsis">{movie.synopsis || "No synopsis is available for this title yet."}</p>
                  </div>
                </article>
              ))}
            </div>
          </div>
        )}

        {loading && <SkeletonPosterGrid count={4} />}

        {!loading && error && (
          <ErrorState message={error} onRetry={loadData} />
        )}

        {!loading && !error && visiblemovies.length === 0 && (
          <EmptyState
            title="We searched every universe."
            message={
              genrefilter !== "All" || languagefilter !== "All"
                // The vibe/genre/language filters compound (all three must
                // match), so a zero-result state is often two filters
                // narrowing each other out rather than one being too
                // strict — the old message only ever named the vibe, even
                // when genre or language was the actual reason nothing
                // matched, and "Reset to All Vibes" cleared vibe alone,
                // leaving the real culprit filter still applied.
                ? "That combination of vibe, genre, and language filters is too narrow. Try clearing one."
                : `Nothing matched "${vibe}". Try lowering one emotional filter or switching back to "All".`
            }
            action={
              <button
                type="button"
                onClick={() => { setvibe("All"); setgenrefilter("All"); setlanguagefilter("All"); }}
                className="btn-primary"
              >
                Reset All Filters
              </button>
            }
          />
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

            {hiddengems.length > 0 && renderRow(
              "One You Might Have Missed",
              "Hidden Gem",
              "High audience quality, low popularity — the titles a popularity-sorted feed would bury.",
              hiddengems
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

            {rediscover.length > 0 && renderRow(
              "Rediscover",
              "Worth Revisiting",
              "Titles you rated highly a while back — maybe it's time again.",
              rediscover
            )}

            {collections.length > 0 && (
              <div style={{ marginBottom: "var(--sp-5)" }}>
                <div style={{ marginBottom: "var(--sp-2)" }}>
                  <p className="section-eyebrow">Curated Shelves</p>
                  <h2 style={{ fontSize: "1.4rem", margin: "0 0 4px 0" }}>Pick Tonight's Mood</h2>
                  <p style={{ margin: 0, fontSize: "0.82rem" }}>
                    Each shelf is ranked against a hand-tuned taste profile, the same trait-vector math as everything else here — not a genre tag.
                  </p>
                </div>

                <div className="pill-row" style={{ flexWrap: "wrap" }}>
                  {collections.map((c) => (
                    <button
                      key={c.slug}
                      type="button"
                      className={`pill${selectedcollection === c.slug ? " active" : ""}`}
                      onClick={() => selectCollection(c.slug)}
                      title={c.blurb}
                    >
                      {c.title}
                    </button>
                  ))}
                </div>

                {selectedcollection && collectionloading && (
                  <p style={{ fontSize: "0.82rem", marginTop: "var(--sp-2)" }}>Building your shelf...</p>
                )}

                {selectedcollection && !collectionloading && collectionitems.length === 0 && (
                  <p style={{ fontSize: "0.82rem", marginTop: "var(--sp-2)" }}>
                    Nothing matched closely enough yet — rate a few more titles and try again.
                  </p>
                )}

                {selectedcollection && !collectionloading && collectionitems.length > 0 && (
                  <div style={{ marginTop: "var(--sp-3)" }}>
                    {renderRow(
                      collections.find((c) => c.slug === selectedcollection)?.title || "Your Shelf",
                      "Curated Shelf",
                      collections.find((c) => c.slug === selectedcollection)?.blurb || "",
                      collectionitems
                    )}
                  </div>
                )}
              </div>
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

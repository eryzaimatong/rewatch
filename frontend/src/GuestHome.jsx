import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Logo } from "./BrandMark";
import Ree from "./Ree";
import ErrorState from "./ErrorState";
import Footer from "./Footer";
import { MoodPicker } from "./Cinema";
import { MOOD_REE_POSE } from "./moods";
import useModalA11y from "./useModalA11y";
import { BASE } from "./api";
import "./App.css";

const TMDB = "https://image.tmdb.org/t/p/w500";
const FALLBACK = "https://placehold.co/500x750/16233f/45c5b4?text=Re:Watch";

function posterFor(item) {
  if (!item.posterPath) return FALLBACK;
  return item.posterPath.startsWith("http") ? item.posterPath : `${TMDB}${item.posterPath}`;
}

function genresOf(item) {
  return (Array.isArray(item.genres) ? item.genres : [])
    .map((g) => (typeof g === "string" ? g : g?.name))
    .filter(Boolean);
}

async function fetchPicks(mood) {
  const params = new URLSearchParams();
  if (mood.vibe) params.set("vibe", mood.vibe);
  if (mood.genre) params.set("genre", mood.genre);
  let res = await fetch(`${BASE}/api/movies/popular?${params}`);
  if (!res.ok) throw new Error(`status ${res.status}`);
  let data = await res.json().catch(() => []);
  if (!Array.isArray(data) || data.length === 0) {
    res = await fetch(`${BASE}/api/movies/popular`);
    if (!res.ok) throw new Error(`status ${res.status}`);
    data = await res.json().catch(() => []);
  }
  return Array.isArray(data) ? data.slice(0, 3) : [];
}

function SignupPrompt({ title, onClose }) {
  const ref = useModalA11y(onClose);
  return (
    <div className="modal-overlay" onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal-card modal-card--narrow guest-prompt" ref={ref} role="dialog" aria-modal="true" aria-labelledby="guest-prompt-title" tabIndex={-1}>
        <button type="button" className="modal-close guest-prompt-close" onClick={onClose} aria-label="Close">×</button>
        <Ree pose="excited" size={150} />
        <h2 id="guest-prompt-title">Keep {title ? `“${title}”` : "this"} for later?</h2>
        <p>Make a free account and Ree remembers what you save, rate and love — so every pick after this one fits you better.</p>
        <div className="guest-prompt-actions">
          <Link to="/login?mode=register" className="btn-primary btn-block">Create my account</Link>
          <Link to="/login" className="btn-block guest-prompt-link">I already have one</Link>
        </div>
      </div>
    </div>
  );
}

/**
 * The signed-out front door. No form first: the visitor tells Ree how they want
 * to feel and gets real picks straight away (the same public feed the app
 * uses). An account is only asked for when they want to keep something.
 */
export default function GuestHome() {
  const [mood, setMood] = useState(null);
  const [picks, setPicks] = useState([]);
  const [status, setStatus] = useState("idle"); // idle | loading | ready | error
  const [slow, setSlow] = useState(false);
  const [prompt, setPrompt] = useState(null);
  const [hop, setHop] = useState(0);
  const resultsRef = useRef(null);
  const reqId = useRef(0);
  const [small, setSmall] = useState(() => typeof window !== "undefined" && window.innerWidth < 900);
  useEffect(() => {
    const onResize = () => setSmall(window.innerWidth < 900);
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  async function pick(m) {
    const id = ++reqId.current;
    setMood(m);
    setHop((n) => n + 1);
    setStatus("loading");
    setSlow(false);
    try {
      const data = await fetchPicks(m);
      if (id !== reqId.current) return;
      setPicks(data);
      setStatus("ready");
      requestAnimationFrame(() => resultsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" }));
    } catch {
      if (id === reqId.current) setStatus("error");
    }
  }

  useEffect(() => {
    if (status !== "loading") return undefined;
    const t = setTimeout(() => setSlow(true), 5000);
    return () => clearTimeout(t);
  }, [status]);

  const reePose = status === "error" ? "sad" : mood ? MOOD_REE_POSE[mood.key] : "default";

  return (
    <div className="guest">
      <header className="guest-bar">
        <Link to="/" className="guest-brand" aria-label="Re:Watch home"><Logo size={34} /></Link>
        <nav className="guest-bar-actions" aria-label="Account">
          <Link to="/login" className="guest-signin">Sign in</Link>
          <Link to="/login?mode=register" className="cx-cta guest-join">Sign up</Link>
        </nav>
      </header>

      <main id="main-content">
      <section className="guest-hero" aria-labelledby="guest-title">
        <div className="guest-hero-copy">
          <p className="cx-eyebrow"><span className="cx-eyebrow-dot" aria-hidden="true" />Try it — no account needed</p>
          <h1 id="guest-title">How do you want to <em>feel</em> tonight?</h1>
          <p className="guest-sub">Skip the scrolling. Tell Ree a mood and she’ll pick stories that fit it — movies, series, anime and K-drama.</p>
        </div>
        <div className="guest-hero-ree">
          <Ree pose={reePose} size={small ? 170 : 260} hop={hop} />
        </div>
      </section>

      <section className="guest-moods" aria-label="Pick a mood">
        <MoodPicker activeKey={mood?.key} onPick={pick} />
      </section>

      <aside className="guest-night">
        <Ree pose="cinema" size={84} decorative aura={false} />
        <div>
          <h2>Picking with friends?</h2>
          <p>Start a Movie Night: everyone answers five quick questions, Ree finds the three you’ll all say yes to.</p>
        </div>
        <Link to="/night" className="cx-ghost guest-night-cta">Start a Movie Night</Link>
      </aside>

      <section className="guest-results" ref={resultsRef} aria-live="polite">
        {status === "loading" && (
          <p className="guest-note">
            Ree is flipping through stories…
            {slow && <> The server naps when it’s quiet, so the first pick can take a minute to wake up.</>}
          </p>
        )}
        {status === "error" && (
          <ErrorState title="Ree couldn’t reach the stories." message="Check your connection, or give the server a moment to wake up." onRetry={() => mood && pick(mood)} />
        )}
        {status === "ready" && picks.length > 0 && (
          <>
            <h2 className="guest-results-title">Ree’s {mood.name.toLowerCase()} picks</h2>
            <ul className="guest-picks">
              {picks.map((p, i) => (
                <li key={p.id ?? i} className="guest-pick" style={{ "--i": i }}>
                  <img src={posterFor(p)} alt={`${p.title} poster`} loading="lazy" onError={(e) => { e.currentTarget.src = FALLBACK; }} />
                  <div className="guest-pick-body">
                    <h3>{p.title}</h3>
                    <p className="guest-pick-meta">{[p.year, ...genresOf(p).slice(0, 2)].filter(Boolean).join(" · ")}</p>
                    {p.overview && <p className="guest-pick-syn">{p.overview}</p>}
                    <button type="button" className="cx-ghost" onClick={() => setPrompt(p.title)}>Save to watchlist</button>
                  </div>
                </li>
              ))}
            </ul>
            <p className="guest-fine">
              Picks get sharper once Ree knows you. <Link to="/login?mode=register">Create a free account</Link> to teach her your taste.
            </p>
          </>
        )}
        {status === "ready" && picks.length === 0 && <p className="guest-note">Ree found nothing for that mood yet — try another.</p>}
      </section>
      </main>

      <Footer />
      {prompt != null && <SignupPrompt title={prompt} onClose={() => setPrompt(null)} />}
    </div>
  );
}

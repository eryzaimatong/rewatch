import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Logo } from "./BrandMark";
import Ree from "./Ree";
import ErrorState from "./ErrorState";
import Footer from "./Footer";
import { BASE } from "./api";
import "./App.css";

const TMDB = "https://image.tmdb.org/t/p/w500";
const FALLBACK = "https://placehold.co/500x750/16233f/45c5b4?text=Re:Watch";
const POLL_MS = 6000;

// Ree reacts to each answer she's given on the way through.
const QUESTION_POSE = ["curious", "excited", "thinking", "lovestory", "cinema"];

const posterFor = (p) => (!p ? FALLBACK : p.startsWith("http") ? p : `${TMDB}${p}`);
const memberKey = (code) => `rewatch:night:${code}`;

function readMember(code) {
  try {
    return localStorage.getItem(memberKey(code));
  } catch {
    return null;
  }
}
function rememberMember(code, id, name) {
  try {
    localStorage.setItem(memberKey(code), JSON.stringify({ id, name }));
  } catch {
    /* private mode: the page still works, you just rejoin on reload */
  }
}

async function api(path, options) {
  const res = await fetch(`${BASE}/api/nights${path}`, {
    ...options,
    headers: { "Content-Type": "application/json" }
  });
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    const err = new Error(body?.message ?? `Request failed (${res.status})`);
    err.status = res.status;
    throw err;
  }
  return body;
}

function Shell({ children }) {
  return (
    <div className="guest night">
      <header className="guest-bar">
        <Link to="/" className="guest-brand" aria-label="Re:Watch home"><Logo size={34} /></Link>
        <nav className="guest-bar-actions" aria-label="Account">
          <Link to="/login" className="guest-signin">Sign in</Link>
        </nav>
      </header>
      <main id="main-content">{children}</main>
      <Footer />
    </div>
  );
}

/** Name + five one-tap mood questions. Used both to start a night and to join one. */
function Quiz({ title, intro, submitLabel, onSubmit }) {
  const [questions, setQuestions] = useState(null);
  const [name, setName] = useState("");
  const [answers, setAnswers] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [loadError, setLoadError] = useState(false);

  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let live = true;
    api("/questions")
      .then((q) => live && setQuestions(q))
      .catch(() => live && setLoadError(true));
    return () => { live = false; };
  }, [attempt]);
  const load = () => { setLoadError(false); setAttempt((n) => n + 1); };

  if (loadError) return <ErrorState title="Ree couldn’t reach the questions." message="Give the server a moment to wake up." onRetry={load} />;
  if (!questions) return <p className="guest-note">Ree is getting the questions ready…</p>;

  const step = answers.length; // index of the question being asked
  const ready = name.trim().length > 0;
  const done = step >= questions.length;
  const pose = error ? "sad" : done ? "celebrating" : ready ? QUESTION_POSE[step] ?? "default" : "default";

  async function submit() {
    setBusy(true);
    setError("");
    try {
      await onSubmit(name.trim(), answers);
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  }

  return (
    <section className="night-card" aria-labelledby="night-quiz-title">
      <div className="night-ree"><Ree pose={pose} size={170} /></div>
      <h1 id="night-quiz-title">{title}</h1>
      <p className="guest-sub night-intro">{intro}</p>

      <label className="night-name">
        <span>Your name</span>
        <input value={name} maxLength={24} onChange={(e) => setName(e.target.value)} placeholder="So your friends know it’s you" autoComplete="given-name" />
      </label>

      {ready && !done && (
        <div className="night-q" key={step}>
          <p className="night-q-count">Question {step + 1} of {questions.length}</p>
          <h2>{questions[step].prompt}</h2>
          <div className="night-options" role="group" aria-label={questions[step].prompt}>
            {questions[step].options.map((label, i) => (
              <button key={label} type="button" className="night-option" onClick={() => setAnswers([...answers, i])}>
                {label}
              </button>
            ))}
          </div>
          {step > 0 && (
            <button type="button" className="night-back" onClick={() => setAnswers(answers.slice(0, -1))}>← Back</button>
          )}
        </div>
      )}

      {ready && done && (
        <div className="night-q">
          <h2>All set, {name.trim()}.</h2>
          <button type="button" className="cx-cta" onClick={submit} disabled={busy}>
            {busy ? "One moment…" : submitLabel}
          </button>
          <button type="button" className="night-back" onClick={() => setAnswers(answers.slice(0, -1))} disabled={busy}>← Change my last answer</button>
        </div>
      )}
      {error && <p className="night-error" role="alert">{error}</p>}
    </section>
  );
}

export function NightStart() {
  const navigate = useNavigate();
  return (
    <Shell>
      <Quiz
        title="Movie Night"
        intro="Everyone answers five quick questions on their own phone — no accounts. Ree finds three stories the whole group can say yes to."
        submitLabel="Start the night"
        onSubmit={async (name, answers) => {
          const r = await api("", { method: "POST", body: JSON.stringify({ name, answers }) });
          rememberMember(r.code, r.memberId, name);
          navigate(`/night/${r.code}`);
        }}
      />
    </Shell>
  );
}

// Raw scores run low for a group that pulls in opposite directions, so show a
// plain-language band next to the bar instead of a bare percentage.
function band(score) {
  if (score >= 65) return "Great fit";
  if (score >= 50) return "Good fit";
  if (score >= 35) return "Okay";
  return "A stretch";
}

function ScoreBar({ name, score }) {
  return (
    <li className="night-bar">
      <span className="night-bar-name">{name}</span>
      <span className="night-bar-track" aria-hidden="true"><i style={{ width: `${Math.max(4, Math.min(100, score))}%` }} /></span>
      <span className="night-bar-num">{band(score)}</span>
    </li>
  );
}

export function NightRoom() {
  const { code } = useParams();
  const upper = (code ?? "").toUpperCase();
  const [night, setNight] = useState(null);
  const [state, setState] = useState("loading"); // loading | ready | gone | error
  const [me, setMe] = useState(() => {
    const raw = readMember(upper);
    try { return raw ? JSON.parse(raw) : null; } catch { return null; }
  });
  const [copied, setCopied] = useState(false);
  const timer = useRef(null);

  const refresh = useCallback(async () => {
    try {
      setNight(await api(`/${upper}`));
      setState("ready");
    } catch (e) {
      setState((s) => (e.status === 404 ? "gone" : s === "ready" ? "ready" : "error"));
    }
  }, [upper]);

  useEffect(() => {
    refresh();
    timer.current = setInterval(() => { if (!document.hidden) refresh(); }, POLL_MS);
    return () => clearInterval(timer.current);
  }, [refresh]);

  const link = `${window.location.origin}/night/${upper}`;
  async function share() {
    const data = { title: "Re:Watch Movie Night", text: "Answer 5 quick questions and Ree will pick what we all watch:", url: link };
    try {
      if (navigator.share) { await navigator.share(data); return; }
      await navigator.clipboard.writeText(link);
      setCopied(true);
      setTimeout(() => setCopied(false), 2200);
    } catch { /* user cancelled */ }
  }

  if (state === "loading") return <Shell><p className="guest-note">Opening the night…</p></Shell>;
  if (state === "error") return <Shell><ErrorState title="Couldn’t open this night." message="Check your connection — or the server may still be waking up." onRetry={refresh} /></Shell>;
  if (state === "gone") {
    return (
      <Shell>
        <div className="feed-state">
          <Ree mood="lost" size={150} className="state-ree" />
          <h1>This movie night has ended.</h1>
          <p>Nights last 24 hours. Start a fresh one for tonight.</p>
          <Link to="/night" className="cx-cta" style={{ textDecoration: "none" }}>Start a Movie Night</Link>
        </div>
      </Shell>
    );
  }

  const joined = me && night.members.some((m) => m.id === me.id);
  if (!joined) {
    const full = night.members.length >= night.maxMembers;
    if (full) {
      return (
        <Shell>
          <div className="feed-state">
            <Ree mood="error" size={150} className="state-ree" />
            <h1>This night is full.</h1>
            <p>Up to {night.maxMembers} people can join one night.</p>
            <Link to="/night" className="cx-cta" style={{ textDecoration: "none" }}>Start your own</Link>
          </div>
        </Shell>
      );
    }
    return (
      <Shell>
        <Quiz
          title="You’re invited to a Movie Night"
          intro={`${night.members[0]?.name ?? "A friend"} started one${night.members.length > 1 ? ` with ${night.members.length - 1} other${night.members.length > 2 ? "s" : ""}` : ""}. Five quick questions and you’re in.`}
          submitLabel="Join the night"
          onSubmit={async (name, answers) => {
            const r = await api(`/${upper}/join`, { method: "POST", body: JSON.stringify({ name, answers }) });
            rememberMember(upper, r.memberId, name);
            setMe({ id: r.memberId, name });
            await refresh();
          }}
        />
      </Shell>
    );
  }

  const picks = night.picks ?? [];
  const waiting = night.members.length < 2;
  const tough = picks.length > 0 && picks[0].groupScore < 40;
  return (
    <Shell>
      <section className="night-room" aria-labelledby="night-room-title">
        <div className="night-room-head">
          <Ree pose={waiting ? "sleepy" : "celebrating"} size={150} />
          <div>
            <p className="cx-eyebrow"><span className="cx-eyebrow-dot" aria-hidden="true" />Movie Night · <span className="mono">{upper}</span></p>
            <h1 id="night-room-title">{waiting ? "Waiting for your friends…" : tough ? "Tough crowd. Here’s the fairest three." : "Ree found your three."}</h1>
            <p className="guest-sub">
              {waiting
                ? "Send the link. As soon as one more person answers, Ree picks for the group."
                : tough
                  ? "You’re pulling in different directions, so Ree weighed everyone equally and picked the least painful compromise."
                  : "Chosen so nobody is stuck with something they’d hate — agreement beats a single big fan."}
            </p>
            <div className="night-actions">
              <button type="button" className="cx-cta" onClick={share}>{copied ? "Link copied ✓" : "Invite friends"}</button>
              <span className="night-count" aria-live="polite">{night.members.length} of {night.maxMembers} in</span>
            </div>
          </div>
        </div>

        <ul className="night-members" aria-label="Who's in">
          {night.members.map((m) => (
            <li key={m.id} className={me?.id === m.id ? "is-me" : ""}>{m.name}{me?.id === m.id ? " (you)" : ""}</li>
          ))}
        </ul>

        {!waiting && picks.length > 0 && (
          <ol className="night-picks">
            {picks.map((p, i) => (
              <li key={p.titleId} className="night-pick" style={{ "--i": i }}>
                <span className="night-pick-rank" aria-hidden="true">{i + 1}</span>
                <img src={posterFor(p.posterPath)} alt={`${p.title} poster`} loading="lazy" onError={(e) => { e.currentTarget.src = FALLBACK; }} />
                <div className="night-pick-body">
                  <h2>{p.title}</h2>
                  <p className="guest-pick-meta">{[p.year, ...(p.genres ?? []).slice(0, 2)].filter(Boolean).join(" · ")}</p>
                  {p.reason && <p className="night-why"><strong>Ree:</strong> you all lean toward <em>{p.reason.toLowerCase()}</em>, and this one delivers.</p>}
                  <ul className="night-bars" aria-label={`How well ${p.title} fits each person`}>
                    {p.members.map((m) => <ScoreBar key={m.name} name={m.name} score={m.score} />)}
                  </ul>
                </div>
              </li>
            ))}
          </ol>
        )}

        <p className="guest-fine">
          Want Ree to know your taste for real? <Link to="/login?mode=register">Create a free account</Link>.
        </p>
      </section>
    </Shell>
  );
}

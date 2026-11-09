import { useState } from "react";
import MatchRing from "./MatchRing";
import { IconPlay, IconArrowRight, IconChevron } from "./RailIcons";

/**
 * Cinematic home-feed sections, modelled on the "Inspo" board:
 *  - CinemaHero  — full-bleed switchable hero (giant title, watch-now pill,
 *                  side tab) + a "story traits" card row for the active pick
 *  - TitleStrip  — landscape thumbnail strip (the Wednesday "Episodes" row)
 *  - SpotlightWheel — "Selected films": centred poster on a dial with
 *                  tilted neighbours, arrows and medallions
 * Pure presentation: data and handlers come from MovieFeed.
 */

function SectionHead({ label, dots = 0, active = 0, onDot }) {
  return (
    <div className="cx-head">
      <span className="cx-pill">{label}</span>
      <span className="cx-rule" aria-hidden="true" />
      {dots > 1 && (
        <span className="cx-dots" role="tablist" aria-label={`${label} pages`}>
          {Array.from({ length: dots }, (_, i) => (
            <button
              key={i}
              type="button"
              role="tab"
              aria-selected={i === active}
              aria-label={`Show ${i + 1} of ${dots}`}
              className={`cx-dot${i === active ? " is-active" : ""}`}
              onClick={() => onDot?.(i)}
            />
          ))}
        </span>
      )}
    </div>
  );
}

export function CinemaHero({ movies, backdropOf, posterOf, onPosterError, onTrailer, onDetails, onSave, isSaved }) {
  const picks = movies.slice(0, 5);
  const [index, setIndex] = useState(0);
  if (picks.length === 0) return null;

  const i = Math.min(index, picks.length - 1);
  const movie = picks[i];
  const go = (n) => setIndex((n + picks.length) % picks.length);
  const drivers = movie.explanation?.drivers ?? [];
  const tensions = movie.explanation?.tensions ?? [];
  const traitCards = [
    ...drivers.map((d) => ({ ...d, kind: "driver" })),
    ...tensions.map((t) => ({ ...t, kind: "tension" }))
  ];
  const saved = isSaved(movie);

  return (
    <>
      <section className="cx-hero" aria-label="Tonight's picks" aria-roledescription="carousel">
        <div className="cx-hero-stage" key={movie.id}>
          <img
            src={backdropOf(movie)}
            alt=""
            aria-hidden="true"
            className="cx-hero-art"
            onError={onPosterError}
          />
          <div className="cx-hero-shade" aria-hidden="true" />

          <div className="cx-hero-copy">
            <p className="cx-eyebrow">
              <span className="cx-eyebrow-dot" aria-hidden="true" />
              Tonight's pick <span className="mono">{i + 1}/{picks.length}</span>
            </p>
            <h2 className="cx-title">{movie.title}</h2>
            <p className="cx-meta">
              {movie.matchScore != null && (
                <span className="cx-meta-match">
                  <span className="mono">{Math.round(movie.matchScore)}%</span> match
                </span>
              )}
              {movie.year && <span>{movie.year}</span>}
              {movie.genres.slice(0, 3).map((g) => (
                <span key={g}>{g}</span>
              ))}
            </p>
            <p className="cx-synopsis">
              {movie.synopsis || "No synopsis is available for this title yet."}
            </p>
            {drivers.length > 0 && (
              <p className="cx-because">
                <span>Fits you because</span> {drivers.slice(0, 2).map((d) => d.label).join(" · ")}
              </p>
            )}
            <div className="cx-actions">
              <button type="button" className="cx-cta" onClick={() => onTrailer(movie)}>
                <IconPlay /> Watch now
              </button>
              <button type="button" className="cx-square" onClick={() => onDetails(movie)} aria-label={`Why ${movie.title} fits`} title="Why this fits">
                <IconArrowRight />
              </button>
              <button
                type="button"
                className={`cx-ghost${saved ? " is-saved" : ""}`}
                onClick={() => onSave(movie)}
              >
                {saved ? "✓ Saved" : "+ Watchlist"}
              </button>
            </div>
          </div>

          <button type="button" className="cx-tab" onClick={() => go(i + 1)} aria-label="Next pick">
            <IconChevron dir="left" />
          </button>
          <div className="cx-hero-dots">
            <button type="button" className="cx-arrow" onClick={() => go(i - 1)} aria-label="Previous pick">
              <IconChevron dir="left" />
            </button>
            {picks.map((p, n) => (
              <button
                key={p.id}
                type="button"
                className={`cx-dot${n === i ? " is-active" : ""}`}
                onClick={() => setIndex(n)}
                aria-label={`Show ${p.title}`}
                aria-current={n === i}
              />
            ))}
            <button type="button" className="cx-arrow" onClick={() => go(i + 1)} aria-label="Next pick">
              <IconChevron dir="right" />
            </button>
          </div>
        </div>
      </section>

      {traitCards.length > 0 && (
        <section className="cx-section" aria-label="Story traits">
          <SectionHead label="Story traits :" />
          <div className="cx-scroll">
            {traitCards.map((t) => (
              <article key={`${t.kind}-${t.trait}`} className={`cx-card cx-trait is-${t.kind}`}>
                <div className="cx-trait-art">
                  <img src={posterOf(movie)} alt="" aria-hidden="true" loading="lazy" onError={onPosterError} />
                  <span className="cx-trait-value mono">
                    {t.contribution > 0 ? "+" : ""}
                    {t.contribution.toFixed(1)}
                  </span>
                </div>
                <h3>{t.label}</h3>
                <p>{t.kind === "driver" ? "Pulls you toward it" : "Pushes against your taste"}</p>
              </article>
            ))}
          </div>
        </section>
      )}
    </>
  );
}

export function TitleStrip({ movies, backdropOf, onPosterError, onDetails, label = "More tonight" }) {
  const items = movies.slice(0, 10);
  if (items.length === 0) return null;
  return (
    <section className="cx-section" aria-label={label}>
      <SectionHead label={label} />
      <div className="cx-scroll cx-strip">
        {items.map((m, n) => (
          <button key={m.id} type="button" className="cx-thumb" onClick={() => onDetails(m)}>
            <span className="cx-thumb-frame">
              <img src={backdropOf(m)} alt="" loading="lazy" onError={onPosterError} />
              <span className="cx-thumb-play" aria-hidden="true"><IconPlay /></span>
              {m.matchScore != null && <span className="cx-thumb-score mono">{Math.round(m.matchScore)}%</span>}
            </span>
            <span className="cx-thumb-title">
              <span className="mono">{n + 1}.</span> {m.title}
            </span>
          </button>
        ))}
      </div>
    </section>
  );
}

export function SpotlightWheel({ movies, posterOf, onPosterError, onTrailer, onDetails }) {
  const items = movies.slice(0, 9);
  const [active, setActive] = useState(0);
  if (items.length < 3) return null;

  const n = items.length;
  const a = Math.min(active, n - 1);
  const wrap = (v) => ((v % n) + n) % n;
  const current = items[a];

  return (
    <section className="cx-wheel" aria-label="Selected films">
      <div className="cx-wheel-head">
        <span className="cx-pill cx-pill-lg">Selected films</span>
        <IconChevron dir="down" className="cx-wheel-chevron" />
      </div>

      <div className="cx-wheel-stage">
        <div className="cx-dial" aria-hidden="true">
          <div className="cx-dial-rim" />
          <svg className="cx-dial-text" viewBox="0 0 1000 500" preserveAspectRatio="xMidYMax meet">
            <defs>
              <path id="cx-arc" d="M 100 500 A 400 400 0 0 1 900 500" />
            </defs>
            <text>
              <textPath href="#cx-arc" startOffset="50%" textAnchor="middle">
                RE:WATCH · STORIES CHOSEN FOR HOW YOU FEEL
              </textPath>
            </text>
          </svg>
        </div>

        <div className="cx-medallion cx-medallion-l" aria-label={`${Math.round(current.matchScore ?? 0)} percent match`}>
          <MatchRing score={current.matchScore} size={64} />
        </div>
        <div className="cx-medallion cx-medallion-r" aria-label={`Released ${current.year ?? "unknown"}`}>
          <span className="mono">{current.year ?? "—"}</span>
        </div>

        <div className="cx-fan">
          {[-2, -1, 0, 1, 2].map((off) => {
            const m = items[wrap(a + off)];
            return (
              <div key={`${off}-${m.id}`} className={`cx-fan-item off-${off}`}>
                <button
                  type="button"
                  className="cx-fan-poster"
                  onClick={() => (off === 0 ? onDetails(m) : setActive(wrap(a + off)))}
                  aria-label={off === 0 ? `Open ${m.title}` : `Bring ${m.title} to the front`}
                >
                  <img src={posterOf(m)} alt={`${m.title} poster`} loading="lazy" onError={onPosterError} />
                </button>
                {off === 0 && (
                  <button type="button" className="cx-fan-play" onClick={() => onTrailer(m)} aria-label={`Play ${m.title} trailer`}>
                    <IconPlay />
                  </button>
                )}
              </div>
            );
          })}
        </div>

        <button type="button" className="cx-wheel-arrow is-prev" onClick={() => setActive(wrap(a - 1))} aria-label="Previous film">
          <IconChevron dir="left" />
        </button>
        <button type="button" className="cx-wheel-arrow is-next" onClick={() => setActive(wrap(a + 1))} aria-label="Next film">
          <IconChevron dir="right" />
        </button>
      </div>

      <p className="cx-wheel-caption" aria-live="polite">
        <strong>{current.title}</strong>
        <span>{[current.year, ...current.genres.slice(0, 2)].filter(Boolean).join(" · ")}</span>
      </p>
    </section>
  );
}

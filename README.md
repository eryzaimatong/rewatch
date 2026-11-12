<div align="center">

# Re:Watch

**Stories chosen for how you feel.**

A movie / TV / anime / K-drama discovery app that recommends by *emotion and storytelling* — not by genre or popularity — using a real, explainable taste model.

[![CI](https://github.com/eryzaimatong/rewatch/actions/workflows/ci.yml/badge.svg)](https://github.com/eryzaimatong/rewatch/actions/workflows/ci.yml)
![License](https://img.shields.io/badge/license-MIT-45c5b4)
![React](https://img.shields.io/badge/React-19-61dafb?logo=react&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3-6db33f?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql&logoColor=white)

**[Live demo → rewatch-stories.vercel.app](https://rewatch-stories.vercel.app)**

<sub>The backend runs on a free tier and sleeps when idle, so the very first request can take a couple of minutes to wake it. After that it's quick.</sub>

<br />

<img src="docs/screenshots/03-home.png" alt="Re:Watch home: a cinematic 'Tonight's pick' hero with a match score and Ree's take" width="900" />

</div>

---

## The idea

Choosing what to watch has turned into scrolling. Recommendation systems optimise for popularity and engagement, so two titles can share a genre tag and feel nothing alike.

Re:Watch asks a different question — not *"what genre do you like?"* but ***"how do you want to feel?"*** Every title gets an emotional fingerprint, every person gets a **Taste DNA**, and every match score comes with the reasons that produced it.

Meet **Ree**, the app's chameleon companion. Ree is a living part of the page, not a sticker: she floats and breathes, looks toward your cursor, hops when poked, changes pose to match the story on screen (a mystery gets her detective hat), and dozes off if you leave her alone. She shows up where she has something to say — the hero, mood picking, onboarding, empty and error states.

## Screenshots

<table>
  <tr>
    <td width="50%"><img src="docs/screenshots/16-try-first.png" alt="Signed-out home: pick a mood and Ree returns real picks, no account needed" /><br /><sub><b>Try it first.</b> No sign-up wall: pick a mood, get real picks. An account is only asked for when you want to keep something.</sub></td>
    <td width="50%"><img src="docs/screenshots/17-movie-night.png" alt="Movie Night results: three titles chosen fairly for a group, with a fit bar per person" /><br /><sub><b>Movie Night.</b> Friends open one link, answer five questions, and Ree picks three everyone can say yes to.</sub></td>
  </tr>
  <tr>
    <td width="50%"><img src="docs/screenshots/05-mood-picker.png" alt="Mood picker: six feelings, each with its own Ree" /><br /><sub><b>Start with a feeling, not a genre.</b> Six moods drive the recommendation filters.</sub></td>
    <td width="50%"><img src="docs/screenshots/07-match-explained.png" alt="A match explained: radar chart comparing your taste to the title, with the drivers behind the score" /><br /><sub><b>Every match explains itself.</b> Your Taste DNA vs. the story, trait by trait.</sub></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/08-taste-profile.png" alt="Taste DNA profile with radar chart, evolution timeline and every trait" /><br /><sub><b>Taste DNA.</b> A profile that is replayed from your rating history and evolves with it.</sub></td>
    <td><img src="docs/screenshots/06-selected-films.png" alt="Selected films dial with a centred poster" /><br /><sub><b>Selected films.</b> A dial of your top matches.</sub></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/09-community.png" alt="Community: people with similar taste and an activity feed" /><br /><sub><b>Community.</b> People with a similar Taste DNA, and what they're watching.</sub></td>
    <td><img src="docs/screenshots/10-achievements.png" alt="Achievements page with progress" /><br /><sub><b>Achievements & streaks.</b></sub></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/01-sign-in.png" alt="Sign-in screen with Ree" /><br /><sub><b>Sign in.</b></sub></td>
    <td><img src="docs/screenshots/02-onboarding.png" alt="Onboarding: pick five stories you've loved" /><br /><sub><b>Onboarding.</b> Pick five stories you've loved; Ree reads the rest.</sub></td>
  </tr>
</table>

<details>
<summary><b>Phone layouts, Watchlist and Settings</b></summary>
<br />

<table>
  <tr>
    <td width="33%"><img src="docs/screenshots/13-mobile-home.png" alt="Mobile home" /></td>
    <td width="33%"><img src="docs/screenshots/14-mobile-moods.png" alt="Mobile mood picker" /></td>
    <td width="33%"><img src="docs/screenshots/15-mobile-profile.png" alt="Mobile Taste DNA profile" /></td>
  </tr>
</table>

<table>
  <tr>
    <td width="50%"><img src="docs/screenshots/11-watchlist.png" alt="Watchlist and today's pick" /></td>
    <td width="50%"><img src="docs/screenshots/12-settings.png" alt="Settings with grouped navigation" /></td>
  </tr>
</table>
</details>

> **About the images:** they're captured from the real app running against a real backend and database, with a small demo cast created through the public API. Poster artwork is a generated placeholder (the title on a gradient) because the capture environment can't reach TMDB. Everything else — layout, data, scores — is real. Regenerate them any time with `npm run screenshots` (see below).

## Features

- **Try before you sign up** — the signed-out home lets anyone pick a mood and see real recommendations immediately; the sign-up prompt only appears when they try to save something.
- **Movie Night** — a no-account, shareable group pick. Each friend answers five quick mood questions on their own phone; titles are scored per person and ranked by *mean minus spread*, so agreement beats one enthusiastic veto. Nights hold up to 8 people and expire after 24 hours.
- **Mood discovery** — pick how you want to feel, or describe it in plain language ("something comforting after exams"). Natural-language queries are parsed into the same filters; there is no LLM in the recommendation path.
- **Taste DNA** — ten storytelling traits (pacing, found family, bittersweet drama, humour…) per person, rebuilt by replaying your rating log rather than mutating a number in place.
- **Explainable matches** — each score is the sum of per-trait contributions, so the "why this fits" breakdown always adds up to the number shown.
- **Taste evolution** — your profile plotted over time, with milestones, plus a monthly **Wrapped** recap you can export as a share card.
- **Discovery shelves** — hidden gems, "because you loved X", similar-DNA titles, curated collections, and a daily double feature.
- **Social** — public profiles, follow, DNA-based match suggestions, an activity feed, reviews with likes and comments, and a shareable **compatibility check** that works without an account.
- **Watchlist** — saved titles, custom shelves, watch status; shelves can be made public and followed.
- **Playful extras** — "Roast my taste", achievements, rating streaks, a Ctrl+K command palette.
- **Accessible & installable** — keyboard-first, screen-reader friendly, WCAG 2.1 AA checked with axe-core, reduced-motion and high-contrast options, installable as a PWA.

## Tech stack

| Layer | Tech |
| --- | --- |
| Frontend | React 19, Vite, React Router, Framer Motion, plain CSS (no UI framework), Inter via `@fontsource` |
| Backend | Spring Boot 3, Java 17, Spring Security (BCrypt + stateless JWT), Flyway |
| Database | PostgreSQL 16 |
| Catalog data | [TMDB](https://www.themoviedb.org/) API plus a hand-authored genre/keyword lexicon |
| API docs | OpenAPI / Swagger UI, generated from the controllers (`/swagger-ui/index.html`) |
| Testing | Vitest + happy-dom (frontend), JUnit (backend), Playwright (screenshots), axe-core (accessibility audits) |
| Hosting | Vercel (frontend), Render (backend, free tier), Neon (Postgres) |

## Getting started

### Option A — Docker (everything at once)

```bash
cp .env.example .env      # then fill in DB_PASSWORD, TMDB_API_KEY, JWT_SECRET, mail settings
docker compose up --build
```

Open <http://localhost:5173>. The API is on <http://localhost:8080>.

### Option B — run the pieces yourself

You need Java 17, Node 20+, and a local PostgreSQL.

```bash
# 1. database
createdb rewatch_db

# 2. backend  (http://localhost:8080)
cd backend-java
DB_USERNAME=postgres DB_PASSWORD=... TMDB_API_KEY=... ./mvnw spring-boot:run

# 3. frontend (http://localhost:5173) — in a second terminal
cd frontend
npm install
npm run dev
```

The frontend reads the API address from `VITE_API_BASE` (defaults to `http://localhost:8080`). A free TMDB API key is needed to fill the title catalog.

### Environment variables

| Variable | Where | Purpose |
| --- | --- | --- |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | backend | PostgreSQL connection |
| `TMDB_API_KEY` | backend | Catalog data and posters |
| `JWT_SECRET` | backend | Signs login tokens — generate with `openssl rand -base64 48` |
| `CORS_ALLOWED_ORIGINS` | backend | Comma-separated browser origins allowed to call the API |
| `FRONTEND_BASE_URL` | backend | Origin used in password-reset links |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | backend | Gmail address + App Password for reset emails |
| `ADMIN_EMAILS` | backend | Comma-separated emails promoted to admin (optional) |
| `VITE_API_BASE` | frontend (build time) | Where the browser finds the API |
| `VITE_SITE_URL` | frontend (build time) | Canonical URL for link previews |

See [`.env.example`](.env.example) and [`DEPLOYMENT.md`](DEPLOYMENT.md) for the full picture.

## Tests and quality checks

```bash
# frontend
cd frontend
npm run lint
npm test
npm run build

# backend
cd backend-java
./mvnw test
```

CI runs all of the above on every push and pull request.

### Regenerating the screenshots

The screenshots in `docs/screenshots/` come from one reproducible script. With the backend and a built frontend running:

```bash
cd frontend
npm run build && npx vite preview --port 5173     # in one terminal
npx playwright install chromium                    # first time only
npm run screenshots                                # in another
```

`APP_URL`, `API_URL` and `OUT_DIR` can be overridden; see the header of [`frontend/scripts/capture-screenshots.mjs`](frontend/scripts/capture-screenshots.mjs).

## Project structure

```
rewatch/
├── frontend/                 React + Vite single-page app
│   ├── src/                  Pages, components, styles (App.css + brand.css)
│   │   ├── assets/ree/       Ree the chameleon: poses and mood artwork
│   │   ├── Ree.jsx           The mascot component
│   │   ├── Cinema.jsx        Home hero, thumbnail strip, "selected films" dial
│   │   └── api.js            All backend calls
│   ├── scripts/              capture-screenshots.mjs (Playwright)
│   └── public/               Icons, manifest, service worker
├── backend-java/             Spring Boot API
│   └── src/main/java/com/rewatch/
│       ├── controller/       REST endpoints
│       ├── service/          Recommender, scoring, Taste DNA, social, TMDB client…
│       ├── model/ repository/ dto/ security/ config/
│       └── features/         Genre/keyword lexicon that derives emotional vectors
├── docs/                     Product, design and engineering documents
│   ├── CASE-STUDY.md         How the taste model works, and what's still limited
│   └── screenshots/          README images
├── .github/                  CI, uptime check, database-expiry guardian, templates
├── docker-compose.yml        Postgres + backend + frontend
├── render.yaml               Backend deploy blueprint (Render)
└── DEPLOYMENT.md             Deployment notes and migration history
```

## Documentation

- [`docs/CASE-STUDY.md`](docs/CASE-STUDY.md) — the engineering story: how a title's emotional vector is derived, why match scores add up, why profiles are replayed from a log, and an honest list of limitations.
- [`docs/`](docs) — product requirements, TasteDNA and recommendation models, API specification, UI design system, brand guidelines, security and testing strategy.
- [`DEPLOYMENT.md`](DEPLOYMENT.md) — hosting, environment, and operations.

## License

[MIT](LICENSE) © 2026 eryzaimatong

This product uses the TMDB API but is not endorsed or certified by TMDB.

## Author

Built by **Eryza Mae C. Imatong**.

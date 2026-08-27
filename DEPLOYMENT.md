# Deployment

## Architecture

- `frontend/` — React (Vite), built to static assets and served by nginx.
- `backend-java/` — Spring Boot 3 / Java 17 API.
- PostgreSQL — the only datastore. (The old `backend/` Flask prototype and its
  `database/rewatch.db` SQLite file have been removed; they predate the
  Spring Boot backend and were never wired to the frontend.)

## Quickest path: docker-compose

```
cp .env.example .env
# fill in DB_PASSWORD, TMDB_API_KEY, JWT_SECRET (see comments in .env.example
# for how to generate JWT_SECRET), and CORS_ALLOWED_ORIGINS /
# VITE_API_BASE if the frontend and backend won't both be on localhost.

docker compose up --build
```

This starts three services: `postgres`, `backend` (port 8080), `frontend`
(port 5173, nginx). The backend runs with `SPRING_PROFILES_ACTIVE=prod`
(see `backend-java/src/main/resources/application-prod.properties`), which
requires `JWT_SECRET`, `TMDB_API_KEY`, and `DB_PASSWORD` to be set — it will
refuse to start rather than fall back to the insecure dev JWT secret.

## Required environment variables

| Variable | Used by | Notes |
|---|---|---|
| `DB_PASSWORD` | backend, postgres | no default in prod |
| `TMDB_API_KEY` | backend | no default in prod |
| `JWT_SECRET` | backend | no default in prod; `openssl rand -base64 48` |
| `CORS_ALLOWED_ORIGINS` | backend | comma-separated, must match the frontend's actual origin |
| `VITE_API_BASE` | frontend (build-time) | the URL the *browser* uses to reach the backend — baked into the JS bundle at build time, not read at container start |
| `MAIL_USERNAME` | backend | no default in prod; a Gmail address used to send password-reset emails |
| `MAIL_PASSWORD` | backend | no default in prod; a Gmail [App Password](https://myaccount.google.com/apppasswords), not the account password |
| `FRONTEND_BASE_URL` | backend | the origin the SPA is served from — embedded in reset-password email links; defaults to `http://localhost:5173` |
| `ADMIN_EMAILS` | backend | comma-separated emails auto-promoted to ADMIN on login; optional, empty is fine |

`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` have working defaults for the
docker-compose setup; override them if pointing at a managed Postgres
instance instead of the bundled container.

Every one of these must be set for `docker compose up` to start at all —
`docker-compose.yml` uses `${VAR:?message}` for anything with no safe
default, so a missing `MAIL_USERNAME`/`MAIL_PASSWORD`/`TMDB_API_KEY`/
`JWT_SECRET`/`DB_PASSWORD` fails the compose run immediately with that
message, rather than starting a broken backend.

## Deploying frontend and backend separately (not docker-compose)

If the frontend is deployed as static files (e.g. to a CDN/static host)
rather than via the nginx image:

```
cd frontend
VITE_API_BASE=https://api.yourdomain.com npm run build
# deploy the resulting dist/ directory
```

For the backend, build and run the jar directly:

```
cd backend-java
./mvnw -DskipTests package
SPRING_PROFILES_ACTIVE=prod \
  DB_HOST=... DB_PASSWORD=... TMDB_API_KEY=... JWT_SECRET=... \
  CORS_ALLOWED_ORIGINS=https://yourdomain.com \
  MAIL_USERNAME=... MAIL_PASSWORD=... FRONTEND_BASE_URL=https://yourdomain.com \
  java -jar target/backend-java-1.0.0.jar
```

## API docs

`GET /swagger-ui/index.html` (no auth required — it's documentation, not
data) — generated from the controllers/DTOs via springdoc, not hand-written,
so it can't drift from the actual routes. `GET /v3/api-docs` is the raw
OpenAPI 3.0 JSON if you want to feed it to another tool.

## Catalog seeding

The `titles` table starts empty on a fresh database. `CatalogSeeder`
(`service/CatalogSeeder.java`) runs automatically on every boot and, if fewer
than 200 titles exist, calls the same bulk-expand operation below in a
background thread to seed ~6000 titles — a first-time deploy no longer needs
a manual step for Mood Search, genre browsing, and Discovery to have real
breadth to work with.

To re-run it manually (e.g. to grow the catalog further, or immediately
rather than waiting on the next restart):

```
POST /api/admin/expand-catalog?target=6000
```

Requires an ADMIN-role account (see `rewatch.admin.emails`) and a configured
`TMDB_API_KEY`. Takes real wall-clock time — on the order of ten to twenty
minutes at the default target (verified live: a real run against production
TMDB took ~7 minutes and added 4,382 titles in 454 page calls, well under
the 1,500-call safety cap).

`DemoCommunitySeeder` also runs automatically on every boot, once the
catalog has at least 500 titles: it seeds 8 demo accounts with real rating
history and one public collection apiece, so Community (Similar TasteDNA,
Discover Collections) isn't empty on a fresh deploy before any real users
have found each other. Idempotent — checks whether it already ran and
no-ops if so.

## Health check

`GET /api/health` (no auth required) checks the database connection and
returns `{"status":"ok","db":"up"}` or a 503. Used as the docker-compose
healthcheck and is a reasonable target for a load balancer or uptime monitor.

## CI

`.github/workflows/ci.yml` runs on every push/PR to `main`: `./mvnw test` for
the backend, `npm ci && npm run lint && npm test && npm run build` for the
frontend. It doesn't build or push Docker images yet — that's a reasonable
next step once there's somewhere to push them to.

CI passing is not the same thing as the deploy being safe — Render deploys
on push independently of GitHub Actions' result, so a CI failure doesn't
block or delay production. Confirmed the hard way: a same-day commit passed
locally, broke CI on the next push (a race-condition-broken local install
masquerading as a real fix), and Render had already deployed the *previous*
commit successfully in the meantime purely by chance of timing. Watch the
actual Render deploy (`render deploys list <serviceId> -o json`, or
`render logs --resources <serviceId>` for real boot output) for anything
that touches schema handling, auth, or startup — CI passing is necessary,
not sufficient.

## Rolling back a bad deploy

`render deploys create rewatch-backend --commit <sha> --wait` redeploys any
previous commit and waits for the result — this is the actual rollback
mechanism, not a dedicated `rollback` subcommand. Find a known-good sha with
`render deploys list srv-da4rltc9v7es7392tp8g -o json` (look for the last
entry with `"status": "live"` before the bad one). Frontend rollback is the
same idea via Vercel: `vercel rollback <deployment-url-or-id>` from
`frontend/` (find candidates with `vercel ls`), or `vercel deploy --prod`
after `git checkout` to the last good commit.

## HTTPS

Neither service terminates TLS itself — that's expected to happen at the
hosting platform/load balancer (Render, Railway, Vercel, and similar all
terminate HTTPS and redirect HTTP automatically at their edge). Forcing a
redirect inside the app itself risks a redirect loop behind exactly that
kind of proxy, where the app only ever sees the decrypted-HTTP side of the
connection. If the platform sits in front as a reverse proxy, also set
`TRUST_PROXY_HEADERS=true` (see `SecurityUtil.clientIp` and the CORS/rate-
limiting sections above) — it's usually the same proxy hop that terminates
TLS and forwards `X-Forwarded-For`. Render's own edge proxy is exactly this
case — it appends rather than overwrites, so `TRUST_PROXY_HEADERS=true` is
set in `render.yaml` and `SecurityUtil.clientIp()` reads the rightmost
entry specifically because of that; the leftmost entry is whatever the
client claimed.

## Live deployment

Backend + Postgres run on Render (`render.yaml`, blueprint-managed), frontend
on Vercel — two separate services, not the docker-compose monolith above.
Render builds `backend-java/Dockerfile` directly on every push to `main`
(`autoDeploy: yes`); Vercel is deployed manually via `vercel --prod` from
`frontend/` (see `frontend/vercel.json` for the SPA-fallback rewrite every
client-side route needs). Values marked `sync: false` in `render.yaml` (`TMDB_API_KEY`, `JWT_SECRET`,
`MAIL_USERNAME`, `MAIL_PASSWORD`, `CORS_ALLOWED_ORIGINS`,
`FRONTEND_BASE_URL`) are set by hand in the Render dashboard, never
committed — the latter two aren't secret, but the live Vercel origin
doesn't match what's in source control, and a real `value:` here would
mean the next blueprint sync silently reverts production CORS to
localhost-only.

Two GitHub Actions workflows exist purely to cover gaps the free-tier
platforms themselves don't: `keepalive.yml` pings both services every 10
minutes so Render's 15-minute idle-sleep never triggers on a real visitor
(and doubles as uptime monitoring — a failed ping fails the workflow), and
`db-guardian.yml` watches the free Postgres instance's 30-day expiry and

`keepalive.yml`'s frontend ping reads the target from the `FRONTEND_URL`
repository variable (Settings → Secrets and variables → Actions →
Variables — not a secret, it's not sensitive) instead of a hardcoded URL,
same reasoning as `VITE_SITE_URL` above: the moment the real domain is
live, this is a one-line update instead of a string literal to hunt down.
Set it once — `gh variable set FRONTEND_URL --body "https://your-domain"`
or via the GitHub UI — before merging any change to that workflow, or the
frontend-ping step fails every run until it's set.

opens a GitHub issue with an exact runbook once it's within 5 days of
deletion. The latter needs `RENDER_API_KEY` as a repo secret to do anything;
**currently unset** — every run to date has hit the no-op branch (confirmed
via the Actions API: the "Fetch database expiry" step shows `skipped`, not
just reading the workflow file) — see the comment at the top of that file
for why it can't be fully automated (Render refuses to run two free-tier
databases at once, so an unattended rotation risks data loss instead of
preventing it).

A third workflow, `actions-heartbeat.yml`, exists purely to stop GitHub's
own scheduler from disabling `keepalive.yml`/`db-guardian.yml` in the first
place: GitHub auto-disables every scheduled workflow in a public repo after
60 days with no repository *activity* (a push/release/PR — not the
workflow's own runs, and not an issue db-guardian.yml opens). It commits a
timestamp file weekly, comfortably under the 60-day deadline even accounting
for GitHub's scheduler being measurably unreliable (see below). This does
NOT cover the case of the heartbeat workflow itself silently failing — only
a genuinely external, out-of-band watchdog (a free service like
healthchecks.io or UptimeRobot's heartbeat monitor, pinged by a successful
`keepalive.yml` run) can detect that, and setting one up needs a
third-party account this repo can't create on its own.

**GitHub's cron schedule is measurably unreliable at this frequency** —
worth knowing before trusting `*/10 * * * *` to mean every 10 minutes.
Pulled every available run of `keepalive.yml` (100 runs, spanning ~4 days)
and measured the actual gaps between them: 100% exceeded Render's 15-minute
idle-sleep window, median gap 44.5 minutes, worst gap 309.8 minutes (over
5 hours). This is documented GitHub behavior for high-frequency scheduled
triggers under platform load, not a bug in this workflow — it means the
keepalive ping is not reliably preventing cold starts today, regardless of
the cron expression, and no code change to this repo fixes that.

## Known gaps

- Email still doesn't actually send on the current deployment, despite the
  fix being built. Render's free tier blocks outbound SMTP entirely
  (confirmed live: both 587 and 465 hit `SocketTimeoutException`), so
  `EmailConfig`/`HttpEmailSender` now route mail through Resend's HTTP API
  instead (`rewatch.mail.provider=http`) — plain HTTPS isn't blocked, only
  SMTP is. That path is built and unit-tested against a mocked client, but
  UNVERIFIED against a real send: no `RESEND_API_KEY` has ever been
  provisioned, so `rewatch.mail.provider` still defaults to `smtp` in
  production (a known-broken but gracefully-degrading default) rather than
  `http` (which would hard-fail at boot without a real key). Password-reset
  and new-follower emails fail into `EmailDeliveryRecord` by design (the
  request itself still succeeds) — see `EmailService`'s doc comment.
- Render's free Postgres tier hard-deletes the database 30 days after
  creation, with no free way to extend it — see `db-guardian.yml` above.
  The durable fix is migrating off it to a provider without that limit
  (e.g. Neon), not building around it forever.
- No *automated* backups — `GET /api/admin/backup` (BackupController, admin
  JWT required) exports every user-generated table as JSON on demand, which
  covers the actual disaster-recovery need, but nothing calls it on a
  schedule yet.
- No schema migration baseline verified against the live production schema
  — Flyway is wired up (`db/migration/V1__baseline.sql`) and tracks every
  schema change from here forward, but `ddl-auto=update` deliberately stays
  on as the active schema authority until a full DDL baseline can be tested
  against a real Postgres instance rather than shipped blind.
- Frontend test coverage covers the security/reliability-critical modules
  (api.js's network-error/HTTP-error handling, sessionGuard.js's fetch
  timeout and dead-session detection, auth.js, onboardingUtils.js,
  accessibility) via Vitest, but not the UI components themselves — those
  are still verified only by ad hoc manual passes, not a committed suite.

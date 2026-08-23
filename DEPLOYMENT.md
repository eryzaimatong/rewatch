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
the backend, `npm run lint && npm run build` for the frontend. It doesn't
build or push Docker images yet — that's a reasonable next step once there's
somewhere to push them to.

## HTTPS

Neither service terminates TLS itself — that's expected to happen at the
hosting platform/load balancer (Render, Railway, Vercel, and similar all
terminate HTTPS and redirect HTTP automatically at their edge). Forcing a
redirect inside the app itself risks a redirect loop behind exactly that
kind of proxy, where the app only ever sees the decrypted-HTTP side of the
connection. If the platform sits in front as a reverse proxy, also set
`TRUST_PROXY_HEADERS=true` (see `SecurityUtil.clientIp` and the CORS/rate-
limiting sections above) — it's usually the same proxy hop that terminates
TLS and forwards `X-Forwarded-For`.

## Live deployment

Backend + Postgres run on Render (`render.yaml`, blueprint-managed), frontend
on Vercel — two separate services, not the docker-compose monolith above.
Render builds `backend-java/Dockerfile` directly on every push to `main`
(`autoDeploy: yes`); Vercel is deployed manually via `vercel --prod` from
`frontend/` (see `frontend/vercel.json` for the SPA-fallback rewrite every
client-side route needs). Secrets marked `sync: false` in `render.yaml`
(`TMDB_API_KEY`, `JWT_SECRET`, `MAIL_USERNAME`, `MAIL_PASSWORD`) are set by
hand in the Render dashboard, never committed. `CORS_ALLOWED_ORIGINS` and
`FRONTEND_BASE_URL` are set to the real Vercel URL post-deploy, once it's
known.

Two GitHub Actions workflows exist purely to cover gaps the free-tier
platforms themselves don't: `keepalive.yml` pings both services every 10
minutes so Render's 15-minute idle-sleep never triggers on a real visitor
(and doubles as uptime monitoring — a failed ping fails the workflow), and
`db-guardian.yml` watches the free Postgres instance's 30-day expiry and
opens a GitHub issue with an exact runbook once it's within 5 days of
deletion. The latter needs `RENDER_API_KEY` as a repo secret to do anything;
see the comment at the top of that file for why it can't be fully automated
(Render refuses to run two free-tier databases at once, so an unattended
rotation risks data loss instead of preventing it).

## Known gaps

- No schema migration tool (Flyway/Liquibase). `spring.jpa.hibernate.ddl-auto`
  is `update` even in the `prod` profile, so Hibernate auto-alters the schema
  on boot. Fine for a single-instance early-stage deploy; revisit before
  scaling past one backend instance or wanting real migration history.
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
- Frontend test coverage is thin — one utility module covered by Vitest,
  everything else verified only by ad hoc manual passes, not a committed
  suite.

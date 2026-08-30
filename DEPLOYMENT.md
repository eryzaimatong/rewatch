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

Backend runs on Render (`render.yaml`, blueprint-managed), frontend on
Vercel — two separate services, not the docker-compose monolith above.
Render builds `backend-java/Dockerfile` directly on every push to `main`
(`autoDeploy: yes`); Vercel is deployed manually via `vercel --prod` from
`frontend/` (see `frontend/vercel.json` for the SPA-fallback rewrite every
client-side route needs). Values marked `sync: false` in `render.yaml`
(`TMDB_API_KEY`, `JWT_SECRET`, `MAIL_USERNAME`, `MAIL_PASSWORD`,
`CORS_ALLOWED_ORIGINS`, `FRONTEND_BASE_URL`, `DB_HOST`, `DB_PORT`,
`DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SSLMODE`) are set by hand in
the Render dashboard, never committed — some of these aren't secret (the
live Vercel origin, e.g.), but a real `value:`/`fromDatabase` here would
mean the next blueprint sync silently reverts production to a stale or
wrong value.

**Postgres runs on Neon, not Render, as of the migration below** — the
`databases:` block in `render.yaml` still declares the original Render
Postgres resource so it stays alive (deliberately not deleted; see below),
but the app no longer connects to it.

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

**Measured cold-boot times so far: 135–146s, and one outlier at 166.6s**
(hit live mid-session on 2026-08-27, `/api/health` didn't respond until
166.6s).

**Compound case measured (2026-08-29): Render idle-sleep + Neon
suspended at the same time, not just Render alone.** Deliberately let
both go idle (a genuinely silent ~16-minute window — no requests to
either service, confirmed via Render's own request logs, since even the
CLI's `render logs`/`render deploys list` calls don't touch the deployed
web service's own traffic-based idle timer) then hit `/api/health` cold:
**167.40s, HTTP 200, `db: up`.** Indistinguishable in magnitude from the
Render-only range above — the compound case is not materially worse.
Isolated separately via boot logs from a clean deploy-boot window:
HikariCP took 7.2s to get its first connection cold vs. ~2s when Neon
was already warm (local testing) — Neon's suspend costs roughly 5-7s at
the DB-connection step specifically, not a multiplicative or
compounding delay on top of Render's own boot time. (One earlier attempt
at this same measurement was invalidated by an unrelated push during
the wait window triggering a Render redeploy — see the build-filter
note below; the 167.40s figure is from a clean, non-interfered second
attempt.)

**Frontend abort budget raised 180s → 200s (2026-08-29)**, after the
167.40s compound measurement left the old budget only ~13s of margin on
a small handful of samples with ~30s of spread already seen between the
typical (135-146s) and outlier (166-167s) cases — not enough to be
confident another sample wouldn't exceed it. The tradeoff: a longer
budget costs nothing to a legitimately slow-but-succeeding wake-up (the
visitor just waits, and `WakingState.jsx` fills the entire wait with
staged content, not a stall) but delays how long a genuinely dead server
takes to surface a real error. A budget left too thin risks aborting a
request that would have succeeded seconds later — worse than either
alternative, since the retry (see `WakingState.jsx`) restarts the whole
wait from zero, turning one ~167s wait into that plus a second ~150s+
one. 200s = worst observed (167.40s) + roughly one more typical-to-
outlier spread (~30s) — chosen from that arithmetic, not a reflexive
round-up; a future measurement landing meaningfully closer to 200s means
revisiting this again. Updated everywhere the old figure was referenced:
`sessionGuard.js` (`FETCH_TIMEOUT_MS`), its test, and the backend's
`DataSourceConfigTest` (statement_timeout must stay well under whatever
this number is).

**Render's `autoDeploy` rebuilds the backend on every push to `main`,
including frontend-only changes that never touch `backend-java/`** —
confirmed directly (a frontend-only commit triggered a full backend
rebuild + ~150s cold boot mid-session, which is what invalidated the
first compound cold-start attempt above). Fixed (2026-08-29):
`render.yaml`'s `rewatch-backend` service now sets `buildFilter.paths`
to `backend-java/**` and `render.yaml` itself. Verified both directions:
a `render.yaml` change (in the filter) still triggered a deploy; a
frontend-only push should now be confirmed separately not to.

## Database migration: Render Postgres → Neon (2026-08-28)

Render's free Postgres tier deletes the database 30 days after creation
(confirmed live via `render postgres get`: created 2026-08-22, expires
2026-09-21) — with real user data on that clock, this was treated as the
highest-priority infra item, ahead of everything else in this phase.

**Why Neon over Supabase:** both offer a free tier with a Singapore region
(`ap-southeast-1`) and no hard data-deletion policy. The deciding factor was
how each recovers from idle: Neon's free-tier compute auto-suspends after 5
minutes idle and **auto-wakes on the next connection**, sub-second, no human
involved. Supabase pauses after ~7 days idle and requires a human to click
"Resume" in its dashboard — there's no API/CLI unpause. A side project with
sporadic traffic going a week without a visitor would go dark on Supabase
and stay dark until someone noticed. Neon self-recovers.

**Connection endpoint: direct, not pooled.** Neon exposes both a pooled
(PgBouncer, `-pooler` in the hostname) and a direct endpoint. The app uses
the **direct** endpoint, for two independent reasons found during migration:
1. HikariCP's `connection-init-sql` (`SET statement_timeout = 30000`,
   see above) needs to reliably apply per connection. Neon's pooler runs
   PgBouncer in transaction mode, where `SET`/`RESET` are documented as
   restricted — session state isn't guaranteed to survive across
   transactions when the underlying server connection can be handed to a
   different client. That would silently defang the statement-timeout
   safety net this app already relies on.
2. **Observed directly during migration, not just theoretical:** an
   `ALTER ROLE neondb_owner SET search_path = public` was run to fix
   unqualified table access (see below). Querying `SHOW search_path;`
   through the **pooled** endpoint immediately after still returned empty,
   even though `pg_db_role_setting` confirmed the setting was persisted
   server-side. The same query through the **direct** endpoint correctly
   returned `public`. Whatever the exact cause, the pooled endpoint did not
   reliably reflect a role-level default that had already committed —
   reinforcing #1 rather than being purely theoretical.

HikariCP's own pool (`spring.datasource.hikari.maximum-pool-size=10`,
explicit rather than left at Hikari's implicit default) already provides
the application-side pooling a single Render instance needs. Neon's direct
endpoint reports `max_connections=104` at the free tier's minimum 0.25 CU
compute (scaling to 839 as it autoscales) — a pool of 10 uses under 10% of
the floor, so there's no capacity reason to take on PgBouncer's
transaction-mode restrictions at this scale.

**A note for anyone restoring a fresh Neon project from scratch:** the new
project's `neondb_owner` role came up with an *empty* `search_path` (not
Postgres's usual `"$user", public` default) — `pg_restore` still succeeded
and created every table correctly in the `public` schema, but unqualified
queries (`SELECT * FROM titles`) failed with "relation does not exist"
until `ALTER ROLE neondb_owner SET search_path = public;` was run once,
directly, against the new database. Not yet clear whether this is a Neon
platform default or specific to this project — flagging as observed,
unverified as to cause.

**SSL:** `spring.datasource.url` now appends `?sslmode=${DB_SSLMODE:prefer}`.
Default `prefer` matches pgjdbc's own implicit default, so Render/local
behavior is unchanged; `DB_SSLMODE=require` is set via the dashboard
specifically for the Neon connection. Neon's dashboard-provided connection
string also includes a `channel_binding=require` parameter, deliberately
**not** added to the JDBC URL — this environment had no way to verify
pgjdbc's exact support/spelling for that parameter against a real
connection, and `sslmode=require` alone is sufficient for an encrypted,
verified connection.

**Migration verification, row counts (exact `count(*)`, not stats
estimates), before (Render) vs. after (Neon), 19 tables — full set:**

| Table | Before | After |
|---|---|---|
| blocks | 0 | 0 |
| collection_follows | 0 | 0 |
| daily_guesses | 0 | 0 |
| email_delivery_records | 1 | 1 |
| flyway_schema_history | 2 | 2 |
| follows | 0 | 0 |
| notifications | 41 | 41 |
| password_reset_tokens | 4 | 4 |
| ratings | 144 | 144 |
| reports | 0 | 0 |
| review_comments | 0 | 0 |
| review_likes | 0 | 0 |
| titles | 6005 | 6005 |
| trait_events | 1690 | 1690 |
| user_traits | 250 | 250 |
| users | 32 | 32 |
| watch_statuses | 1 | 1 |
| watchlist_folders | 8 | 8 |
| watchlist_items | 64 | 64 |

Taken via `pg_dump --format=custom --no-owner --no-privileges` from Render's
**external** connection string (its internal `dpg-...` hostname only
resolves inside Render's own network — the first dump attempt failed on
that before the external one was used), restored via `pg_restore --clean
--if-exists` against Neon. `pg_restore` exit 0, 0 errors; `pg_restore
--list` confirmed all 19 `TABLE DATA` entries were included in the dump
before restoring.

**Rollback procedure (written before cutover):**
1. Trigger: `/api/health` fails, login fails, a rating write fails,
   read-back of pre-existing data comes back missing/wrong, or Neon's
   compute fails to wake within a reasonable window.
2. Mechanism: flip `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/
   `DB_PASSWORD`/`DB_SSLMODE` in the Render dashboard back to the original
   Render-managed values, then restart the service. No code change or
   redeploy needed since these are dashboard-set, not committed.
3. Re-run the same live checklist post-rollback: clean boot, `/api/health`
   200, login, a rating write, read-back.
4. **Not perfectly clean either direction:** the Render DB is never
   written to or deleted during cutover, so rolling back loses nothing
   that existed before migration — but any writes that land on Neon
   *after* cutover and before a rollback is triggered would not be on the
   Render DB being rolled back to.

**The Render Postgres instance is being kept alive deliberately** — not
deleted as a cleanup step. It expires on its own on 2026-09-21; until then
it's the rollback safety net.

**Two real problems hit during cutover, not anticipated in the plan above:**

1. **pgjdbc 42.6.2 (Spring Boot 3.2.5's managed default) couldn't
   authenticate against Neon**: `java.lang.IllegalArgumentException:
   Argument 'iteration must be >= 4096' is not valid`, thrown inside the
   driver's bundled SCRAM library while parsing Neon's SCRAM server-first
   message. Confirmed this was the driver, not the credentials: the exact
   same connection string authenticated cleanly from a local machine with
   this same 42.6.2 driver — only Render's connection to it failed. Fixed
   by pinning `org.postgresql:postgresql` to `42.7.13` explicitly in
   `pom.xml` (overriding Spring Boot's managed version). Re-verified
   locally against the real Neon endpoint post-upgrade before pushing.
2. **`render.yaml` switching `DB_*` from `fromDatabase` to `sync: false`
   did not clear the previously-injected Render-managed values** — it
   only stopped Render from overwriting them going forward. The first
   deploy after the dashboard values were (eventually) set still failed
   with `password authentication failed for user 'rewatch'` — Render's
   original Postgres username — until the dashboard values were actually
   saved. Worth knowing for next time: a `fromDatabase` → `sync: false`
   switch is not itself a value reset.

**Final live verification, 2026-08-29, actual production deploy**
(`d09d9bd`, status `live`, confirmed via `render deploys list`):
- `/api/health` → `200 {"status":"ok","db":"up"}`, 1.18s response.
- Real register + login against production (`userId=34`) — both 200.
- Real rating write against production (`POST /api/movies/rate`,
  `ratingId=146`) — 200, trait shifts computed, achievement unlocked.
- Read-back of pre-existing data: `ratings.id=1` (user 1, "Colony",
  predates migration) — present and correctly joined to its title.
- Smoke-test user/rating (id 34) and its 20 trait_events / 1 notification
  / 10 user_traits rows deleted afterward — row counts confirmed back to
  the exact pre-migration baseline (32 users, 144 ratings, 1690
  trait_events).

C1 is done and verified live.

## Per-route Open Graph previews

`/compare/:username` and `/social/:userId` are the two shareable links the
viral loop depends on — a link posted to a group chat or Discord needs a
preview specific to that user, not the generic homepage card every route
got before this. Since crawlers don't execute JS, the SPA's client-rendered
`<title>`/meta was never going to reach them; the fix runs entirely at the
edge, before any of that:

1. `frontend/middleware.js` (Vercel Edge Middleware) matches only those two
   routes and checks the request's User-Agent against a crawler allowlist
   (facebookexternalhit, Twitterbot, Discordbot, Slackbot, WhatsApp,
   LinkedInBot, TelegramBot). A real visitor's browser never matches, and
   falls straight through to the normal SPA via `next()`.
2. A matched crawler request is rewritten (URL unchanged, same as any
   Vercel rewrite) to `frontend/api/og-page.js`, which builds a minimal
   static HTML document with real per-route `og:title`/`og:description`/
   `og:image` — fetched from the new unauthenticated
   `GET /api/social/{userId}/og-summary` (or `/username/{username}/...`
   for compare) backend endpoint, a deliberately small slice of the profile
   (username, archetype, top trait, rating count — see
   `SocialService.publicOgSummary`'s own comment for exactly what's
   excluded).
3. `og:image` points at `frontend/api/og-image.jsx`, which renders an
   actual 1200x630 PNG via `@vercel/og`, porting `shareCard.js`'s existing
   card design (same gradient, purple accent, footer tagline/wordmark)
   into the landscape link-preview shape instead of inventing a new look.

Privacy: a private (`profilePublic=false`) profile and a nonexistent one
both make `og-summary` 404 identically, and `og-page.js` collapses that —
along with a network error or a timed-out cold backend — into the exact
same fully generic fallback (the static `/og-image.png`, the homepage
title/description). No username, archetype, or count ever appears for a
private profile, and a crawler hitting a cold Render instance gets the
generic card instead of an error or a hang (a short server-side timeout in
`og-page.js` guards this, since Render's free tier can take tens of seconds
to wake and crawlers keep a much shorter fetch budget than that).

UNVERIFIED as of this writing: the actual rendered previews have not yet
been checked against Facebook's Sharing Debugger, Twitter/X's Card
Validator, or a real Discord paste — do that after the next deploy, for
both a public and a private profile.

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

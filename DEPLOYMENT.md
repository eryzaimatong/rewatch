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

`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` have working defaults for the
docker-compose setup; override them if pointing at a managed Postgres
instance instead of the bundled container.

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
  java -jar target/backend-java-1.0.0.jar
```

## Health check

`GET /api/health` (no auth required) checks the database connection and
returns `{"status":"ok","db":"up"}` or a 503. Used as the docker-compose
healthcheck and is a reasonable target for a load balancer or uptime monitor.

## CI

`.github/workflows/ci.yml` runs on every push/PR to `main`: `./mvnw test` for
the backend, `npm run lint && npm run build` for the frontend. It doesn't
build or push Docker images yet — that's a reasonable next step once there's
somewhere to push them to.

## Known gaps

- No schema migration tool (Flyway/Liquibase). `spring.jpa.hibernate.ddl-auto`
  is `update` even in the `prod` profile, so Hibernate auto-alters the schema
  on boot. Fine for a single-instance early-stage deploy; revisit before
  scaling past one backend instance or wanting real migration history.
- No Docker image publishing / CD — CI only verifies the build, it doesn't
  deploy anywhere.

# TwistMeet (working name)

Small-club speedcubing event platform. "TwistMeet" is a provisional working name, not a cleared
brand (see `DECISIONS.md`). The controlling specification is `product-docs/00-authoritative-build-contract.md`;
read `product-docs/README.md` first. Build status, decisions, and traceability live in
`M0-REPORT.md`, `DECISIONS.md`, and `TRACEABILITY.md` at the repo root.

This is **Milestone 1** (product foundation + project setup): staff auth, organizations, tenant
isolation, event lifecycle, and guest join. Competition rounds, scoring, scrambles, and public
display are not built yet — see `TRACEABILITY.md` for what's implemented vs. planned per
milestone.

## Repository layout

- `api/` — Spring Boot (Java 21) modular monolith API, PostgreSQL, Flyway migrations.
- `web/` — Next.js (TypeScript) web app.
- `product-docs/` — the specification package (read-only; don't edit as part of implementation).
- `docker-compose.yml` — local Postgres + a dev-only mail catcher.

## Prerequisites

- Java 21 (the Gradle wrapper in `api/` pins Gradle 8.10.2; no separate Gradle install needed).
- Node 22.x (see `web/.nvmrc`) and npm.
- Docker (for local Postgres + mail catcher) — or a local PostgreSQL 16 instance if Docker isn't
  available in your environment (see note below).

## Local setup

1. **Start infrastructure:**

   ```bash
   docker compose up -d
   ```

   This starts PostgreSQL on `5432` (db/user/password: `twistmeet`/`twistmeet`/`twistmeet`) and a
   local mail catcher (MailDev) on SMTP `1025` with a web inbox at `http://localhost:1080`. No
   production email provider is configured — see `DECISIONS.md` OD04.

   > If Docker isn't available in your environment, install PostgreSQL 16 locally and create a
   > matching role/database (`createuser twistmeet`, `createdb -O twistmeet twistmeet`), then set
   > `TWISTMEET_DB_URL`/`TWISTMEET_DB_USER`/`TWISTMEET_DB_PASSWORD` accordingly. Email sending will
   > simply fail silently (logged, not thrown — see `MailService`) without a mail catcher running.

2. **Run the API** (migrations run automatically via Flyway on startup):

   ```bash
   cd api
   ./gradlew bootRun
   ```

   The API listens on `http://localhost:8080`. Health check: `GET /actuator/health`.

   To also seed one demo organization/event (join code `DEMO2026`, organizer login
   `demo-organizer@twistmeet.local` / `demo-password-123`), run with the `seed` profile:

   ```bash
   SPRING_PROFILES_ACTIVE=seed ./gradlew bootRun
   ```

3. **Run the web app:**

   ```bash
   cd web
   cp .env.example .env.local   # first time only; override NEXT_PUBLIC_* values as needed
   npm install
   npm run dev
   ```

   The web app listens on `http://localhost:3000` and talks to the API at
   `NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:8080`).

4. **Try it:** open `http://localhost:3000`, create a staff account, create an organization and a
   draft event, open registration, and open the join link shown on the event page in a different
   browser/incognito window to join as a guest.

## Running checks

```bash
# API: format check (Spotless) + tests + build
cd api && ./gradlew check && ./gradlew build

# Web: lint (ESLint) + type-check + production build
cd web && npm run lint && npm run build
```

Both are run in CI on every push/PR — see `.github/workflows/ci.yml`. CI's API job uses a
`postgres:` service container; locally this repo's tests connect to whatever Postgres
`TWISTMEET_DB_URL` (test profile default: `jdbc:postgresql://localhost:5432/twistmeet_test`)
points at — Testcontainers is not used because it requires a Docker daemon that isn't available in
every environment this has been developed in (see `DECISIONS.md`). Tests reset the schema
(`Flyway.clean()` + `migrate()`) before each test method, so point `TWISTMEET_DB_URL` at a
database you're fine seeing wiped repeatedly, not one with data you care about.

## What's implemented vs. planned

See `TRACEABILITY.md` section G for the full per-route status. In short: staff register/login/
logout, organizations, event create/edit/lifecycle (draft → registration open → locked →
reopened), join-code rotation, guest join with duplicate-name disambiguation, and an
organizer-only roster read are built and tested. Rounds, attempts, scoring, scrambles,
corrections, advancement, public display, and CSV export are not built yet (milestones M2–M5).

# TwistMeet (working name)

Small-club speedcubing event platform. "TwistMeet" is a provisional working name, not a cleared
brand (see `DECISIONS.md`). The controlling specification is `product-docs/00-authoritative-build-contract.md`;
read `product-docs/README.md` first. Build status, decisions, and traceability live in
`M0-REPORT.md`, `DECISIONS.md`, and `TRACEABILITY.md` at the repo root.

This is **Milestone 5** (advancement, publishing, public display, event history, and CSV export):
advancement preview/commit (reusing the pure scoring engine), publish/unpublish with a rotating
public link, unauthenticated public event/standings display, organization event history with
search/filter, and CSV export with CSV-injection mitigation — on top of Milestone 4's scramble
controls (versioned generation, encrypted vault storage, role-scoped reveal, the 3D move guide),
Milestone 3's organizer and competitor experience (event creation wizard, Tournament Control,
judge-entry validation/undo, the competitor waiting room's explicit lifecycle/connection-loss
states), Milestone 2's competition engine (round setup/lifecycle, the attempt state machine for
both physical-judge and phone-casual modes, roster management, judge result entry with
append-only correction history, and the scoring/ranking engine), and Milestone 1's staff auth,
organizations, tenant isolation, event lifecycle, and guest join. See `TRACEABILITY.md` for what's
implemented vs. planned per milestone, and `DECISIONS.md` for the M5 implementation decisions and
the deliberately deferred items (the optional tie-break attempt, a `PublicSnapshot` cache table,
public name masking, advancement rollback, and S14's copy-event/retention-deletion).

## Repository layout

- `api/` — Spring Boot 4.1.1 (Java 25 LTS) modular monolith API, PostgreSQL 18, Flyway migrations.
- `web/` — Next.js 16.3.8 (TypeScript, React 19) web app.
- `product-docs/` — the specification package (read-only; don't edit as part of implementation).
- `docker-compose.yml` — local Postgres + a dev-only mail catcher.

## Prerequisites

- Java 25 LTS (the Gradle wrapper in `api/` pins Gradle 9.8.0, which auto-provisions Java 25 via
  the Foojay toolchain resolver if it isn't already installed — no separate JDK install needed on
  a machine with normal internet access).
- Node 24.x LTS (see `web/.nvmrc`) and npm.
- Docker (for local Postgres + mail catcher) — or a local PostgreSQL 18 instance if Docker isn't
  available in your environment (see note below).

## Local setup

1. **Start infrastructure:**

   ```bash
   docker compose up -d
   ```

   This starts PostgreSQL on `5432` (db/user/password: `twistmeet`/`twistmeet`/`twistmeet`) and a
   local mail catcher (MailDev) on SMTP `1025` with a web inbox at `http://localhost:1080`. No
   production email provider is configured — see `DECISIONS.md` OD04.

   > If Docker isn't available in your environment, install PostgreSQL 18 locally and create a
   > matching role/database (`createuser twistmeet`, `createdb -O twistmeet twistmeet`), then set
   > `TWISTMEET_DB_URL`/`TWISTMEET_DB_USER`/`TWISTMEET_DB_PASSWORD` accordingly. Email sending will
   > simply fail silently (logged, not thrown — see `MailService`) without a mail catcher running.
   >
   > **Using Neon instead of local/Docker Postgres:** set the same three environment variables to
   > point at a Neon PostgreSQL 18 project instead — never commit the values. Use Neon's **pooled**
   > connection string (the `-pooler` host, PgBouncer in transaction mode) for `TWISTMEET_DB_URL` at
   > runtime, since the app only ever needs short-lived request-scoped connections through
   > HikariCP. Flyway, however, needs a **direct** (non-pooled) connection for migrations — session
   > state and advisory locks it may use don't survive PgBouncer's transaction-mode pooling — so if
   > you run migrations by hand against Neon (`./gradlew flywayMigrate` or equivalent), point that
   > one invocation at the direct (non-`-pooler`) host instead; `bootRun`'s own startup migration
   > uses whatever `TWISTMEET_DB_URL` is set to, so for everyday dev prefer the direct host there
   > too and only switch the running app to the pooled host for a deployed/shared environment. See
   > `DECISIONS.md` OD15 for the full rationale and current findings (no existing Neon project in
   > this account runs PostgreSQL 18 as of this writing; one would need to be created separately —
   > not done here per the standing no-create/no-alter/no-delete constraint on Neon projects).

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

4. **Try it:** open `http://localhost:3000`, create a staff account — registration is double
   opt-in: submitting email + display name sends a verification link (check the mail catcher's web
   inbox, or the API's debug log, for it) and you choose your password only after clicking that
   link, never at registration (see `DECISIONS.md` "M1 follow-up: double opt-in registration") —
   then create an organization and a draft
   event, open registration, and open the join link shown on the event page in a different
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

### Browser E2E critical-journey test

`web/e2e/critical-journey.spec.ts` drives a full organizer+competitor golden path (register,
verify email, create org/event, open registration, guest joins, run a round through to a judged
result, confirm Tournament Control's live update and connection-state badge, close the round,
check standings) through a real Chromium browser against the real running app — not mocked. It
runs as its own `e2e` job in `.github/workflows/ci.yml`, separate from and additive to the
existing `api`/`web` jobs (its own Postgres service, its own steps) so a flake here never blocks
those two: it starts the dev-only mail catcher, boots the real API (`bootRun`) and a production
build of the web app, waits for both to answer health checks, then runs the spec. To run it
locally the same way:

```bash
# 1. Start Postgres (the usual local/CI one), then the API and web app (build+start matches CI
#    more closely than `npm run dev`, but dev works too for local iteration):
cd api && ./gradlew bootRun &
cd web && npm run build && npm run start &

# 2. Start the dev-only mail catcher (reads the verification link SmtpMailService actually sends,
#    since there's no mailbox to check otherwise):
python3 web/e2e/mailcatcher.py &

# 3. Run the test:
cd web && npx playwright test
```

This needs Chromium (`npx playwright install chromium` if you don't already have one Playwright
can find) and `@playwright/test`/`@axe-core/playwright` (`npm install`, already in
`package.json`). `mailcatcher.py` uses the standard library's `smtpd` module, removed in Python
3.12 — the CI job pins Python 3.11 for this reason (see the `e2e` job's own comment); use 3.11
locally too, or swap to `aiosmtpd` if 3.11 ever becomes impractical to keep around.

## What's implemented vs. planned

See `TRACEABILITY.md` section G for the full per-route status. In short: staff register/login/
logout, organizations, event create/edit/lifecycle (draft → registration open → locked →
reopened), join-code rotation, guest join with duplicate-name disambiguation, full roster
management (add/edit/check-in/withdraw/remove), round setup and lifecycle (draft → preparing →
ready → live → review → closed), the attempt state machine for both physical-judge and
phone-casual modes, judge result entry with append-only revision history (now with client-side
validation, a saved receipt, and an undo window), competitor correction requests with
organizer-only decisions (category selection, request-ID confirmation, required decision reason),
the pure scoring/ranking engine (every `09` conformance vector and property test), a staff-only
standings view, an event-creation wizard, a Tournament Control screen, a competitor waiting room
with explicit lifecycle and connection-loss states, scramble generation/vault with role-scoped
reveal and the 3D move guide (M4), advancement preview/commit, publish/unpublish with public
event/standings display, organization event history, and CSV export (M5) are built and tested.
Deferred items are recorded in `DECISIONS.md`'s M4/M5 implementation-decisions sections rather
than silently skipped.

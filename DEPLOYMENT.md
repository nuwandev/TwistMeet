# Deployment guide (small V1 pilot)

This is packaging for a **small, single-host pilot deployment** — provider-neutral, no
Kubernetes, no Terraform, no cloud resources created by anything in this repository. It does not
make this product worldwide-launch-ready; see `OPERATIONS.md`, `SECURITY_PRIVACY_REVIEW.md`, and
`DECISIONS.md` for the launch decisions (hosting/domain, legal jurisdiction, support owner,
pilot size, translations, dependency license review) that remain genuinely open and are **not**
resolved by adding Dockerfiles. **Nothing in this guide has been run against a real deployment
target** — it has been verified as far as this sandboxed environment allows (Dockerfile review,
`docker compose config` validation, a CI job that builds both images) and is explicitly not
claimed as battle-tested.

## What this is, and isn't

- `api/Dockerfile`, `web/Dockerfile` — production multi-stage images.
- `docker-compose.production.yml` — an **example** stack wiring the two images together,
  against an externally managed PostgreSQL database and mail provider (neither is started by
  this file). Explicitly **not labeled production-ready as committed** — see the comments at
  the top of that file for why (no TLS termination, no public port binding, build-time API URL).
- `docker-compose.yml` (unchanged) — the existing local-development stack (Postgres + MailDev),
  still the right tool for `./gradlew bootRun` / `npm run dev` local iteration. This guide does
  not touch or replace it.

## 1. Build the images

```bash
cp .env.production.example .env.production   # fill in real values; never commit this file
docker compose -f docker-compose.production.yml --env-file .env.production build
```

This builds `twistmeet-api` and `twistmeet-web`. The web image bakes in
`NEXT_PUBLIC_API_BASE_URL`/`NEXT_PUBLIC_APP_NAME` as Next.js build args — **rebuild the web image
whenever either changes** (e.g. a different environment's public API URL). This is a real
constraint of Next.js's static `NEXT_PUBLIC_*` inlining, not a bug to work around; the
alternative is a reverse proxy that exposes the API at a path on the same origin as the web app
(e.g. `/api` → the API container), so the baked-in value can just be a relative path that never
needs to change across environments. That reverse-proxy approach is the one actually recommended
below, for the HTTPS reason in the next section as well.

## 2. HTTPS and reverse-proxy assumptions

**This stack terminates no TLS and publishes no port to the public interface.** Both services
bind only to `127.0.0.1` on the host (see `docker-compose.production.yml`). You are expected to
run a reverse proxy — Caddy, nginx, Traefik, or your platform's load balancer — in front, which:

- Terminates HTTPS with a real certificate (Let's Encrypt via Caddy/certbot, or your platform's
  managed TLS).
- Forwards the public web origin to `127.0.0.1:3000`.
- Forwards the public API origin (or an `/api` path on the same origin, if you choose that
  approach) to `127.0.0.1:8080`.

This is not optional cosmetics: `api/src/main/resources/application-production.yml` sets the
session and CSRF cookies `Secure`, meaning **they are never sent at all over plain HTTP** — the
app will not actually work in the `production` Spring profile without HTTPS in front of it.
Running this stack bound to `0.0.0.0` without a TLS-terminating proxy would also send credentials
across the network in plaintext — never do that.

Set `TWISTMEET_WEB_BASE_URL` (API-side CORS allow-list) and `NEXT_PUBLIC_API_BASE_URL` (web
build arg) to the exact public HTTPS origins your reverse proxy exposes.

## 3. Database migrations

Flyway runs automatically on API startup (`spring.flyway.enabled: true`), applying any pending
`api/src/main/resources/db/migration/V*.sql` migration in order, inside the same process that
then serves traffic. For this pilot's scale (no blue/green, no multi-instance API yet — see
`OPERATIONS.md`), ordering is:

1. **Back up the database before starting a new image** (see §5). Migrations here are additive
   by design (see `TRACEABILITY.md`'s migration review) but a backup before any schema change is
   non-negotiable.
2. Start (or restart) exactly one `api` container. It migrates, then serves.
3. Only after that container reports healthy (see §4) should you route traffic to it or start a
   second instance, if you ever run more than one.

Never run two API instances against the same database on different, unreleased migration
versions — Flyway on the older instance would attempt to re-validate against a schema a newer
instance already advanced past. For a single-instance pilot this is a non-issue; flagging it
for if/when this grows beyond one instance.

### Neon direct vs. pooled connections, if using Neon

If your externally managed Postgres is Neon: use the **direct** (non-`-pooler`) connection string
for `TWISTMEET_DB_URL` in this deployment, the same guidance as local dev (see `README.md`).
Flyway's migration run needs session state/advisory locks that don't survive PgBouncer's
transaction-mode pooling, and the API currently uses one `TWISTMEET_DB_URL` for both migration
and every request (no separate migration-only connection string exists yet). Switching the
*running* app to the pooled `-pooler` host for higher concurrency, while keeping the direct host
for migrations only, is a real option worth revisiting once this pilot's actual concurrency
numbers justify it — not done here, since it would need a second connection string and isn't
required at pilot scale.

## 4. Health checks

Both images declare a Docker `HEALTHCHECK`:
- `api`: `GET /actuator/health` must report `{"status":"UP"}`.
- `web`: the root page must respond.

`docker compose -f docker-compose.production.yml ps` shows each service's health state. The
`web` service's `depends_on: condition: service_healthy` means Compose won't start it until `api`
reports healthy. Your reverse proxy should also health-check these endpoints (or rely on
connection failures) before routing traffic to a freshly started container.

## 5. Backups and restore

Not provided by anything in this repository — the database is externally managed by design (see
§0). Whatever you point `TWISTMEET_DB_URL` at needs its own backup schedule and a tested restore
procedure; `OPERATIONS.md` documents a real `pg_dump`/`pg_restore` drill run against this schema
during development, which is a reasonable starting point for your own externally managed
instance's backup/restore script, not a provided backup service. Back up before every migration-
carrying deploy (§3) at minimum.

## 6. Logs

Both containers log to stdout/stderr (Spring Boot's default console appender; Next.js's own
server logs) — plain `docker logs <container>` or your platform's log aggregation picks them up
with no extra configuration. `api`'s `RequestIdFilter` attaches a correlation ID to every log
line (see `TRACEABILITY.md` R59), useful for tracing one request across both containers if you
forward the same ID (not currently propagated web→API as a header — a real gap if you need
cross-service trace correlation at pilot scale, not built here).

## 7. Upgrade / rollback

Upgrade:
```bash
docker compose -f docker-compose.production.yml --env-file .env.production build
docker compose -f docker-compose.production.yml --env-file .env.production up -d
```
Compose recreates only the containers whose image/config changed. Follow §3's backup-first
ordering for any release that includes a new migration.

Rollback: because Flyway migrations here are additive-only (no down-migrations are written —
see `TRACEABILITY.md`'s migration safety review), rolling back the **application** image to a
previous tag is safe (an older API version simply won't use the newer columns/tables); rolling
back the **schema** is not automated and would need a manual reverse migration or a restore from
the pre-deploy backup in §5. Keep the previous image tag (`IMAGE_TAG` in `.env.production`)
available so `docker compose up -d` with the prior tag is a fast path back if a release regresses
behavior without needing a database restore.

## What still needs an owner decision

Unchanged by this packaging work — tracked in `OPERATIONS.md`/`DECISIONS.md`/
`SECURITY_PRIVACY_REVIEW.md`, not resolved here:
- Actual hosting platform, domain name, and who owns/renews the TLS certificate.
- Reverse proxy choice and its own configuration/hardening (not prescribed above).
- Production email provider credentials.
- Legal jurisdiction, support owner, pilot size, translations, TNoodle/cubing.js license
  sign-off.

This guide and the images it describes do not make any of the above decisions, and nothing in
this repository creates, alters, or deletes a cloud resource.

# TwistMeet operations runbook

This covers M6's reliability/monitoring/incident scope from
`product-docs/12-release-acceptance.md` ("Reliability/accessibility" and "Deployment runbook
outline"). It describes the mechanisms that exist today and is written for whoever is on call
during a pilot event, not a hypothetical future operator — every procedure here either has been
run and verified in this repository, or is marked as a launch blocker still needing an external
decision (hosting, domain, a real production database).

**Current deployment status**: no production environment exists yet. There is no Neon project
provisioned (see DECISIONS.md "Neon findings") and no hosting/domain decision made. Everything
below describes the mechanism and procedure; the specific endpoints, credentials, and schedule
columns are placeholders until those decisions are made (see "Launch blockers" at the end).
Production Docker packaging (`api/Dockerfile`, `web/Dockerfile`, `docker-compose.production.yml`,
`DEPLOYMENT.md`) now exists for a small V1 pilot — build/run mechanics, health checks, migration
ordering, and upgrade/rollback are documented there; it does not change the "no production
environment exists yet" status above, since nothing has actually been deployed to a real host.

## Backup and restore

### Mechanism

- **Local/CI**: plain PostgreSQL in Docker Compose / the CI Postgres service container. No
  automated backup exists for this environment by design — it is throwaway dev/test data, reset
  on every test run by `AbstractIntegrationTest`'s `flyway.clean()`/`flyway.migrate()`.
- **Production (once provisioned)**: the stack decision (DECISIONS.md "Stack decision") targets
  Neon PostgreSQL. Neon provides automated, continuous backups with point-in-time restore (PITR)
  as a managed-service feature — this product does not need to implement its own backup job,
  only to know how to invoke Neon's restore and to rehearse doing so. **This has not yet been
  rehearsed against a real Neon project**, because no Neon project is provisioned for this
  product yet (a standing constraint of this task forbids creating one without that decision
  being made first — see DECISIONS.md OD15). The drill below is the same underlying
  `pg_dump`/`pg_restore` mechanism, run against this repository's own Postgres, as a stand-in that
  proves the *procedure* works; the production runbook is to use Neon's own branch-restore (or
  point-in-time restore) UI/API instead of a manual `pg_dump`, since Neon's restore is faster,
  verified by Neon itself, and doesn't require shipping a dump file anywhere.

### Restore drill performed (this session, local Postgres)

Run directly against this repository's local Postgres instance to prove the dump/restore
mechanism end to end, not just described:

```
# 1. Seed a marker row so a successful restore is unambiguous
psql -d twistmeet_test -c "CREATE TABLE backup_drill_marker (...); INSERT INTO backup_drill_marker ..."

# 2. Dump
pg_dump -F c -d twistmeet_test -f twistmeet_test_<timestamp>.dump

# 3. Restore into an isolated database (never restore over a live one directly)
createdb twistmeet_restore_drill
pg_restore -d twistmeet_restore_drill --no-owner --role=twistmeet twistmeet_test_<timestamp>.dump

# 4. Verify
psql -d twistmeet_restore_drill -c "SELECT * FROM backup_drill_marker;"   # marker row present
# row counts on flyway_schema_history (and by extension every table) matched source exactly
```

Result: **passed**. The marker row and all table row counts (verified via
`flyway_schema_history`, representative of every table since the whole schema is Flyway-managed)
matched exactly between source and restored database. The drill database and marker table were
dropped afterward; no artifact of the drill was left in the shared dev/test database.

### Production restore procedure (to follow once a Neon project exists)

1. Identify the target point in time (an incident timestamp, or "most recent backup").
2. Use Neon's branch-from-point-in-time feature to create a new branch at that timestamp — this
   does not touch the live database, so it is always safe to do first and verify before cutting
   over.
3. Smoke-test the new branch: run the API's Flyway `info` against it to confirm migration state,
   spot-check a few known rows (an event, its entrants, its results) against what's expected.
4. Only after verification, follow "Deployment runbook outline" step 5 (`12`): point the
   application's `TWISTMEET_DB_URL` at the restored branch, or promote the branch to primary per
   Neon's own procedure — under incident-lead approval, after assessing write loss since the
   restore point, per the existing `12` deployment runbook this file does not duplicate.
5. Record the incident per "Incident response" below.

### Backup/restore rehearsal cadence

Rehearse the Neon restore procedure (steps 1-3 above, without cutover) at least once before the
first pilot event, and again after any schema change that significantly restructures a core
table (entrants, attempts, rounds). Record each rehearsal's date and result in this file's
"Rehearsal log" below.

### Rehearsal log

| Date | Environment | Result | Notes |
|---|---|---|---|
| This session | Local/CI Postgres (`twistmeet_test`) | **Passed** | `pg_dump`/`pg_restore` round-trip verified byte-for-byte on a marker row and exact row-count match; see "Restore drill performed" above. Not yet rehearsed against Neon — no project provisioned. |

## Monitoring and health checks

### What exists today

- **`GET /actuator/health`** (Spring Boot Actuator, already `permitAll` in `SecurityConfig`):
  aggregates the standard health indicators — database connectivity (via the configured
  `DataSource`), disk space, and Flyway migration state — into a single up/down signal.
  Kubernetes-style liveness/readiness probes are enabled (`management.health.probes.enabled`),
  so a container orchestrator can distinguish "process alive" from "ready to serve traffic."
  Email/mail health is explicitly disabled (`management.health.mail.enabled: false`) since the
  mail catcher is best-effort dev infra and `MailService` already degrades gracefully on send
  failure (DECISIONS.md OD04) — mail being briefly unreachable should never flip the whole
  service unhealthy.
- **Structured logs with correlation IDs**: every log line is tagged `[%X{requestId}]`
  (`logging.pattern.console` in `application.yml`), and `AuditService` separately records every
  staff/guest action that changes state (who, what, when) to the `audit_events` table, queryable
  via `GET /events/{eventId}/audit`.
- **Scramble-access auditing**: every reveal/official-view/print/mark-applied/mark-checked/spoil
  action is an `AuditEvent` row (00 §7's audit-trail requirement), which is what "suspicious
  scramble access" monitoring (`12`) would alert on — the data is captured; no alerting rule is
  wired to it yet (see "Launch blockers").

### What does not exist yet (launch blockers, not silently skipped)

- **No APM / error-tracking service** (Sentry, Datadog, or similar) is wired in. The contract
  (`00` §10) asks for "error reporting with sensitive-field redaction," which needs a concrete
  external service choice — this is an operational decision for the product owner, not something
  to default silently, since it involves where application data (including stack traces that may
  contain request details) gets sent.
- **No alerting** is configured on top of `/actuator/health`, Flyway migration failures, backup
  completion, or the scramble-access audit trail. Wiring an alert needs a destination (email,
  Slack, PagerDuty) — another external decision.
- **No load test** has been run (`12`: "Load test at 2× expected pilot concurrency and event-day
  realtime fan-out passes"). This needs an actual pilot-size estimate first (see "Launch
  blockers" below) — testing against an arbitrary guessed concurrency would not produce a
  meaningful result.

### Minimum viable monitoring for a pilot (recommendation)

Given the above, the lowest-effort monitoring that still meets `12`'s bar for a *pilot* (not a
public launch) is: an external uptime check hitting `/actuator/health` on a 1-5 minute interval
with a push/SMS alert on failure (e.g. a free-tier UptimeRobot/Better Uptime check), plus the
hosting platform's own basic metrics (CPU/memory/request rate) if it provides any by default.
This needs no new code — only the hosting decision from "Launch blockers" to have a URL to check.

## Incident response

### Scramble exposure (`12` deployment runbook step 6, restated with this codebase's specifics)

1. Disable the affected round's reveal path: the fastest way with zero deploy is to close the
   round (`POST /rounds/{roundId}/close` after `review`) if closing doesn't erase evidence
   needed for the investigation, or revoke the specific staff assignment that performed the
   unauthorized reveal (`DELETE` the `EventStaffAssignment`, or deactivate the account) to stop
   further access by that actor immediately.
2. Rotate unused batches: any scramble batch for the affected round that has unrevealed
   assignments should be treated as compromised if there's any doubt about scope; `spoil` each
   unrevealed assignment it's unsafe to keep, which burns the secret and assigns an unused extra
   (`ScrambleService.spoil()` — already audited, already enforces "never reuse a revealed or
   burned scramble").
3. Preserve audit evidence: `GET /events/{eventId}/audit` and the `scramble_assignments` table's
   `revealedAt`/timestamps are the evidence trail — do not delete or truncate anything while an
   investigation is open.
4. Notify the event owner/organizer and, if competitor-facing fairness is affected, the affected
   competitors, per whatever the pilot's support-contact decision ends up being (see "Launch
   blockers").
5. Investigate every assignment revealed by the implicated actor or in the implicated time
   window using the audit trail; determine whether any live competition result needs voiding
   via the existing correction flow.
6. File the incident per "Record operational issues and review within 48 hours" (`12`
   closeout) — this repository has no incident-tracking tool wired in; use whatever lightweight
   tracker the pilot owner already has (a shared doc is sufficient at pilot scale).

### Event outage (venue network/app down mid-event)

1. Direct event staff to the printed offline score sheet
   (`rounds/[roundId]/print-sheet`, linked from Tournament Control) — record times/results on
   paper exactly as they would have entered them digitally.
2. Never ask staff to share login credentials or future (unrevealed) scramble sequences over an
   insecure channel (phone photo of a screen, a messaging app) to work around the outage — the
   paper sheet exists specifically so this is never necessary.
3. Once the system is back, re-enter every paper-recorded result through the normal judge-entry
   screen per attempt (`PUT /attempts/{attemptId}/judge-result`) — this naturally creates the
   same `ResultRevision` audit trail as a live entry would have, so there is no special "bulk
   import from paper" path that could bypass per-attempt validation or the revision history.
4. If a result was already in progress digitally when the outage started (a self-timed attempt
   `RUNNING`/`STOPPED` but not yet submitted, or a judge entry mid-draft), treat the paper-
   recorded value as authoritative — the digital draft was never committed, so there is nothing
   to reconcile against; this is why self-timed submission is a single idempotent call
   (`AttemptService.submitSelfTimed()`) rather than a multi-step draft that could be left in a
   partial state across an outage.

### Database migration failure during a deploy

Follows `12`'s deployment runbook steps 3 and 5 directly — this file does not duplicate them.
The one addition specific to this codebase: `spring.jpa.hibernate.ddl-auto: validate` (every
environment, not only production) means a missing or mismatched migration fails the application
at startup with a clear Hibernate validation error naming the mismatched table/column, rather
than Hibernate silently patching the schema — so a bad migration is caught before it can ever
serve traffic, not discovered later as a data-integrity surprise.

## Launch blockers surfaced by this review

These require a decision from the product owner / a human operator before a real pilot, not
something this task can default on its own:

- **Hosting and domain** — no decision made yet (DECISIONS.md's Neon-findings note and the stack
  decision assume a target but no concrete provider/region/domain has been chosen).
- **Production email provider** — `application.yml`'s mail config defaults to a local
  dev/test catcher; a real pilot needs a real SMTP/transactional-email provider for registration
  verification and any notification email.
- **Error-tracking/APM service** — needed for `00` §10's "error reporting with sensitive-field
  redaction"; no default chosen here since it's a vendor/cost decision.
- **Support contact / incident response owner** — `12`'s "status page/support route and incident
  response owner exist" names a person, which this task cannot assign.
- **Expected pilot concurrency** — needed before the `12` load test can be run meaningfully.
- **Legal/privacy jurisdiction(s) for the pilot** — see the Security and privacy review
  (separate document) for what this gates.
- **Translations / localization** — `00` §2 item 12 asks for "localization-ready strings," which
  the UI text is written to support (no hard-coded concatenated sentences that would break under
  translation), but no actual translation has been produced or requested.

None of these block finishing the V1 feature set or this M6 documentation/testing pass — they
block an actual public launch, and are recorded here rather than assumed or silently deferred.

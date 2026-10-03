# System architecture and security

## Architecture direction

Start as a modular monolith with a relational database and a small real-time channel. This fits a small team and keeps round closure, score calculation and audit behavior transactionally coherent. Split services only after measurable load or team ownership requires it.

```text
Responsive web app
  ├── Competitor / judge / organizer / display views
  └── Offline queue for explicitly supported judge-entry actions
             │ HTTPS + authenticated real-time channel
Application API (modular monolith)
  ├── Identity and organizations
  ├── Events and registration
  ├── Rules and scoring
  ├── Scramble vault and assignment
  ├── Attempts, corrections and advancement
  ├── Public display and exports
  └── Audit and notifications
             │
PostgreSQL + encrypted backups + object storage for generated print assets
```

## Core entities

- `User`: staff identity, authentication settings, deletion state.
- `Organization`, `OrganizationMembership`, `RoleGrant`: tenant boundary and scoped staff roles.
- `Event`: owner, visibility, timezone, lifecycle, rule version, join-code hash, privacy settings.
- `EventEntrant`: event-scoped guest identity, display name, check-in and status; no global identity by default.
- `EventStaffAssignment`: role and scope for an event.
- `Event`, `Round`, `RoundGroup`: configuration snapshots and lifecycle.
- `RulesetVersion`: immutable format, penalty, tie, advancement and disclosure configuration.
- `ScrambleBatch`, `ScrambleSecret`, `ScrambleAssignment`: encrypted sequence, source/version, assignment, reveal audit and used/extra status.
- `Attempt`: immutable attempt identity, entrant, round, scramble assignment, timer source, started/stopped values, raw time, penalty, status, accepted result.
- `CorrectionRequest`, `ResultRevision`: request and append-only decision history.
- `AuditEvent`: actor, tenant, event, action, target, timestamp, reason and correlation ID.
- `PublicSnapshot`: optional publication cache containing only approved fields.

Canonical times should be integer milliseconds (or centiseconds if product precision is deliberately set); never use binary float. Store raw value and penalty separately. A versioned pure scoring function derives best, average, DNF and rank from attempt records.

## Security boundaries

- Tenant ID must be enforced on every query and write; test cross-tenant access systematically.
- Staff uses verified account and MFA. Competitor guest access uses random high-entropy, short-lived, event-scoped credentials stored securely; never use a sequential entrant ID or reusable join code as authorization.
- Join code is invitation convenience, not a password. Rate limit code attempts, provide rotation and expiry, and avoid exposing entrant data before successful join.
- Enforce authorization on the server for every action; client-side hiding is not security. Check role, event scope, lifecycle and action policy.
- Keep scramble payload in a separate protected service/module with narrow access. Public and competitor endpoints must not include future scrambles in JSON, HTML, source maps, caches, logs or analytics.
- Encrypt transport; encrypt sensitive fields and backups at rest; manage keys outside the database; rotate secrets; redact logs.
- Use secure, HttpOnly, SameSite cookies for staff sessions; CSRF protection where applicable; short-lived access; revocation; session rotation; rate limits and abuse monitoring.
- Prevent stored/reflected XSS in display names and event text. Use output encoding, content security policy, upload restrictions and dependency scanning.
- Audit privileged changes and security-relevant scramble reveals. Audit logs are append-only to normal application operators and exported securely.

## Privacy and safeguarding

- Collect only event display name and necessary operational metadata. Do not require date of birth, legal name, phone, email or precise location for guest participation.
- Public event owners choose whether results are public; default public view contains display name and results only, with an option to use aliases or initials.
- Define retention periods for abandoned guest sessions, event data, audit logs and backups. Give organizers export and deletion processes while preserving legitimate dispute/audit needs with a stated schedule.
- Because school and youth use is likely, default to no direct messaging, no public competitor profiles, minimal personal data and organizer-controlled visibility. Before worldwide launch, obtain jurisdiction-specific advice on children’s privacy, consent, cookies, data transfers and deletion obligations.
- Publish privacy notice, terms, acceptable use, security contact, incident process and subprocessor list before public launch. These require legal review; this document is not legal advice.

## Reliability and offline operation

- Use managed PostgreSQL with point-in-time recovery, encrypted scheduled backups and regular restore drills.
- Store event writes transactionally. Commands carry idempotency keys; real-time notifications are advisory and can be recovered by refetching authoritative state.
- Offline queue only supports judge result-entry commands and explicitly safe event actions. Queue records event ID, attempt ID, client timestamp, actor, monotonic local sequence, payload hash and signed-in session identity. Re-authentication may be required on reconnect.
- Define conflict handling; preserve both values and require human resolution. No auto-merge of result corrections.
- Observe API errors, queue depth, websocket disconnections, scramble access events, failed login/join attempts, database health and backup status. Avoid collecting unnecessary device fingerprints.

## Scramble generation component review

Before implementation, choose a supported library/service by checking: legal move parsing, random-state correctness for supported puzzle, generator maintenance, license and attribution, deterministic test vectors, secure randomness where relevant, server-side compatibility, performance and known issues. Pin versions; review update notices; keep test fixtures. Avoid representing a 3D rendering library as the source of randomness or truth.

## API and event model

Use explicit commands such as `JoinEvent`, `AssignAttempt`, `RevealScramble`, `MarkPrepared`, `SubmitAttempt`, `RequestCorrection`, `ResolveCorrection`, `CloseRound`, `CommitAdvancement`, and `PublishResults`. Validate every transition against event state. Use optimistic concurrency versioning so two staff members do not silently overwrite each other. Emit minimal real-time state deltas; clients can refetch authoritative state after reconnect.

## Proposed operational targets

- Availability: 99.5% monthly at pilot scale.
- Recovery point objective: 15 minutes or better for primary records; offline paper workflow covers venue outages.
- Recovery time objective: 4 hours for service restoration; event can continue on printed records.
- Security incident triage begins within one business day for standard reports and immediately for suspected exposure of future scrambles or personal data.
- Run dependency and secret scans in CI; production changes require reviewed migration and rollback plan.

# Data and API contract

This is a technology-neutral behavior contract with a Spring Boot modular monolith reference. Route names can be adapted, but resource semantics, tenant checks, state transitions, idempotency and audit behavior must remain.

## API conventions

- Base path `/api/v1`; JSON; UTF-8; UTC ISO-8601 timestamps; timezones are IANA names; canonical durations are integer milliseconds.
- Staff endpoints use secure session authentication; guest endpoints use event-scoped opaque credential. Never put credential in a query string. Use `Authorization: Bearer` only if architecture review explicitly chooses token auth; default is secure HttpOnly SameSite cookies plus CSRF token for browser writes.
- Every event query checks organization membership or event guest scope. Return 404 for inaccessible objects to reduce enumeration.
- Writes require `Idempotency-Key` UUID. Responses include `requestId`, resource `version`, and server timestamp. Optimistic concurrency uses `If-Match` or `expectedVersion`.
- Errors: `{code, message, fieldErrors?, requestId}`. Stable codes include `EVENT_CLOSED`, `JOIN_CODE_INVALID`, `CAPACITY_REACHED`, `FORBIDDEN`, `STALE_VERSION`, `INVALID_TRANSITION`, `DUPLICATE_ATTEMPT`, `CORRECTION_PENDING`, `SCRAMBLE_NOT_AVAILABLE`, `RATE_LIMITED`.
- Paginate list endpoints by opaque cursor. Do not include secrets/scramble payloads in generic event DTO.

## Core resources

Organization `{id, name, slug, defaultTimezone, createdAt, version}`.
Event `{id, organizationId, name, description, startsAt, timezone, venueLabel, visibility, state, puzzleType, timerMode, scramblePolicy, registrationState, rulesetSnapshot, publishedAt, createdAt, version}`.
Entrant `{id, eventId, displayName, status, checkInState, joinedAt, groupId?, stationLabel?, version}`. Never include guest credential hash/token.
Round `{id, eventId, order, name, format, attemptCount, advancementRule, tiePolicy, state, rulesetVersion, version}`.
Attempt `{id, eventId, roundId, entrantId, attemptNumber, scrambleAssignmentId?, resultSource, state, rawTimeMs?, penalty, adjustedTimeMs?, resultStatus, startedAt?, stoppedAt?, submittedAt?, version}`.
Correction `{id, attemptId, requestedBy, category, note, state, decision?, decidedBy?, reason?, createdAt, decidedAt?}`.
Scramble assignment response is a distinct protected DTO only available to assigned officials or current self-scramble entrant after unlock: `{assignmentId, scrambleId, notation, puzzleType, roundId, attemptNumber, revealedAt}`.

## Endpoint contract

### Identity and organization

- `POST /auth/register` staff owner registration; verify email before creating live event. Response is identical whether or not the submitted email is already registered (no account fields, no status difference) — registration must not let a caller enumerate which emails exist (12 SP04).
- `POST /auth/email/verify` confirms the token from the registration email and marks the account verified. *(Added at M1 implementation time: this document's original list named the "verify email" step in the register bullet above but did not itemize the confirmation endpoint itself; documented here per `00` §12 rather than left undocumented. See `TRACEABILITY.md` row 65.)*
- `POST /auth/login`, `POST /auth/logout`, `POST /auth/password/forgot`, `POST /auth/password/reset`, `POST /auth/mfa/totp/enroll`, `POST /auth/mfa/totp/verify`.
- `GET /me`, `GET /organizations`, `POST /organizations`, `GET /organizations/{id}/members`, `POST /organizations/{id}/members/invitations`, `PATCH /organizations/{id}/members/{userId}`.

### Events and registration

- `POST /organizations/{orgId}/events` create draft with full validated config.
- `GET /organizations/{orgId}/events?state=&from=&to=&cursor=` list staff-visible events.
- `GET /events/{eventId}` role-filtered detail; response never contains scramble secrets.
- `PATCH /events/{eventId}` draft-only or safe metadata changes; if registered entrants exist, rules/config fields are immutable without an explicit versioned change flow.
- `POST /events/{eventId}/registration/open`, `/lock`, `/reopen`; audited.
- `POST /events/{eventId}/join-codes/rotate`; old code invalid immediately; rate limit.
- `POST /join/{joinCode}` validates code and returns event summary plus guest credential in secure cookie; never returns roster.
- `GET /guest/events/{eventId}/me`; `PATCH /guest/events/{eventId}/me` display name only before start or through staff correction.
- `POST /events/{eventId}/entrants` staff add; `PATCH /events/{eventId}/entrants/{entrantId}`; `POST .../check-in`; `POST .../withdraw`; `DELETE ...` only before attempt assignment; deletion action audited and returns tombstone.

### Rounds and attempts

- `POST /events/{eventId}/rounds` draft event only; `PATCH /rounds/{roundId}` before live.
- `POST /rounds/{roundId}/prepare` freezes entrants/ruleset and allocates attempt slots/scramble assignments.
- `POST /rounds/{roundId}/ready`, `/start`, `/pause`, `/review`, `/close` with state checks.
- `GET /rounds/{roundId}/control-state` role-filtered snapshot; pagination/filter.
- `GET /attempts/{attemptId}` returns only authorized fields.
- `POST /attempts/{attemptId}/start` and `/stop` only for phone mode, current entrant credential, current attempt and correct state. Server receives client elapsed duration plus timestamps/monotonic metadata, but stores source as self-timed; server does not claim to validate physical elapsed time.
- `POST /attempts/{attemptId}/submit` phone mode; payload `{rawTimeMs, penalty, clientStartedAt?, clientStoppedAt?, clientBuild}`. Penalty DNF/DNS rules validated; repeated key returns same result.
- `PUT /attempts/{attemptId}/judge-result` judge only, payload `{rawTimeMs?, penalty, note?, expectedVersion}`; validates state and records revision.

### Scrambles

- `POST /rounds/{roundId}/scramble-batches` organizer, generates/seals batch; returns counts/IDs only.
- `GET /scramble-assignments/{id}/official-view` assigned scrambler/judge only; no batch fetch.
- `POST /scramble-assignments/{id}/reveal` audit event then returns one assignment payload. Must be idempotent for same authorized actor/request but never bulk reveals.
- `POST /scramble-assignments/{id}/mark-applied`, `/mark-checked`; actor/time required; optional independent checker.
- `POST /scramble-assignments/{id}/spoil` organizer/judge, reason, consumes sequence if revealed and assigns unused extra where configured.
- `GET /guest/.../current-scramble` only self-scramble mode and only when attempt unlocked. Never return future assignment.

### Corrections, standings, display and export

- `POST /attempts/{attemptId}/correction-requests` entrant; category/note validation.
- `GET /events/{eventId}/corrections?state=pending` organizer/judge authorized.
- `POST /corrections/{id}/decision` organizer, payload `{action: ACCEPT_NO_RETRY|ACCEPT_RETRY|REJECT|NEED_INFO, reason, expectedVersion}`.
- `GET /rounds/{roundId}/standings` authorized; includes provisional/final flag, exact ranking sort values and ruleset version.
- `POST /rounds/{roundId}/advancement/preview`; `POST .../commit` with expected version and idempotency key; returns committed entrants + tie notes.
- `POST /events/{eventId}/publish`, `/unpublish`; `GET /public/events/{publicSlug}` limited DTO; `GET /public/events/{publicSlug}/standings` only published data.
- `GET /organizations/{orgId}/events/{eventId}/export.csv` authorized; `GET /events/{eventId}/audit` owner/organizer restricted.

### Real-time updates

Use SSE or WebSocket with authenticated, event-scoped subscriptions. Initial snapshot comes from REST; updates contain `{eventId, eventVersion, eventType, resourceId, changedFields, occurredAt}` and no scramble text. Client reconnects with last event ID or refetches snapshot. Public channel emits only published fields. Rate limit subscriptions; unsubscribe on scope change/logout.

## Database invariants

- Foreign keys include tenant/event IDs so a row cannot be attached across organizations accidentally.
- Unique `(round_id, entrant_id, attempt_number)`; unique scramble assignment use; unique public slug; unique active join code hash.
- Append-only audit and result revision records; no cascade delete from organization that erases live event evidence.
- Store raw result, penalty, source, result status, ruleset version and actor separately. Derived scores can be recalculated deterministically.
- Scramble notation encrypted separately from ordinary event data; encryption keys managed outside DB; reveal metadata audit has no notation content.
- Guest credentials are random ≥128-bit secrets, stored hashed, event-scoped, revocable and expiring after event retention period. Join codes are rate-limited and not authorization credentials.

## API security tests

Automated tests must prove: competitor cannot fetch other entrant detail; spectator cannot see unpublished results; judge cannot change round config; scrambler cannot publish results; organization A cannot access organization B; competitor cannot fetch staff-prepared scramble; self-scramble entrant cannot fetch future attempt; expired/rotated join code fails; repeated writes are idempotent; stale version gives a conflict without overwriting; public DTO has no email/token/notes/scramble fields.

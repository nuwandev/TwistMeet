# Data retention decisions (open — owner action required)

This is an **owner decision register**, not an implementation. It exists because 04
(`product-docs/04-architecture-security.md`) requires "Define retention periods for abandoned
guest sessions, event data, audit logs and backups. Give organizers export and deletion processes
while preserving legitimate dispute/audit needs with a stated schedule," and because the V1
acceptance pass explicitly must not invent a retention period or perform destructive deletion
without one. What *is* built — the policy-independent mechanism — is described first; the actual
retention periods below are each an open choice for whoever owns this product's launch to make.

## What's built today (no policy decision required)

- `users.deletion_requested_at` (migration `V8`) and `User.requestDeletion()`: a staff member can
  record a deletion request on their own account via `POST /api/v1/me/deletion-request`. Setting
  it is idempotent (a repeat request doesn't change the timestamp) and performs **no** automated
  deletion, anonymization, or data loss — it is a flag for a human operator to act on.
- `GET /api/v1/me/export` (staff) and `GET /api/v1/guest/events/{eventId}/me/export` (competitor):
  self-service personal-data export, returning exactly what the caller could already read through
  other endpoints (profile, org memberships, event staff assignments / entrant record, own
  attempts, corrections and help requests filed).
- Every audit event this feature writes (`AuditService.recordSelfServiceAction` /
  `recordGuestAction`) is itself subject to whatever audit-log retention is decided below — this
  mechanism does not special-case itself out of that policy.

None of the above requires picking a retention period, because none of it deletes anything. The
decisions below are what an operator needs before *acting* on a deletion request, or before this
product can truthfully publish a retention schedule in a privacy policy.

## Decision 1 — Abandoned guest sessions / unused guest credentials

A guest who joins an event and never returns leaves an `EventEntrant` row and a `GuestCredential`
(hashed, cookie-bound, already time-boxed to `CREDENTIAL_TTL` = 30 days for the *cookie session*
itself — see `JoinController`). The underlying `EventEntrant`/`Attempt` rows are **not** deleted
when the credential expires.

| Option | What it means | Trade-off |
|---|---|---|
| A. Keep indefinitely while the event exists | No deletion; rely on event-level retention (Decision 2) | Simplest; defers the real decision to Decision 2 |
| B. Purge entrants with no attempts after a fixed window (e.g. 90 days post-event) | Scheduled cleanup job removes never-competed entrants | Reduces stored PII for people who never actually competed; needs a new scheduled job (none exists today) |
| C. Purge immediately on event archive if no attempts exist | Tie cleanup to the existing archive action (`EventService`/`EventArchiveCloneTest`) rather than a new scheduler | Reuses an existing lifecycle hook; still needs implementation |

**Recommendation:** C — it reuses the archive action that already exists and is organizer-
triggered (no new background job to build/operate), and only ever removes entrants who never
generated a result, so it can't affect standings, disputes, or audit needs.

## Decision 2 — Event data (entrants, attempts, corrections, help requests, scrambles)

This is the core competitive record. 09/02 et al. assume historical results stay queryable
(organization event history, M5) and disputes can be re-examined after the fact.

| Option | What it means | Trade-off |
|---|---|---|
| A. Retain indefinitely | No deletion ever, short of a manual deletion request being acted on | Matches most competition records (WCA-style results are kept permanently); simplest; largest long-term storage footprint |
| B. Retain N years post-event, then anonymize entrant display names but keep aggregate results | Preserves historical rankings/records while removing the name-to-person link | Requires defining "anonymize" precisely (what happens to a `displayName` already published in CSV exports people downloaded?) |
| C. Retain N years post-event, then hard-delete entrant/attempt rows entirely | Simplest technically | Loses historical record integrity organizers may still want (club "all-time" boards) |

**Recommendation:** A for now, revisited once there's an actual pilot with real retention
pressure (storage cost, or a specific legal retention limit for the pilot's jurisdiction — see
OPERATIONS.md's open "legal jurisdiction for the pilot" item, which this decision depends on and
should not be made ahead of). Recommending "indefinite" is not the same as "no decision" — it's
the explicit, stated default until a jurisdiction-specific requirement overrides it.

## Decision 3 — Audit logs (`audit_events`)

Append-only by design (00 §4/§10, enforced at the migration level). 04 asks for "a stated
schedule," which today does not exist — there is no deletion path for `audit_events` at all, by
design (append-only), so "retention" here really means "how long before a backup/export of this
table is itself discarded," not in-database deletion.

| Option | What it means | Trade-off |
|---|---|---|
| A. Retain indefinitely in the primary database | Matches the append-only design intent | Simplest; grows unboundedly with usage |
| B. Retain N years in the primary database, then archive to cold storage and delete from the live table | Bounds live-table size | Requires building an archival export + a (very rare, append-only-breaking) deletion path — real engineering work, not done here |

**Recommendation:** A until the pilot's actual usage volume is known. Revisit if/when table size
becomes an operational concern — premature to build an archival pipeline for a system with no
pilot data yet.

## Decision 4 — Backups

No backup strategy exists yet at all (this is infrastructure/hosting scope, tracked alongside the
hosting/domain launch decision in OPERATIONS.md, not something this codebase controls). Backup
retention is a function of whatever hosting/backup provider is chosen and cannot be decided here.

**Recommendation:** decide alongside the hosting/domain launch decision; do not decide in
isolation.

## What an operator does once these are decided

1. Fill in the chosen option (or a different one) for each decision above, with a date and who
   decided it.
2. For Decision 1/2/3, if any option other than "retain indefinitely" is chosen, that becomes a
   new implementation task — a scheduled job or an archive-action hook — scoped and built
   separately, with its own tests, rather than retrofitted into this register.
3. For any `User` with a non-null `deletionRequestedAt` (queryable directly, or add an admin
   endpoint once there's an admin surface to put it on — none exists today), an operator manually
   reviews what can be deleted/anonymized consistent with Decision 2/3 above and the dispute/audit
   carve-out 04 requires, and performs that action by hand or via a follow-up tool — this register
   does not build that tool, since its shape depends entirely on which option is chosen above.

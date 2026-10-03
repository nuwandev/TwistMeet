# TwistMeet authoritative build contract

Version 2.0 · 4 October 2026 · Working name only

This is the controlling implementation specification for the first shippable product. TwistMeet is a provisional project name; do not treat it as a cleared brand or domain. This contract converts the product brief into observable behavior for a software team or coding agent. The other documents explain the rationale and provide screen, schema, scoring, guide, and release detail. If two documents conflict, follow this file, then the more specific contract in `07`, `08`, and `09`, and record the conflict instead of silently choosing.

## 1. Product outcome

An organization can create an unofficial 3×3 speedcubing event, invite people by QR/link/short code, run attempts using either judge-entered physical timers or an explicitly casual phone timer, calculate published results under a disclosed ruleset, monitor progress, advance competitors, and retain an event history. A first-time organizer can do this on a phone or laptop without understanding a competition database.

The app is a competition operations tool. It is not a physical timer instrument in judge-controlled mode, a WCA Delegate, a judge, a scramble-state verifier, an anti-cheat guarantee, or a source of WCA-sanctioned results.

## 2. Product boundaries

### V1 must ship

1. Responsive web app with staff accounts, organization workspaces and tenant isolation.
2. Event create/edit, public/private choice, timezone, date, description, rules preview, clone, archive and event history.
3. QR, join URL and short code. Guest competitor joins with display name and event-scoped device credential; no competitor account required in physical timer mode.
4. Easy roster management: add, edit display name, check-in, remove before start, withdraw after start without deleting records, prevent silent duplicate merge, lock/unlock registration.
5. 3×3×3 single event with Single Round, Quick (qualifier + final) and Custom round setup. Formats: Bo1, Bo2, Bo3, Mo3, Ao5. Advancement: everyone, top N, top percentage; exact tie handling below.
6. Two primary operation modes: (A) Physical Timer + Judge Entry; (B) Competitor Phone Timer, labelled Casual / Self-timed. The first is default for an in-person event. A later Remote mode is not silently included in V1.
7. Organizer-configurable scramble source policy: staff-prepared (recommended for judged in-person) or competitor self-scramble (only enabled in casual phone mode; prominent fairness disclosure). Generated scramble sets with extra sequences, role-bound release, printable sheets, 3D guide, human check attestations and audit trail.
8. Competitor status, judge/scrambler station, timer (when enabled), attempt status, request help/correction, personal result view.
9. Judge entry of time, +2, DNF, DNS, note; correction approvals; immutable revision history; no direct competitor result edit.
10. Organizer Tournament Control, real-time progress, exceptions, round close, advancement preview/commit, result publish, display mode, export CSV, event history.
11. Deterministic, versioned score/rank calculation and tests.
12. Accessibility baseline WCAG 2.2 AA, localization-ready strings/time zones, reduced-motion option, mobile support, status clarity and offline paper fallback.
13. Security, privacy, backups, monitoring, error reporting and operator documentation before public beta.

### Not in V1

No sanctioned WCA operations or WCA record claims; no native apps; no 2×2/4×4/other puzzle events; no camera cube recognition; no smart-cube Bluetooth; no global ranking; no payments/prizes; no public messaging/social graph; no remote self-timed tournament; no automatic cheating verdict; no machine claim that a 3D guide confirms a real cube. Add these only through separate approved requirements and validation.

## 3. Modes and trust labels

| Mode | Timer/result source | Scramble visibility | Required label | Allowed advancement |
|---|---|---|---|---|
| Physical Timer + Judge Entry | Judge observes a physical timer and enters result | Officials only; competitor sees prepared/covered cube | “Judge recorded · physical timer” | Yes |
| Competitor Phone Timer | Competitor’s browser/device records a self-reported time | Competitor sees sequence and 3D guide before their solve | “Self-timed · device/browser timing” | Yes only when organizer confirms this casual policy; label round/results throughout |

Organizer cannot describe phone-timed results as independently verified. Do not silently mix result sources within a round. If an exceptional replacement changes source, keep the source on each attempt and show it in result detail.

## 4. Product defaults

- Event visibility: private/unlisted by default. Public event requires explicit opt-in and confirmation of display-name/result visibility.
- Registration: open only after organizer presses “Open registration”; event draft is not joinable.
- Event timezone: organizer’s selected IANA timezone, required at creation; show timezone abbreviation alongside local date/time.
- Default puzzle: 3×3×3. Default round: one round, Ao5. Default mode: physical timer + judge entry. Default scramble policy: staff-prepared.
- Default correction policy: judge/organizer approval required; no automatic retry. One accepted replacement only per affected attempt by default, using an unused extra scramble. Organizer may configure no replacements or a different cap before registration opens.
- Default ranking tie-break: average then best single; if equal, shared rank. Advancement includes all competitors tied at the final advancing position unless organizer explicitly chooses a tie-break attempt before registration opens.
- Default public standings: unpublished until organizer publishes. Public view never includes scramble sequences, email, staff notes, guest credential or correction reason.
- Result precision: canonical integer milliseconds; physical entry permits 0.001-second units; UI shows `SS.ss` by default, `SS.sss` in detail. Store and rank by exact integer milliseconds. Do not round before sorting.
- Result input bounds: positive time from 0.001 to 9999.999 seconds. Under 0.001 or over maximum requires authorized DNF/invalid path; no negative/NaN/infinite value.
- Timer inspection: configurable per round; default 15 seconds for casual mode, with warnings at 8 and 12 seconds and a clear event-specific note that this is an app setting. Physical-mode judge follows the announced event procedure; app countdown is optional cue only.
- In-app rules text is versioned and snapshotted into the event when registration opens. Editing a rule after opening requires close registration, show affected entrants a notice and record the change.

## 5. Roles and authorization

Organization Owner: billing later; organization settings, owner transfer, staff grants, workspace deletion. Organizer: full event configuration and event administration, close/publish, approve correction. Judge: assigned event attempt entry/status and help escalation; cannot change rounds, roster removal after start, scramble policy or publish. Scrambler: assigned scramble and 3D guide; mark physical preparation checked; no results or roster access unless also assigned another role. Competitor: own event state, self timer if enabled, correction request; cannot change submitted results, see another competitor’s private detail or officials-only scrambles. Spectator: published public fields only.

Use server-side authorization on every request. A staff member can hold multiple roles. Display role and event scope visibly on staff pages. Staff account roles are organization-scoped; event staff assignment can narrow access further. The organizer who creates an event is owner/organizer for that event. Require an explicit confirmation for round close, advancement, publish, result voiding and event archive.

## 6. Event and round lifecycle

Event: `DRAFT → REGISTRATION_OPEN → REGISTRATION_LOCKED → READY → LIVE → COMPLETED → ARCHIVED`. Only an organizer may transition. A completed event can be reopened for corrections; record actor/reason and recompute derived standings transparently. Archived is read-only except export/delete request processes.

Round: `DRAFT → PREPARING → READY → LIVE → REVIEW → CLOSED`. `REVIEW` begins after all slots have accepted result statuses; unresolved correction requests block close. Organizer may close with outstanding entrants only by recording DNS or authorized withdrawal for each. Advancement is separate: `PREVIEW → COMMITTED`; preview shows exact tie/percentage math and roster. Committing creates next round entrants and is auditable.

Attempt: `PENDING → ASSIGNED → PREPARING → READY → INSPECTION (optional) → RUNNING → STOPPED → SUBMITTED → ACCEPTED`. Alternate outcomes: `DNS`, `DNF`, `VOIDED`. Competitor phone timer may transition RUNNING/STOPPED from its own session; physical mode does not expose that control. Every command is idempotent. A void retains original values and requires reason + organizer permission. No hard-delete after attempt assignment.

## 7. Scramble policy

Two modes:

1. **Staff-prepared (V1, recommended):** sequences generated server-side, encrypted at rest, assigned to attempt slot, revealed only to assigned scrambler/judge. Competitor never sees notation or 3D state before solve. Officials may view current assignment only; access is audited. Mark “applied” then “checked” by human. The 3D cube is a guide to moves, not sensor-based verification.
2. **Competitor self-scramble (V1, casual phone mode only):** system assigns a distinct generated sequence to that competitor/attempt and shows notation + 3D guide on their device. Event and result are labeled self-scrambled/self-timed. This is not judge-controlled or official-equivalent. Sequence reveal is timestamped; no future attempt sequence is available before the attempt is unlocked.

Use an established generator with reviewed license, maintenance, puzzle-state correctness and version. Never describe as WCA-compliant unless generator and operating procedure are reviewed for that use. Generate at least two extra sequences per round or 10% of attempts rounded up, whichever is greater. Extra sequences have unique IDs and can be assigned once. Do not reuse a used or revealed scramble for a replacement. Keep scramble payloads out of public APIs, analytics, logs and error reports. Print only for officials. The operator can delete future unrevealed sequences after event cancellation.

## 8. Scoring, penalties and rank

Full exact rules and examples live in `09-scoring-conformance.md`. High-level invariant: attempts are source data; averages/ranks are derived. Ao5 discards one fastest and one slowest adjusted value; one DNF/DNS can be worst, two make Ao5 DNF. Mo3 is arithmetic mean of three with any DNF/DNS causing DNF. BoX ranks by best adjusted valid attempt. +2 applies to raw time before comparison. DNF/DNS are worse than any valid time. Event rules choose displayed rounding; sorting uses exact integer milliseconds.

## 9. Product UI contract

Screens and states are specified in `07-screen-and-component-spec.md`. Required navigation:

- Public: home/marketing, public event page, join, published results.
- Staff: sign-in, organization switcher, organization dashboard, events list, event wizard, event detail, registration roster, round setup, scramble station, Tournament Control, judge entry, correction queue, advancement preview, results/publish, display mode, history/export, staff/settings, help.
- Competitor: join, rules/consent summary, waiting room, station/group, scramble/timer only where allowed, attempt submitted, correction request, personal results.

UI must be usable at 360 CSS px, support keyboard, screen readers, text zoom to 200%, reduced motion. Use color plus icon/text for status, focus visibility, labels, non-blocking help and loading/error/empty/saved/unsynced states on every data-entry screen.

## 10. Build architecture and stack

Reference stack for a new build: Spring Boot Java modular monolith API; PostgreSQL; Next.js/React TypeScript web; HTTPS; server-sent events or WebSocket for event-room updates; Docker Compose for local development. Start without Redis. A managed object store may hold generated printable sheets; if omitted, generate on demand. Pin supported dependency versions at project start and lock them. AI must inspect repository and existing skills before replacing files; if an existing repository has a different compatible stack, document proposed deviation and preserve established conventions unless user explicitly requests a migration.

Deploy API and web app as one product with environment separation (local, staging, production). Use migrations, never Hibernate schema auto-update in production. Store secrets in platform secret manager/environment injection, never commit. PostgreSQL row-level tenant constraints or service-level tenant checks, plus tests. Use structured logs, correlation IDs and error reporting with sensitive-field redaction.

## 11. Build completion definition

“Built” means all V1 requirements, flows, score examples, role permissions, accessibility and release gates in this set pass; staging deployment is usable; seeded demo event and operator guides exist; database migration/backup restore/monitoring/error handling are documented; no secrets are committed; and unresolved limitations are listed.

“Shipped publicly” additionally requires real owner accounts, domain, production infrastructure/secrets, reviewed privacy/terms for launch markets, support and incident contacts, backups and restore drill, scramble-generator review, accessibility/security release gates and explicit product-owner deployment approval. A coding agent must not claim public launch merely because `npm build` or a local demo works.

## 12. Requirements authority

The user’s product direction controls product scope. This contract controls the implementation defaults. If something is not specified, coding agent must choose the safer simpler behavior, document the assumption in `DECISIONS.md`, add acceptance coverage, and avoid inventing a public claim. Any new capability affecting timing trust, scramble access, public personal data, advancement or deletion needs an explicit requirement and review.

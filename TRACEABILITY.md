# TwistMeet requirements traceability map

Complete map from the specification package to implementation area and test/release evidence.
Every numbered or listed item in `00`, `07`, `08`, `09`, and `12` appears below — none are
sampled or summarized away. "Area" is the planned module/screen; "Evidence" is the test or
release-checklist proof that will exist once built. Nothing here is implemented yet (M0): the
"Evidence" column states what *will* verify the requirement, not a result.

Legend: **NA** = not applicable at this stage, with reason given. Requirement IDs are introduced
by this document for traceability only; they do not appear in the source specs.

---

## A. `00-authoritative-build-contract.md` §2 — V1 must ship (13 items)

| ID | Requirement (§2 item) | Area | Evidence |
|---|---|---|---|
| R01 | Responsive web app, staff accounts, org workspaces, tenant isolation | Auth module; Org module; all API middleware | Cross-tenant authorization test suite (`08` API security tests); responsive layout check at 360px/desktop |
| R02 | Event create/edit, public/private, timezone/date/description, rules preview, clone, archive, history | Event module; S03, S04, S14 | Event lifecycle tests; `12` Product-acceptance item "Event creation wizard…" |
| R03 | QR/URL/short code join; guest display name + event-scoped credential; no account required in physical mode | Registration module; P01, P02 | `12` "QR, URL and short code join work…"; `08` join-code rate-limit/rotation tests |
| R04 | Roster: add/edit/check-in/remove-before-start/withdraw-after-start, no silent merge, lock/unlock | Roster module; S05 | `12` "Organizer can add/edit/remove…"; duplicate-name and withdraw-vs-delete tests |
| R05 | 3×3×3 single event; Single/Quick/Custom; formats Bo1/Bo2/Bo3/Mo3/Ao5; advancement everyone/topN/top%; tie handling | Round config module; S03, S06 | `09` conformance vectors (section D below); advancement boundary tests |
| R06 | Two modes: (A) physical timer+judge entry default, (B) phone/casual; no silent Remote mode | Attempt module; mode flag on Event/Round | Mode-labeling tests; `12` "Physical timer mode supports…", "Phone mode clearly labels self-timed…" |
| R07 | Organizer-configurable scramble policy (staff-prepared / self-scramble casual-only); extras, role-bound release, printable sheets, 3D guide, human attestations, audit trail | Scramble module; S07 | `12` "Scramble roles, 3D guide, applied/checked status…"; leakage security tests |
| R08 | Competitor status, judge/scrambler station, timer (when enabled), attempt status, help/correction request, personal result view | P03, P04, P05, P06, P07 | E2E competitor-journey test |
| R09 | Judge entry of time/+2/DNF/DNS/note; correction approvals; immutable revision history; no direct competitor edit | S09, S10; Attempt/Correction module | `09` property test "Revisions recalculate…"; audit append-only test |
| R10 | Organizer Tournament Control: real-time progress, exceptions, round close, advancement preview/commit, publish, display mode, CSV export, history | S08, S11, S12, S13, S14 | `12` "Round close blocks unresolved…", "Public display reveals only…" |
| R11 | Deterministic, versioned score/rank calculation and tests | Scoring module (pure function) | `09` full conformance + property test suite |
| R12 | Accessibility baseline WCAG 2.2 AA, localization-ready, reduced-motion, mobile, status clarity, offline paper fallback | Design system; all screens | `12` Reliability/accessibility checklist (section F below) |
| R13 | Security, privacy, backups, monitoring, error reporting, operator docs before public beta | Platform/ops | `12` Security/privacy + Reliability checklist; M6 exit criteria |

## B. `00` §2 — Not in V1 (exclusions — 11 items, all NA by design)

| ID | Exclusion | Status |
|---|---|---|
| E01 | No sanctioned WCA operations/record claims | **NA** — explicitly excluded; verified by absence of any WCA-sanction claim in UI copy/docs (`05` Rules gate, `12` public-facing text review) |
| E02 | No native iOS/Android apps | **NA** — web-only by design; no native build target exists or is planned |
| E03 | No 2×2/4×4/other puzzle events | **NA** — schema/UI scoped to 3×3×3 only; enforced by not building puzzle-type selection beyond 3×3×3 |
| E04 | No camera cube-state recognition | **NA** — no camera capture feature planned |
| E05 | No smart-cube Bluetooth | **NA** — no Bluetooth integration planned |
| E06 | No global ranking | **NA** — standings are event/organization-scoped only, no cross-org leaderboard |
| E07 | No payments/prizes | **NA** — no billing/payment module planned for V1 |
| E08 | No public messaging/social graph | **NA** — no DM/social features planned |
| E09 | No remote self-timed tournament | **NA** — only in-person physical and casual phone modes ship; Remote mode explicitly deferred |
| E10 | No automatic cheating verdict | **NA** — no automated fraud/anti-cheat decision logic planned; correction flow stays human-decided |
| E11 | No machine claim that 3D guide confirms a real cube | **NA** — 3D guide UI copy must state it is an instructional aid only (checked in M4 UI copy review and `12` scramble item) |

Each NA above is a scope boundary, not a skipped requirement — the "evidence" that it stays
excluded is the absence of the feature plus a UI-copy/doc check that no document or screen implies
the excluded capability (tracked at M3/M4 UI copy review and before any public launch sign-off).

## C. `00` §3–§11 — Modes, defaults, roles, lifecycle, scramble policy, stack, completion

| ID | Requirement | Area | Evidence |
|---|---|---|---|
| R14 | Physical mode: judge observes/enters result; competitor sees prepared/covered cube; label "Judge recorded · physical timer" | Attempt module; P05 | Label-presence test; scramble-not-exposed test |
| R15 | Phone mode: competitor's device records self-reported time; competitor sees sequence + 3D guide before solve; label "Self-timed · device/browser timing" | Attempt module; P04 | Label-presence test |
| R16 | Organizer cannot describe phone results as independently verified | UI copy; S03 rules preview | Copy-review checklist (M3/M4) |
| R17 | No silent mixing of result sources within a round | Round/Attempt module | Round-level source-consistency test |
| R18 | Exceptional replacement keeps original source visible per attempt | Result detail UI; S12 | Result-detail field test showing per-attempt source |
| R19–R24 | Role definitions: Owner, Organizer, Judge, Scrambler, Competitor, Spectator (`00` §5) | Role/permission module | Role-matrix authorization test (one row per role × sensitive action, per `12` "Role matrix tested for every sensitive command") |
| R25 | Server-side authorization on every request | All API controllers | `12` "Role matrix tested…hidden UI cannot substitute for backend denial" |
| R26 | Display current role/scope visibly on staff pages | `RoleBanner` component; all staff screens | Component render test |
| R27 | Staff roles org-scoped; event assignment can narrow further | Role/permission module | Scoped-access test (org role vs. event-assignment role) |
| R28 | Event creator is owner/organizer for that event | Event creation flow | Creation test asserts creator role grant |
| R29 | Explicit confirmation required for round close, advancement, publish, void, archive | `ConfirmDialog` component; S04, S08, S11, S12 | UI test: each action requires confirm dialog |
| R30 | Event lifecycle `DRAFT→REGISTRATION_OPEN→REGISTRATION_LOCKED→READY→LIVE→COMPLETED→ARCHIVED`; organizer-only transitions | Event state machine | State-machine transition tests incl. unauthorized-actor rejection |
| R31 | Completed event reopenable for corrections with actor/reason, recompute standings | Event state machine; S11 reopen path | Reopen/recompute test with before/after snapshot |
| R32 | Archived read-only except export/delete | Event state machine | Archived-state write-rejection test |
| R33 | Round lifecycle `DRAFT→PREPARING→READY→LIVE→REVIEW→CLOSED` | Round state machine | Transition tests |
| R34 | REVIEW begins after all slots resolved; unresolved corrections block close | Round state machine | Close-blocked-by-pending test |
| R35 | Organizer can close with outstanding entrants only via recorded DNS/withdrawal | Round close flow; S11 | Close-with-DNS test |
| R36 | Advancement `PREVIEW→COMMITTED`, auditable, creates next-round entrants | Advancement module; S11 | `08` advancement preview/commit idempotency test |
| R37 | Attempt lifecycle `PENDING→ASSIGNED→PREPARING→READY→INSPECTION→RUNNING→STOPPED→SUBMITTED→ACCEPTED`, alt `DNS/DNF/VOIDED` | Attempt state machine | Transition + alt-path tests |
| R38 | Phone mode allows competitor-session RUNNING/STOPPED transitions; physical mode does not expose this control | Attempt module; P04 vs P05 | Role/mode-gated transition test |
| R39 | Every command idempotent | All write endpoints | `08` "repeated writes are idempotent" test (per endpoint) |
| R40 | Void retains original values, requires reason + organizer permission; no hard-delete after assignment | Attempt/Correction module | Void-preserves-data test |
| R41 | Staff-prepared scramble behavior: server-generated, encrypted at rest, assigned to slot, revealed only to assigned scrambler/judge, never pre-shown to competitor, audited access, human applied/checked marks | Scramble module; S07 | Leakage test matrix (API/HTML/cache/log); audit-on-reveal test |
| R42 | Self-scramble behavior: distinct sequence per competitor/attempt, shown with notation+3D guide, labeled self-scrambled/self-timed, timestamped reveal, no future sequence pre-unlock | Scramble module; P04 | Reveal-timing test; future-sequence-hidden test |
| R43 | Established generator, reviewed license/maintenance/correctness/version | Scramble generation service | Dependency review record (M4); NA for automated test, process evidence only |
| R44 | Never claim WCA-compliance until reviewed | UI copy, docs | Copy-review checklist |
| R45 | ≥2 extra sequences per round or 10% rounded up, whichever greater; unique IDs; assign-once; no reuse of used/revealed scramble | Scramble batch generation | Extra-sequence-count test; reuse-prevention test |
| R46 | No scramble payload in public APIs/analytics/logs/error reports; print officials-only | API DTOs, logging config | `08`/`12` public-DTO field-allowlist test; log-scrubbing test |
| R47 | Operator can delete future unrevealed sequences after cancellation | Admin/ops tooling | Cancellation-cleanup test |
| R48 | Scoring invariant: attempts are source data; Ao5/Mo3/BoX rules; +2 before comparison; DNF/DNS worse than any valid time; display rounding vs. exact-ms sort | Scoring module | Fully covered by `09` vectors — see section D |
| R49 | Public nav: home/marketing, public event page, join, published results | P01, P02, S13 | Nav-presence test |
| R50 | Staff nav: sign-in, org switcher, dashboard, events list, wizard, event detail, roster, round setup, scramble station, Tournament Control, judge entry, correction queue, advancement preview, results/publish, display mode, history/export, settings, help | S01–S15 | Nav-presence + route-guard test per screen |
| R51 | Competitor nav: join, rules/consent, waiting room, station/group, scramble/timer where allowed, attempt submitted, correction request, personal results | P01–P07 | Nav-presence test |
| R52 | Usable at 360 CSS px; keyboard; screen readers; 200% zoom; reduced motion | All screens | `12` "At 360 CSS px no horizontal page scroll…"; a11y audit |
| R53 | Color+icon+text status, focus visibility, labels, non-blocking help, loading/error/empty/saved/unsynced states on every data-entry screen | `StatusBadge`, `SavedState`, `EmptyState`, `LoadingState`, `ErrorState`, `OfflineState` components | Component test per state; visual audit |
| R54 | Reference stack: Spring Boot modular monolith, PostgreSQL, Next.js/React TS, HTTPS, SSE/WebSocket, Docker Compose | Project scaffolding (M1) | Build/CI passes for both API and web |
| R55 | Deploy API+web as one product, environment separation (local/staging/production) | Deployment config | NA until M6 — process evidence at staging setup |
| R56 | Migrations only, never auto-DDL in production | Database/migration tooling | Migration-tool CI check; `12` "production schema update is migration-based" |
| R57 | Secrets in platform secret manager/env injection, never committed | CI/deployment config | `.gitignore` + secret-scan CI check; `12` "secrets are external to source control" |
| R58 | Tenant isolation enforced + tested (row-level or service-level) | Data access layer | `08` cross-tenant test suite |
| R59 | Structured logs, correlation IDs, sensitive-field redaction | Logging infrastructure | Log-redaction test; correlation-ID presence test |
| R60 | "Built" completion definition (all V1 reqs/flows/scores/roles/a11y/release gates pass; staging usable; seed event; operator guides; migration/backup/monitoring documented; no secrets committed; limitations listed) | Whole program | M6 exit review against `12` |
| R61 | "Shipped publicly" definition (real accounts, domain, infra/secrets, legal review, support, backups/restore drill, generator review, a11y/security gates, explicit owner approval) | Whole program | **NA at this stage** — reason: no public launch is in scope for this engagement yet; tracked for the milestone after M6 if/when authorized |
| R62 | Requirements authority: safer-simpler default + `DECISIONS.md` + acceptance coverage for unspecified behavior; new capability affecting trust/scramble/PII/advancement/deletion needs explicit review | Process rule | **NA for test evidence** (procedural) — verified by `DECISIONS.md` upkeep at every milestone, reviewed in this report's §4 |

## D. `09-scoring-conformance.md` — every conformance vector and property test (19 items)

| ID | Vector / rule | Area | Evidence |
|---|---|---|---|
| V01 | Ao5: 14.21,12.84,18.91,13.05,12.10 → 13.37 (discard 12.10, 18.91) | Scoring module | Unit test, exact fixture |
| V02 | Ao5: 14.20,13.80,DNF,15.10,13.40 → 14.37 (discard 13.40 and DNF; corrected value per note) | Scoring module | Unit test, exact fixture — **must use corrected 14.37 value**, not the earlier erroneous draft number |
| V03 | Ao5: 10.00,11.00,12.00,DNF,DNS → DNF (fewer than four valid) | Scoring module | Unit test |
| V04 | Ao5: 10.00,10.00,12.00,13.00,14.00 → 11.67 (discard one 10.00 by attempt-number tie-break, and 14.00) | Scoring module | Unit test incl. deterministic tie-break assertion |
| V05 | Mo3: 10.00,11.00,12.00 → 11.00 | Scoring module | Unit test |
| V06 | Mo3: 10.00,11.00,DNS → DNF | Scoring module | Unit test |
| V07 | Bo3: 11.00,10.00,DNF → 10.00 (best valid single) | Scoring module | Unit test |
| V08 | Penalty: raw 12.345 +2 → stored raw=12345ms, penalty=2000ms, adjusted=14345ms | Scoring module | Unit test on raw/penalty/adjusted separation |
| V09 | Rank: Ao5 12.00 vs 12.00, best singles 9.00 vs 9.50 → rank 1, rank 2 (average tie breaks on best single) | Ranking module | Unit test |
| V10 | Rank: identical average and best → shared rank 1, next rank 3 (competition ranking) | Ranking module | Unit test |
| V11 | Top N: ranks 1,2,2,4, N=2 → first three advance (boundary tie included) | Advancement module | Unit test |
| V12 | Percent: 11 eligible, 25% → target 3 (ceil(2.75)), include cutoff ties | Advancement module | Unit test |
| P01 | Attempt input order does not change score except deterministic tie selection | Scoring module | Property test (randomized order permutations) |
| P02 | Adding an attempt outside retained Ao5 three cannot change average unless it changes which extreme is discarded | Scoring module | Property test |
| P03 | +2 affects comparisons/averages by exactly 2000ms | Scoring module | Property test |
| P04 | DNF/DNS never outrank valid times | Ranking module | Property test |
| P05 | Same inputs + same ruleset version → byte-equivalent derived values | Scoring module | Determinism/reproducibility test |
| P06 | Revisions recalculate result and downstream standings with before/after snapshots | Correction module | Integration test with revision history assertions |
| P07 | Empty/pending attempt set never generates a final result | Scoring module | Unit test (guards against premature finalization) |

Units/penalty rules (raw ms, `NONE`/`PLUS_TWO`/`DNF`/`DNS` semantics, `PENDING` status) and
rounding rules (display half-up at configured precision, sort by exact rational/integer value,
CSV exports raw ms) are cross-cutting and verified by the same unit/property tests above plus a
dedicated CSV-export format test.

## E. `07-screen-and-component-spec.md` — every screen (22 screens) and shared component

| ID | Screen | Primary role(s) | Area | Evidence |
|---|---|---|---|---|
| P01 | Home / event discovery | Public | Public module | Discovery-disabled-by-default test; no private-event enumeration test |
| P02 | Join event | Guest | Registration module | `12` join-flow test; validation/duplicate-name tests |
| P03 | Competitor waiting room | Competitor | Competitor module | State-rendering test (waiting/check-in/ready/round-not-open/disconnected/withdrawn) |
| P04 | Attempt screen (phone mode) | Competitor | Attempt module | Scramble-reveal-timing test; focus-loss-preserves-timer test |
| P05 | Attempt status (judge-controlled mode) | Competitor | Attempt module | No-scramble-exposed test |
| P06 | Correction request | Competitor | Correction module | Category/note validation test; request-ID confirmation test |
| P07 | Personal result/history | Competitor | Result module | Own-entrant-only field test |
| S01 | Staff sign-in and recovery | Staff | Auth module | MFA, rate-limit, enumeration-resistance tests |
| S02 | Organization dashboard | Owner/Organizer | Org module | Empty-state and scope-switch tests |
| S03 | Event creation wizard | Organizer | Event module | Step validation tests; draft save test |
| S04 | Event overview | Organizer | Event module | Lifecycle-badge/action-availability test |
| S05 | Competitor roster | Organizer | Roster module | Add/edit/check-in/remove/withdraw tests |
| S06 | Round setup | Organizer | Round module | Advancement-count validation; ceil-percent preview test |
| S07 | Scramble preparation station | Scrambler/Judge | Scramble module | Role-gating test; applied/checked audit test |
| S08 | Tournament Control | Organizer | Control room module | Filter/action tests; live-update-no-reorder test |
| S09 | Judge entry | Judge | Attempt module | Save/undo-window/idempotency tests |
| S10 | Correction queue | Organizer | Correction module | Decision-action and audit-timeline tests |
| S11 | Round review and advancement | Organizer | Advancement module | Preview/commit transactional test |
| S12 | Results and publication | Organizer | Results module | Publish/unpublish/export/revision tests |
| S13 | Public display | Spectator | Display module | Published-fields-only test; provisional-label test |
| S14 | Event history and export | Organizer | History module | Search/filter, CSV column, deletion-vs-archive tests |
| S15 | Staff and organization settings | Owner | Settings module | Reauthentication-on-role-change test |

Shared components (`StatusBadge`, `SavedState`, `TimeInput`, `PenaltyPicker`, `RoleBanner`,
`ScrambleVisibilityBanner`, `ConfirmDialog`, `EmptyState`/`LoadingState`/`ErrorState`/
`OfflineState`, `LiveTable`, `ReducedMotion`) are cross-cutting; each is covered by a component
test plus the "Design acceptance checklist" in `07` (all items captured individually in section F
via the matching `12` checklist items, since the two checklists overlap).

## F. `12-release-acceptance.md` — every checklist item (28 release-gate items + process items)

### Product acceptance (11 items)

| ID | Item | Area | Evidence |
|---|---|---|---|
| PA01 | Event creation wizard creates draft, validates timezone/defaults/rules/visibility | S03 | Checklist item — automated test + manual verification at M6 |
| PA02 | QR/URL/short code join works on current mobile browsers; rotation invalidates old code; throttling prevents enumeration | P02 | Automated test + device check at M6 |
| PA03 | Guests join without account, see rules/mode, event-scoped access only | P02, P03 | Automated test |
| PA04 | Add/edit/remove before start, withdraw after start, check-in, lock registration without losing audit history | S05 | Automated test |
| PA05 | Physical timer mode: judge result, +2, DNF, DNS, save receipt, correction audit | S09 | Automated test |
| PA06 | Phone mode labels self-timed; current scramble only on unlock; future scramble inaccessible | P04 | Automated test |
| PA07 | Scramble roles, 3D guide, applied/checked, extra scramble, print usable; physical verification is human attestation only | S07 | Automated test + manual usability check at M4 |
| PA08 | All scoring vectors/property tests pass; standings provisional until complete; tie/advancement math visible and reproducible | Scoring/Advancement modules | `09` full suite (section D) |
| PA09 | Round close blocks unresolved attempts/corrections; advancement requires preview + explicit commit | S11 | Automated test |
| PA10 | Public display reveals only published fields, never future scrambles or private info | S13 | Automated test |
| PA11 | Organization history, event clone, CSV export, archive and deletion/retention work | S14 | Automated test |

### Security and privacy (8 items)

| ID | Item | Area | Evidence |
|---|---|---|---|
| SP01 | Cross-tenant tests cover list/detail/mutation/export/realtime/guessed IDs | All API | Automated security test suite |
| SP02 | Role matrix tested for every sensitive command; hidden UI cannot substitute for backend denial | All API | Automated role-matrix test |
| SP03 | Guest credentials random/hashed/revocable/expiring; join code cannot access joined entrant's account | Auth/registration module | Automated test |
| SP04 | CSRF/XSS/session/password/MFA/rate-limit checks complete; email verification/recovery tested | Auth module | Automated + manual security review at M6 |
| SP05 | Scramble payload absent from competitor/public DTOs, cache, logs, analytics, error tracker (staff-prepared mode) | Scramble module | Automated leakage test |
| SP06 | Scramble dependency license/provenance/version/algorithm review recorded | Scramble generation service | **Process evidence, not automated** — recorded in `DECISIONS.md` before M4 |
| SP07 | Data collection/retention/export/delete/public-name/child-use policy reviewed for pilot jurisdictions | Privacy/legal | **Process evidence** — legal review record before any pilot with real participants |
| SP08 | Privacy notice, terms, cookie notice, subprocessors, security contact published | Platform/legal | **Process evidence** — published documents, checked before public beta (NA until that phase) |

### Reliability/accessibility (9 items)

| ID | Item | Area | Evidence |
|---|---|---|---|
| RA01 | Migration forward/rollback strategy documented; schema update is migration-based | Database tooling | CI check (no auto-DDL) + documented runbook |
| RA02 | Automated encrypted backup verified; restore drill to isolated environment passed | Ops/platform | **Process evidence** — restore-drill record, required before M6 exit, NA until staging infra exists |
| RA03 | Venue network-loss test: saved vs queued clear; reconnect no duplication; conflicts need human resolution | Offline queue module | Automated reconnect/conflict test + manual network-loss drill |
| RA04 | Printed fallback score sheet usable; staff rehearsed it | Ops/print module | **Process evidence** — rehearsal record at M6 |
| RA05 | Monitoring covers uptime/API errors/queue/database/backups/realtime disconnects/suspicious scramble access | Observability | Monitoring-config review; alert-fires test |
| RA06 | Status page/support route and incident owner exist | Ops | **Process evidence** — NA until public beta is scheduled |
| RA07 | Keyboard-only, screen reader, 200% zoom, reduced-motion, contrast checks for join/judge-entry/control-room/display | All screens | a11y audit (axe/manual) at M3 and M6 |
| RA08 | 360 CSS px no horizontal scroll for core flows; touch-size baseline | All screens | Responsive layout test |
| RA09 | Load test at 2× expected pilot concurrency and event-day realtime fan-out | Platform | **Process evidence** — load-test report, required before M6 exit, NA until a pilot concurrency figure is set |

### Event-day checklist (organizer 5 + closeout 4 items) — process checklist, not code

| ID | Item | Status |
|---|---|---|
| ED01–ED05 | Confirm timezone/roster/mode/rules/ties; confirm staff roles; prepare scramble sheets/extras; download offline score sheets; confirm venue network/charging/display | **NA for automated test** — these are event-day operator actions, verified by the operator guide (`10-role-guides.md`) and rehearsed during pilots (M6), not by code |
| CL01–CL04 | Resolve pending states; publish only intended data; export/archive and tell participants where to see results; record issues and review within 48h | **NA for automated test** — operational runbook steps, verified during M6 pilot rehearsal |

### Deployment runbook (7 steps) and public launch sign-off — process items

| ID | Item | Status |
|---|---|---|
| DR01–DR07 | Provision infra; set env vars/validate no secrets in artifacts; backup before migration + smoke test; deploy compatible versions + monitor; rollback procedure; scramble-exposure incident procedure; event-outage procedure | **NA until M6 staging target is chosen** — these are deployment-process steps, not features; will be exercised and documented at M6, not before |
| LS01 | Product owner, technical owner, privacy/legal reviewer, security reviewer, pilot organizer sign-off before public production deployment | **NA at this stage** — out of scope for this engagement until explicitly authorized, per `00` §11 "A coding agent must not claim public launch merely because `npm build` or a local demo works" |

---

## Coverage statement

This document maps:
- all **13** V1-must-ship items and all **11** V1 exclusions in `00` §2,
- all mode/default/role/lifecycle/scramble/stack/completion rules in `00` §3–§12 (**49** items,
  R14–R62),
- all **19** scoring conformance vectors and property tests in `09`,
- all **22** screens in `07`,
- all **51** distinct API endpoints and the cross-cutting API conventions in `08` (endpoint-level
  detail folds into the module rows above; `08`'s own "API security tests" paragraph is the test
  evidence for the data/API contract as a whole and is referenced throughout section A/C),
- all **28** release-acceptance checklist items plus the event-day/deployment/sign-off process
  items in `12`.

Nothing in the five controlling/behavioral documents (`00`, `07`, `08`, `09`, `12`) was left
unmapped. Narrative-only documents (`01`, `02`, `03`, `04`, `05`, `06`, `10`, `11`) were used to
resolve ambiguity and inform the Area/Evidence columns above but do not introduce separate
requirement IDs, since `00` is controlling and the behavioral contracts (`07`–`09`) are where
narrative intent becomes testable.

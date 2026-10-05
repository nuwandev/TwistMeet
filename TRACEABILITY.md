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
| R04 | Roster: add/edit/check-in/remove-before-start/withdraw-after-start, no silent merge, lock/unlock | Roster module; S05 | **Built** — `RosterService` (add/update/checkIn/withdraw/remove), `DisplayNamePolicy.disambiguate()` for no-silent-merge. Tested in `RosterManagementTest` (rows 25–29). "Lock/unlock" is the existing M1 `registration/lock`/`reopen` transitions (row 19/20), not a separate roster-level lock |
| R05 | 3×3×3 single event; Single/Quick/Custom; formats Bo1/Bo2/Bo3/Mo3/Ao5; advancement everyone/topN/top%; tie handling | Round config module; S03, S06 | `09` conformance vectors (section D below); advancement boundary tests |
| R06 | Two modes: (A) physical timer+judge entry default, (B) phone/casual; no silent Remote mode | Attempt module; mode flag on Event/Round | Mode-labeling tests; `12` "Physical timer mode supports…", "Phone mode clearly labels self-timed…" |
| R07 | Organizer-configurable scramble policy (staff-prepared / self-scramble casual-only); extras, role-bound release, printable sheets, 3D guide, human attestations, audit trail | Scramble module; S07 | `12` "Scramble roles, 3D guide, applied/checked status…"; leakage security tests |
| R08 | Competitor status, judge/scrambler station, timer (when enabled), attempt status, help/correction request, personal result view | P03, P04, P05, P06, P07 | E2E competitor-journey test |
| R09 | Judge entry of time/+2/DNF/DNS/note; correction approvals; immutable revision history; no direct competitor edit | S09, S10; Attempt/Correction module | **Built** — `AttemptService.recordJudgeResult()` + `ResultRevision` (append-only, every entry including the first); `CorrectionService.decide()` for approvals. No endpoint lets a competitor write `Attempt`'s result fields. Tested in `AttemptFlowTest.judgeModeRecordsAResultAndEveryEntryCreatesARevision`, `CorrectionFlowTest` |
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
| R14 | Physical mode: judge observes/enters result; competitor sees prepared/covered cube; label "Judge recorded · physical timer" | Attempt module; P05 | **Built** — `ResultSource.JUDGE` recorded per attempt; `rounds/[roundId]/judge` UI. Scramble generation/covering is M4 scope (not built; no scramble exists yet to leak), so the "covered cube" half of this row has nothing to expose either way. Label rendering tested via E2E (`m2_e2e.mjs`) |
| R15 | Phone mode: competitor's device records self-reported time; competitor sees sequence + 3D guide before solve; label "Self-timed · device/browser timing" | Attempt module; P04 | **Built (timer half only)** — `ResultSource.SELF_TIMED`, competitor start/stop/submit UI on `e/[eventId]`. The scramble-sequence+3D-guide half is M4 scope (self-scramble vault not built); the attempt/timer flow itself is built and tested via E2E (`m2_e2e_phone.mjs`) |
| R16 | Organizer cannot describe phone results as independently verified | UI copy; S03 rules preview | Copy-review checklist (M3/M4) — unchanged, no M2 UI claims verification |
| R17 | No silent mixing of result sources within a round | Round/Attempt module | **Built** — `RoundService.prepare()` assigns one `ResultSource` to every attempt in a round, derived from `Event.timerMode`; there is no path that lets a single round contain both sources |
| R18 | Exceptional replacement keeps original source visible per attempt | Result detail UI; S12 | **Built** — a correction-granted replacement attempt (`CorrectionService.decide(ACCEPT_RETRY)`) is a new `Attempt` row with its own `resultSource`; the voided original's source and values remain readable via `GET /attempts/{attemptId}/revisions` (row 68). Tested in `CorrectionFlowTest.acceptRetryVoidsAndCreatesAReplacementAttempt` |
| R19–R24 | Role definitions: Owner, Organizer, Judge, Scrambler, Competitor, Spectator (`00` §5) | Role/permission module | Owner/Organizer: **Built** since M1 (`OrgRole`). Judge: **Built** at M2 (`EventRole.JUDGE` via `EventStaffAssignment`, event-scoped, independent of org membership — see DECISIONS.md). Scrambler: **Built** at M4 (`EventRole.SCRAMBLER`, `TenantAccessService.requireScrambleStaff()`). Competitor/Spectator: **Built** since M1 (guest credential; public read paths — M5 adds the actual unauthenticated public-display routes a Spectator uses, rows 59-60). Role-matrix tests: `RoundLifecycleTest.onlyOrganizerCanConfigureOrTransitionRounds`, `.judgeAssignedToTheEventCannotConfigureRoundsButCanEnterResults`, `CorrectionFlowTest.onlyOrganizerCanDecideACorrectionNotJudgeOrStranger`, `StandingsIntegrationTest.standingsAreAuthorizedStaffOnlyNotCompetitor`, `ScrambleFlowTest` (Scrambler role matrix), `PublishAndPublicFlowTest.onlyOrganizerCanPublishOrUnpublish` |
| R25 | Server-side authorization on every request | All API controllers | `12` "Role matrix tested…hidden UI cannot substitute for backend denial" |
| R26 | Display current role/scope visibly on staff pages | `RoleBanner` component; all staff screens | **Built** — `RoleBanner` (`web/src/components/common.tsx`), backed by `GET /events/{eventId}/my-role`. Shown on event detail, Tournament Control, judge entry, and the correction queue. Tested in `MyRoleApiTest`; rendered on-page assertion in `m3_e2e_organizer.mjs` ("RoleBanner shows Owner on event detail page") |
| R26b | *(M3 minimal addition)* `GET /events/{eventId}/my-role` | Event module | Role-resolution test (own role vs. stranger 404 vs. organizer-only-data-still-404-for-judge) | **Built** — `TenantAccessService.resolveStaffRole()`. Tested in `MyRoleApiTest` (all three cases) |
| R27 | Staff roles org-scoped; event assignment can narrow further | Role/permission module | Scoped-access test (org role vs. event-assignment role) |
| R28 | Event creator is owner/organizer for that event | Event creation flow | Creation test asserts creator role grant |
| R29 | Explicit confirmation required for round close, advancement, publish, void, archive | `ConfirmDialog` component; S04, S08, S11, S12 | **Built** — `ConfirmDialog` (native `<dialog>`, named confirm button, consequence summary) gates: opening registration, withdrawing/removing an entrant, pausing/resuming a live round, closing a round, every correction decision (M1-M3), committing advancement (M5, `rounds/[roundId]/advancement` page), and now (this audit pass) archiving an event (`events/[eventId]`'s "Archive event" confirm, citing 00 §6). Publish/unpublish are one-click without a confirm dialog — a deliberate M5 simplification recorded in DECISIONS.md (publish is reversible by unpublishing; unpublish is itself the "undo" action and rotates the link, so a second confirm step adds friction without protecting anything a single click can't already fix). Tested in `m3_e2e_organizer.mjs` (open-registration, pause/resume, close-round confirms), `m3_e2e_competitor.mjs` (decision confirm), `m5_e2e.mjs` (advancement preview page, publish/unpublish flow), `EventArchiveCloneTest` (archive/complete/reopen authorization and state gating) |
| R30 | Event lifecycle `DRAFT→REGISTRATION_OPEN→REGISTRATION_LOCKED→READY→LIVE→COMPLETED→ARCHIVED`; organizer-only transitions | Event state machine | State-machine transition tests incl. unauthorized-actor rejection |
| R31 | Completed event reopenable for corrections with actor/reason, recompute standings | Event state machine; S11 reopen path | Reopen/recompute test with before/after snapshot |
| R32 | Archived read-only except export/delete | Event state machine | **Built** — `EventService.archive()` (organizer-only, from `DRAFT`/`REGISTRATION_OPEN`/`REGISTRATION_LOCKED`/`COMPLETED`) sets `ARCHIVED`; a `requireNotArchived` guard was added to every mutating event/round method (`EventService.update/publish/transition`, `RoundService.create/update/prepare/transition/togglePause/review/close`) so an archived event genuinely rejects writes while reads, CSV export, and audit history remain available. Tested in `EventArchiveCloneTest.archivedEventIsReadOnlyExceptAllowedActions` |
| R33 | Round lifecycle `DRAFT→PREPARING→READY→LIVE→REVIEW→CLOSED` | Round state machine | **Built** — `Round`/`RoundState`/`RoundService`. Tested in `RoundLifecycleTest.roundMovesThroughEveryStateInOrderAndRejectsSkippingAState` |
| R34 | REVIEW begins after all slots resolved; unresolved corrections block close | Round state machine | **Built** — `RoundService.review()`/`.close()`. Tested in `RoundLifecycleTest.roundMovesThroughEveryStateInOrderAndRejectsSkippingAState`, `CorrectionFlowTest` (close blocked by pending correction) |
| R35 | Organizer can close with outstanding entrants only via recorded DNS/withdrawal | Round close flow; S11 | **Built** — DNF/DNS are recordable results (`AttemptOutcome`), and withdrawal (row 28) removes an entrant from future consideration without erasing history; close still requires every non-voided attempt resolved. Covered by `AttemptFlowTest.dnsAndDnfAreRejectedWithARawTimeAndOkRequiresOneWithinBounds` and `RosterManagementTest` |
| R36 | Advancement `PREVIEW→COMMITTED`, auditable, creates next-round entrants | Advancement module; S11 | **Built** at M5, tie-break attempt added in this audit pass — `AdvancementService.preview()`/`.commit()` (rows 55-56). Preview is pure/non-mutating and reuses `AdvancementCalculator`/`RankingService`/`ScoringEngine` directly (09 V11/V12 math, unchanged). Commit requires REVIEW or CLOSED, an `expectedVersion` match, and a DRAFT next round; it writes `round_qualified_entrants` (the next round's roster restriction) and `Round.advancementCommittedAt/By/Count`, then an `AuditEvent` ("ADVANCEMENT_COMMITTED"). A repeated commit call with the already-committed round replays the same result without touching the roster again (idempotent); a stale `expectedVersion` is rejected before any write; two concurrent commits are serialized by the round's own optimistic-lock `@Version` (the loser's flush fails immediately, before it ever reaches the roster-insert loop — see AdvancementService's commit() comment). The optional organizer-selected tie-break *attempt* (09: "If organizer selected a tie-break attempt...") is now **built**: `TiePolicy.TIE_BREAK_ATTEMPT` must be selected before registration opens (`RoundService.validateTiePolicy`); a boundary tie withholds the tied entrants from `advancing` (`tieBreakRequired`/`tiedPendingResolution` on the preview) until `POST /rounds/{roundId}/advancement/tie-break` creates one extra judged attempt per tied entrant (numbered `format.attemptCount()+1`, kept out of the main ranking); the winner is the lower valid time, DNF last, shared advancement if still tied — exactly as specified; commit refuses while a tie-break is pending. The default (`SHARED_RANK`) is unchanged: boundary ties always advance together. Tested in `AdvancementFlowTest` (9 tests: boundary ties for TOP_N/TOP_PERCENT, EVERYONE, commit+idempotent-replay, stale-version conflict, concurrent-commit race, REVIEW/CLOSED gating, role authorization, withdrawn-entrant exclusion), `TieBreakAttemptTest` (policy-must-precede-registration-open; full boundary-tie → tie-break-attempt → resolved → commit flow) |
| R37 | Attempt lifecycle `PENDING→ASSIGNED→PREPARING→READY→INSPECTION→RUNNING→STOPPED→SUBMITTED→ACCEPTED`, alt `DNS/DNF/VOIDED` | Attempt state machine | **Built, simplified** — `Attempt`/`AttemptState` {PENDING, RUNNING, STOPPED, SUBMITTED, ACCEPTED, VOIDED}. The ASSIGNED/PREPARING/READY/INSPECTION sub-states are collapsed into PENDING for both modes (round-level PREPARING already tracks slot allocation; per-attempt inspection-countdown UI is a client-side concern, not a separate server state the contract's own §9 test vectors require) — a simplification, not a behavior change: judge mode goes PENDING→ACCEPTED directly on `recordJudgeResult`, phone mode goes PENDING→RUNNING→STOPPED→ACCEPTED via `start`/`stop`/`submit`. Tested in `AttemptFlowTest` |
| R38 | Phone mode allows competitor-session RUNNING/STOPPED transitions; physical mode does not expose this control | Attempt module; P04 vs P05 | **Built** — `AttemptService.start()`/`.stop()` 403 outside phone/self-timed mode. Tested in `AttemptFlowTest.judgeModeRecordsAResultAndEveryEntryCreatesARevision` (negative case), `.selfTimedModeLetsCompetitorStartStopSubmitAndIsIdempotent` (positive case) |
| R39 | Every command idempotent | All write endpoints | **Built** for the M2 endpoints most at risk of a retry-caused double-effect: self-timed submission (row 42) and round-prepare (row 32, re-running on an already-prepared entrant is a no-op). Judge-result and correction-decision use `expectedVersion` optimistic concurrency instead of pure idempotency (a repeated identical call with the current version is harmless; a stale version is rejected, never silently reapplied). Tested in `AttemptFlowTest.selfTimedModeLetsCompetitorStartStopSubmitAndIsIdempotent`, `RoundLifecycleTest.roundMovesThroughEveryStateInOrderAndRejectsSkippingAState` (`prepare` re-run) |
| R40 | Void retains original values, requires reason + organizer permission; no hard-delete after assignment | Attempt/Correction module | **Built** — `CorrectionService.decide()`/`AttemptService.voidAttempt()`: void never deletes the row, and the pre-void raw/penalty/status is preserved in `ResultRevision` history. Tested in `CorrectionFlowTest.acceptNoRetryVoidsTheAttemptButPreservesTheOriginalValueInHistory` |
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
| R52 | Usable at 360 CSS px; keyboard; screen readers; 200% zoom; reduced motion | All screens | **Built, for every M1–M3 screen** — all pages use fluid/`max-width` layouts with no fixed-width overflow; `globals.css` adds a `prefers-reduced-motion` rule disabling animations/transitions. Keyboard: judge entry supports Enter-to-save (only when valid) and Escape-to-discard per row (`07` S09); every dialog, form, and button is native HTML, keyboard-operable by default; `ConfirmDialog` uses a native `<dialog>` (focus-trapped, Escape-dismissible). 200%-zoom and full screen-reader audits are **not built/verified** — no automated or manual a11y audit tool was run this milestone; only no-horizontal-scroll-at-360px is actually asserted, via `assertNoHorizontalScroll()` in both `m3_e2e_organizer.mjs` and `m3_e2e_competitor.mjs` (every M3 screen checked, run at both 360px and desktop width) |
| R53 | Color+icon+text status, focus visibility, labels, non-blocking help, loading/error/empty/saved/unsynced states on every data-entry screen | `StatusBadge`, `SavedState`, `EmptyState`, `LoadingState`, `ErrorState`, `OfflineState` components | **Built** — all six components exist in `web/src/components/common.tsx` and are used across the dashboard, event detail, Tournament Control, judge entry, correction queue, standings, and competitor waiting-room pages. `StatusBadge` always pairs an icon glyph with text (never color alone); global CSS adds a visible `:focus-visible` outline on every interactive element. `OfflineState`/connection-loss tested in `m3_e2e_competitor.mjs` (toggling Playwright's offline emulation); the rest exercised implicitly by every other E2E assertion that depends on a loaded/empty/errored page rendering the expected text |
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

All 19 items below are **Built and tested** — `ScoringEngine`/`RankingService`/`AdvancementCalculator`
(package `com.twistmeet.api.scoring`, zero Spring/JPA dependencies, exact rational arithmetic via
`ExactValue` to avoid float rounding) with every vector/property verified in
`ScoringEngineConformanceTest` (18 tests, V01–V12 + P01–P05, P07) plus P06 verified at the
integration level in `StandingsIntegrationTest.revisingAJudgeResultRecalculatesStandings` (a judge
correction recalculates the round's standings; before/after snapshots asserted directly).

| ID | Vector / rule | Area | Evidence |
|---|---|---|---|
| V01 | Ao5: 14.21,12.84,18.91,13.05,12.10 → 13.37 (discard 12.10, 18.91) | Scoring module | **Built** — `ScoringEngineConformanceTest.v01_ao5BasicDiscardFastestAndSlowest` |
| V02 | Ao5: 14.20,13.80,DNF,15.10,13.40 → 14.37 (discard 13.40 and DNF; corrected value per note) | Scoring module | **Built** — `ScoringEngineConformanceTest.v02_ao5CorrectedDnfCase`, using the corrected 14.37 value |
| V03 | Ao5: 10.00,11.00,12.00,DNF,DNS → DNF (fewer than four valid) | Scoring module | **Built** — `ScoringEngineConformanceTest.v03_ao5FewerThanFourValidIsDnf` |
| V04 | Ao5: 10.00,10.00,12.00,13.00,14.00 → 11.67 (discard one 10.00 by attempt-number tie-break, and 14.00) | Scoring module | **Built** — `ScoringEngineConformanceTest.v04_ao5TieDiscardByAttemptNumber`, incl. deterministic tie-break assertion |
| V05 | Mo3: 10.00,11.00,12.00 → 11.00 | Scoring module | **Built** — `ScoringEngineConformanceTest.v05_mo3ArithmeticMean` |
| V06 | Mo3: 10.00,11.00,DNS → DNF | Scoring module | **Built** — `ScoringEngineConformanceTest.v06_mo3AnyDnsMakesMeanDnf` |
| V07 | Bo3: 11.00,10.00,DNF → 10.00 (best valid single) | Scoring module | **Built** — `ScoringEngineConformanceTest.v07_bo3BestValidSingle` |
| V08 | Penalty: raw 12.345 +2 → stored raw=12345ms, penalty=2000ms, adjusted=14345ms | Scoring module | **Built** — `ScoringEngineConformanceTest.v08_plusTwoPenaltyAppliedBeforeComparison` |
| V09 | Rank: Ao5 12.00 vs 12.00, best singles 9.00 vs 9.50 → rank 1, rank 2 (average tie breaks on best single) | Ranking module | **Built** — `ScoringEngineConformanceTest.v09_rankAverageTieBreaksOnBestSingle` |
| V10 | Rank: identical average and best → shared rank 1, next rank 3 (competition ranking) | Ranking module | **Built** — `ScoringEngineConformanceTest.v10_rankIdenticalSharesRankThenSkips` |
| V11 | Top N: ranks 1,2,2,4, N=2 → first three advance (boundary tie included) | Advancement module | **Built** — `ScoringEngineConformanceTest.v11_topNIncludesBoundaryTies` (`AdvancementCalculator` built and unit-tested; not wired to any endpoint — advancement as an operational feature is explicitly out of this milestone's scope, see DECISIONS.md) |
| V12 | Percent: 11 eligible, 25% → target 3 (ceil(2.75)), include cutoff ties | Advancement module | **Built** — `ScoringEngineConformanceTest.v12_topPercentCeilsAndIncludesCutoffTies` |
| P01 | Attempt input order does not change score except deterministic tie selection | Scoring module | **Built** — `ScoringEngineConformanceTest.p01_attemptOrderDoesNotChangeScore` (randomized order permutations) |
| P02 | Adding an attempt outside retained Ao5 three cannot change average unless it changes which extreme is discarded | Scoring module | **Built** — `ScoringEngineConformanceTest.p02_changingADiscardedExtremeWithoutChangingWhichOneIsDiscardedDoesNotAffectAverage` |
| P03 | +2 affects comparisons/averages by exactly 2000ms | Scoring module | **Built** — `ScoringEngineConformanceTest.p03_plusTwoAffectsByExactly2000Ms` |
| P04 | DNF/DNS never outrank valid times | Ranking module | **Built** — `ScoringEngineConformanceTest.p04_dnfNeverOutranksValidTimes` |
| P05 | Same inputs + same ruleset version → byte-equivalent derived values | Scoring module | **Built** — `ScoringEngineConformanceTest.p05_sameInputsSameRulesetAreByteEquivalent` |
| P06 | Revisions recalculate result and downstream standings with before/after snapshots | Correction module | **Built** — `StandingsIntegrationTest.revisingAJudgeResultRecalculatesStandings` (integration-level, not a pure-engine unit test, since it exercises the full judge-result → standings-recompute path) |
| P07 | Empty/pending attempt set never generates a final result | Scoring module | **Built** — `ScoringEngineConformanceTest.p07_emptyAttemptSetNeverGeneratesAFinalResult` |

Units/penalty rules (raw ms, `NONE`/`PLUS_TWO`/`DNF`/`DNS` semantics, `PENDING` status) and
rounding rules (display half-up at configured precision, sort by exact rational/integer value,
CSV exports raw ms) are cross-cutting and verified by the same unit/property tests above plus a
dedicated CSV-export format test.

## E. `07-screen-and-component-spec.md` — every screen (22 screens) and shared component

| ID | Screen | Primary role(s) | Area | Evidence |
|---|---|---|---|---|
| P01 | Home / event discovery | Public | Public module | Discovery-disabled-by-default test; no private-event enumeration test |
| P02 | Join event | Guest | Registration module | `12` join-flow test; validation/duplicate-name tests |
| P03 | Competitor waiting room | Competitor | Competitor module | **Built** — `e/[eventId]` models withdrawn, not-checked-in (informational only, never gates the attempt UI — see DECISIONS.md), round-not-open (`attempts.length === 0`), and disconnected (via `useOnlineStatus`/`OfflineState`) explicitly. "Check-in needed" and "ready" are the same not-checked-in badge/informational-line pairing since there is no separate ready sub-state in this data model. Tested in `m3_e2e_competitor.mjs` (not-checked-in + round-not-open shown together; offline state shown and recovers) at both viewport widths |
| P04 | Attempt screen (phone mode) | Competitor | Attempt module | **Built (timer half)** — `e/[eventId]` self-timed start/stop/submit UI, now with client-side time-range validation (`m3_e2e_competitor.mjs`: "self-timed submission validates the time range client-side") and an `aria-live` announcer for start/stop/submit transitions. Scramble-reveal-timing test is M4 scope (no scramble exists yet). Tested via E2E (`m2_e2e_phone.mjs`, `m3_e2e_competitor.mjs`) |
| P05 | Attempt status (judge-controlled mode) | Competitor | Attempt module | **Built** — `e/[eventId]` shows judge-recorded result/status; no scramble is ever sent to this screen. "Request judge/help" (distinct from a correction request, available any time before a result exists) is now **built**: `HelpRequestService.create()` (guest-authenticated, idempotent — repeated taps return the existing PENDING request), `POST /attempts/{attemptId}/help-requests`, a "Request judge / help" button on `e/[eventId]` shown while the attempt is still PENDING, and a staff-facing "Help requests" queue on `events/[eventId]/corrections` with a resolve action. Tested in `HelpRequestAndNameMaskTest` (create/idempotent/resolve, ownership check, rejected once a result exists) |
| P06 | Correction request | Competitor | Correction module | **Built** — `e/[eventId]` correction-request form, now with the category selector (`07`: timer/entry issue, scramble concern, interruption, other — previously hardcoded to "other"), a 500-char-capped note, the "pauses close/advancement review" disclosure, and a request-ID + status confirmation shown in place of the form once submitted. Tested in `CorrectionFlowTest.competitorCanRequestACorrectionOnlyForTheirOwnAttempt`, E2E (`m2_e2e_phone.mjs`, `m3_e2e_competitor.mjs` — category selection and the request-ID confirmation text) |
| P07 | Personal result/history | Competitor | Result module | **Built** — `e/[eventId]` renders the competitor's own attempts/results (row 67's own-attempts endpoint). Own-entrant-only field test in `AttemptFlowTest.competitorCanListTheirOwnAttemptsAcrossTheEvent` |
| S01 | Staff sign-in and recovery | Staff | Auth module | MFA, rate-limit, enumeration-resistance tests |
| S02 | Organization dashboard | Owner/Organizer | Org module | Empty-state and scope-switch tests |
| S03 | Event creation wizard | Organizer | Event module | **Built, simplified to 3 steps** (basics, operations, review — see DECISIONS.md for why `07`'s 4-step/competition-step shape doesn't apply) — `EventWizard` component, used from the dashboard. Step validation (Continue disabled until the current step is valid) and the self-scramble/phone-mode cross-field rule are enforced client-side, mirroring the server's own check. No cross-reload draft persistence (documented simplification). Tested in `m3_e2e_organizer.mjs`/`m3_e2e_competitor.mjs` (both step transitions, Continue-disabled-until-valid, timer-mode selection) at both viewport widths |
| S04 | Event overview | Organizer | Event module | **Built (partial)** — lifecycle badge, mode/trust label, and lifecycle-appropriate actions (open/lock/reopen registration, rotate join code, round actions) on `events/[eventId]`; a Tournament Control link appears once a round is live. The full tabbed layout (Overview/Competitors/Rounds/Tournament Control/Results/Settings/History as separate tabs) is **not built** — this milestone keeps the single-page layout from M1/M2 with added sections, not a tab system; Tournament Control itself is its own route (row S08) rather than a tab. Tested via E2E (`m3_e2e_organizer.mjs`) |
| S05 | Competitor roster | Organizer | Roster module | **Built** — `events/[eventId]` roster section (add/edit/check-in/withdraw/remove). Tested in `RosterManagementTest` |
| S06 | Round setup | Organizer | Round module | **Built** — `events/[eventId]` round-creation form + round list. Advancement-count validation tested in `RoundLifecycleTest.advancementTopNRequiresAPositiveValueAndCannotExceedFrozenEntrants`; ceil-percent preview math covered at the engine level by V12, not yet surfaced in this screen's UI (no advancement-preview UI exists — advancement is explicitly out of scope, see DECISIONS.md) |
| S07 | Scramble preparation station | Scrambler/Judge | Scramble module | **Built** at M4 — `rounds/[roundId]/scramble`. See rows 44-50 (M4) |
| S08 | Tournament Control | Organizer | Control room module | **Built**, including real-time live updates (this audit pass) — `events/[eventId]/control`, composed from existing M2 reads (round, round attempts, pending corrections filtered to the live round — see DECISIONS.md "Tournament Control... is composed entirely from existing M2 reads"). Shows event/round/status, mode label, time since round start (new `Round.startedAt`), progress summary (N/M complete, corrections pending), a filterable entrant table (All/Needs attention/In progress/Finished), and pause/resume + "open judge entry" + "resolve requests" + "close round" actions, each confirmed where high-impact. "Connection state" (07 S08) is now shown via `useEventStream` (row 63): a Live/Connecting/Reconnecting badge next to the round status, and every SSE message triggers a REST refetch so the table reflects judge entries, corrections, and round transitions without the organizer taking any action. Tested end-to-end in `m3_e2e_organizer.mjs` at both viewport widths (progress counts, pause/resume confirm, filter buttons present, close-round confirm, no-horizontal-scroll at 360px); the SSE channel itself in `EventStreamTest` |
| S09 | Judge entry | Judge | Attempt module | **Built, extended at M3** — `rounds/[roundId]/judge` table UI (status/+2/DNF/DNS, save, adjusted-result preview). M3 adds: client-side validation disabling Save for an out-of-range time (not just server-side rejection), a `SavedState` receipt ("Saving…"/"Saved"), a real 10-second undo window that only appears after correcting an *already-recorded* result (re-submits the prior value as a new revision — see DECISIONS.md), `STALE_VERSION` handling that reloads the row with a message instead of silently failing, and keyboard support (Enter saves only when valid, Escape discards the unsaved draft). Tested via E2E (`m2_e2e.mjs`, `m3_e2e_organizer.mjs` — validation-disables-save, undo-only-after-a-correction, undo-restores-the-prior-value); backend in `AttemptFlowTest` |
| S10 | Correction queue | Organizer | Correction module | **Built, extended at M3** — `events/[eventId]/corrections` queue UI. M3 adds: a required-reason client-side check (mirroring the now-server-enforced `@NotBlank`), a `ConfirmDialog` previewing each decision's consequence before it commits, a "Recent decisions" section (decided corrections with reason/actor/time — a basic audit view, per `07`'s "Detail includes audit timeline"), and role-gating that hides the decide-controls entirely for a non-organizer viewer (reads `GET /events/{eventId}/my-role`) rather than showing buttons that would 403. Tested in `CorrectionFlowTest` (server), `m3_e2e_competitor.mjs` (reason-required, confirm-dialog consequence text, recent-decisions display) |
| S11 | Round review and advancement | Organizer | Advancement module | **Built** — review half from M1-M3 (`events/[eventId]` "Enter review"/"Close round", Tournament Control's combined "Close round" action); advancement preview/commit is now built too (M5): `rounds/[roundId]/advancement` shows eligible/target/advancing counts, the tie note, and a `ConfirmDialog`-gated "Commit advancement" button, linked from the event page for REVIEW/CLOSED rounds (see R36, row 55-56). "Roll back only through a logged admin operation" is **not built** — no rollback endpoint exists; a mis-committed advancement today requires direct data correction, recorded as a genuine gap in DECISIONS.md, not a silent omission. Tested in `AdvancementFlowTest`, `m5_e2e.mjs` (preview page renders, eligible count shown) |
| S12 | Results and publication | Organizer | Results module | **Built (publish/unpublish/export/name-masking)** — folded into `events/[eventId]`'s existing single-page layout rather than a dedicated tabbed S12 screen (same simplification pattern as S04): a "Publishing" section shows published/not-published state, the public link, Publish/Unpublish buttons, and (this audit pass) the "Public name masking option" checkbox (`POST /events/{eventId}/public-name-mask`, `PublicDisplayService` substitutes "Competitor N" for the real name when enabled); CSV export lives on the new history page (row S14) since it is organization-scoped, not just this one event. **Not built**: the dedicated live/unpublished/published/revisions *tabs* and in-place "amend eligible metadata" — genuine, documented UI-consolidation deferrals (DECISIONS.md), not missing capability: every action and view those tabs would hold (publish state, standings, revision history via S10) is already reachable from existing pages. Tested in `PublishAndPublicFlowTest`, `HelpRequestAndNameMaskTest.publicNameMaskHidesRealDisplayNames`, `m5_e2e.mjs` |
| S13 | Public display | Spectator | Display module | **Built (core + live updates)** — `public/[slug]`: event name/venue, a round selector, and a standings table (rank, competitor display name or "Competitor N" when name-masking is on, result, best, provisional badge, completed/total attempt counts for an in-progress round). Standings now refresh live via `usePublicEventStream` (row 63) subscribed to the unauthenticated public SSE channel, which only fires for a currently-published event. **Not built**: the podium-reveal animation and fullscreen/kiosk styling — documented deferrals, cosmetic only. Never includes scramble, private entries, guest credentials, or staff actions (DTO-shape-enforced, not just filtered — see row 59-60); the public SSE envelope carries no resource content either, only `{eventVersion, eventType, occurredAt}`. Tested in `PublishAndPublicFlowTest.publicDtoHasNoEmailTokenNotesOrScrambleFields`, `ScrambleSecurityGapTest.publicUnauthenticatedEndpointsNeverExposeScrambleNotation`, `EventStreamTest.publicStreamRequiresThePublishedEvent`, `m5_e2e.mjs` |
| S14 | Event history and export | Organizer | History module | **Built (search/filter + CSV + clone/archive)** — `organizations/[orgId]/history`: name/state filters, completed-event-card-equivalent summaries (name, state, date, entrant count, round count), and a per-event "Download CSV" button. "Copy event settings" (duplicate-as-template) is now **built**, via `EventService.clone()`/`POST /events/{eventId}/clone` and the "Clone event settings" button on `events/[eventId]` (this audit pass) — copies name/description/startsAt/timezone/venueLabel/visibility/timerMode/scramblePolicy into a fresh DRAFT event with a new join code, never entrants/rounds/attempts. Archive is likewise now **built** (see R29/R32). **Not built**: retention-policy deletion, pending the M6 data-retention/export-deletion work (DECISIONS.md). Date-range filtering is implemented API-side (`from`/`to` query params) but has no date-picker UI yet (query-string-only; a small, documented UI gap). Tested in `HistoryExportFlowTest`, `EventArchiveCloneTest.cloneCopiesConfigNotEntrantsOrRounds`, `m5_e2e.mjs` |
| S15 | Staff and organization settings | Owner | Settings module | Reauthentication-on-role-change test |

Shared components (`StatusBadge`, `SavedState`, `TimeInput`, `PenaltyPicker`, `RoleBanner`,
`ScrambleVisibilityBanner`, `ConfirmDialog`, `EmptyState`/`LoadingState`/`ErrorState`/
`OfflineState`, `LiveTable`, `ReducedMotion`) are cross-cutting; each is covered by a component
test plus the "Design acceptance checklist" in `07` (all items captured individually in section F
via the matching `12` checklist items, since the two checklists overlap).

**M2 minimal UI addition, not one of the 22 enumerated screens:** `rounds/[roundId]/standings`, a
simple authorized (staff-only) standings table showing provisional/final state, rank, result, and
best single. `07` describes public/published standings (S13) and organizer results/publication
(S12), both out of this milestone's scope, but gives staff no way at all to see the
`GET /rounds/{roundId}/standings` data (row 54) this milestone built. Built and tested via E2E
(`m2_e2e.mjs`, which asserts "Provisional" and the correct result appear after a judge entry).

## F. `12-release-acceptance.md` — every checklist item (28 release-gate items + process items)

### Product acceptance (11 items)

| ID | Item | Area | Evidence |
|---|---|---|---|
| PA01 | Event creation wizard creates draft, validates timezone/defaults/rules/visibility | S03 | **Built** — `EventWizard`. Timezone/visibility fields validated client-side and accepted server-side; no separate "rules" document exists to validate against (see DECISIONS.md). Tested in `m3_e2e_organizer.mjs`/`m3_e2e_competitor.mjs` |
| PA02 | QR/URL/short code join works on current mobile browsers; rotation invalidates old code; throttling prevents enumeration | P02 | Automated test + device check at M6 |
| PA03 | Guests join without account, see rules/mode, event-scoped access only | P02, P03 | Automated test |
| PA04 | Add/edit/remove before start, withdraw after start, check-in, lock registration without losing audit history | S05 | Automated test |
| PA05 | Physical timer mode: judge result, +2, DNF, DNS, save receipt, correction audit | S09 | **Built** — see S09 row above for the M3 additions (save receipt, undo, validation, stale-version handling). Tested in `AttemptFlowTest`, `m2_e2e.mjs`, `m3_e2e_organizer.mjs` |
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

## G. `08-data-api-contract.md` — full API route map (route-count correction)

**Route-count correction:** the earlier version of this document and the chat-only M0 summary it
was based on stated "51 distinct API endpoints." Recounting directly against
`08-data-api-contract.md` by expanding every comma-separated route group into its own route
(e.g. "`POST /events/{eventId}/registration/open`, `/lock`, `/reopen`" is 3 routes, not 1) gives
**62 distinct REST-style routes**, plus **1 real-time subscription channel** described in the
"Real-time updates" section (not a REST route, but a distinct API surface `08` specifies) — **63
API surfaces in total**. The table below gives every one of the 62 REST routes its own row, with
the real-time channel as a 63rd row at the end. This replaces the earlier undercount.

| # | Method & route | Owning module | Authorization / role | Relevant data | Acceptance test / release evidence | M1 status |
|---|---|---|---|---|---|---|
| 1 | `POST /auth/register` | Auth | Public; no password accepted here, no account created here (double opt-in — see DECISIONS.md "M1 follow-up: double opt-in registration"); rate-limited 8/15min per client, plus a silent per-email send cap that never changes the response | `PendingRegistration` (not `User` — a `User` row is created only by `POST /auth/email/verify`) | Identical status and body whether the email is new or already verified (`AuthFlowTest.registrationDoesNotRevealWhetherTheEmailIsAlreadyVerified`); a second registration for the same unverified email reissues (invalidates) the first token (`AuthFlowTest.aSecondRegistrationForAnUnverifiedEmailInvalidatesTheFirstLinkRatherThanStealingIt`); rate-limit test (`AuthFlowTest.registrationIsRateLimited`). The prior "register then immediately log in with the same password" residual signal no longer applies — there is no account, and no password, until verification. | **Built** — `AuthFlowTest` |
| 2 | `POST /auth/login` | Auth | Public (credentials) | `User` | Generic-error/rate-limit test (SP04) | **Built** — `AuthFlowTest` |
| 3 | `POST /auth/logout` | Auth | Authenticated staff | `User` session | Session-revocation test | **Built** — `AuthFlowTest` |
| 4 | `POST /auth/password/forgot` | Auth | Public (email) | `User` | Enumeration-resistant generic-response test | Not built (M1 scope is register/login/logout only; password reset deferred) |
| 5 | `POST /auth/password/reset` | Auth | Possession of reset token | `User` | Reset-flow test | Not built (see row 4) |
| 6 | `POST /auth/mfa/totp/enroll` | Auth | Authenticated staff | `User` | MFA enrollment test | Not built — MFA is "required before public beta" (`00` §10), tracked for M6 |
| 7 | `POST /auth/mfa/totp/verify` | Auth | Authenticated staff | `User` | MFA verification test | Not built — see row 6 |
| 8 | `GET /me` | Auth/Org | Authenticated staff (self) | `User` | Self-fetch test | **Built** — `AuthFlowTest`, `CrossTenantAccessTest` |
| 9 | `GET /organizations` | Org | Authenticated staff, scoped to membership | `Organization`, `OrganizationMembership` | List-scoped-to-membership test (R58 tenant isolation) | **Built** — `CrossTenantAccessTest.listingOrganizationsOnlyReturnsOwnMemberships` |
| 10 | `POST /organizations` | Org | Authenticated staff | `Organization` | Creation test; creator becomes Owner (R28 analogue at org level) | **Built** |
| 11 | `GET /organizations/{id}/members` | Org | Org member, role-gated detail | `OrganizationMembership` | Role-matrix test (SP02) | Not built (M2+; M1 has no staff-invite UI yet) |
| 12 | `POST /organizations/{id}/members/invitations` | Org | Owner/Organizer | `OrganizationMembership`, `RoleGrant` | Invite-flow test; least-privilege-by-default test | Not built (see row 11) |
| 13 | `PATCH /organizations/{id}/members/{userId}` | Org | Owner | `RoleGrant` | Reauthentication-on-role-change test (S15) | Not built (see row 11) |
| 14 | `POST /organizations/{orgId}/events` | Event | Organizer/Owner, org-scoped | `Event` | Creation test; cross-tenant isolation test (R58) | **Built** — `CrossTenantAccessTest`, `EventLifecycleAndJoinTest` |
| 15 | `GET /organizations/{orgId}/events` | Event | Staff, org-scoped | `Event` | List-scoped-to-org test | **Built** — `CrossTenantAccessTest.memberOfOrgBCannotReadOrModifyOrgAEvent` |
| 16 | `GET /events/{eventId}` | Event | Role-filtered (staff scope, guest, or public per visibility) | `Event` | Role-filtered-detail test; never-contains-scramble-secrets test (R46) | **Built for staff only** (guest/public detail view not built in M1 — guests get event name via join response instead; simplification, see DECISIONS.md) |
| 17 | `PATCH /events/{eventId}` | Event | Organizer | `Event` | Draft-only-mutable test; immutable-after-registration-without-versioned-flow test | **Built** (simplified to DRAFT-only editing, see DECISIONS.md "Edit lock simplification") |
| 18 | `POST /events/{eventId}/registration/open` | Event | Organizer | `Event` (state) | Lifecycle transition test (R30) | **Built** — `EventLifecycleAndJoinTest` |
| 19 | `POST /events/{eventId}/registration/lock` | Event | Organizer | `Event` (state) | Lifecycle transition test | **Built** — `EventLifecycleAndJoinTest.lockedRegistrationRejectsNewJoinsButExistingGuestsStillWork` |
| 20 | `POST /events/{eventId}/registration/reopen` | Event | Organizer | `Event` (state), `AuditEvent` | Audited-reopen test (R31) | **Built** (endpoint implemented; no dedicated test yet — gap, see report) |
| 21 | `POST /events/{eventId}/join-codes/rotate` | Event | Organizer | `Event` (join code hash) | Rotation-invalidates-old-code test (PA02) | **Built** — `EventLifecycleAndJoinTest.joinCodeRotationInvalidatesOldCode` |
| 22 | `POST /join/{joinCode}` | Registration | Public, valid join code | `EventEntrant`, guest credential | Join-code rate-limit/validity test (PA02, SP03); never returns roster test | **Built** — `EventLifecycleAndJoinTest` (rate limiting implemented, not covered by an automated test — gap) |
| 23 | `GET /guest/events/{eventId}/me` | Registration | Guest (own credential) | `EventEntrant` | Own-entrant-only field test | **Built** — `EventLifecycleAndJoinTest.guestCredentialDoesNotCrossEventBoundary` |
| 24 | `PATCH /guest/events/{eventId}/me` | Registration | Guest (own credential), display name only pre-start | `EventEntrant` | Self-edit-scope test | **Built** — `JoinController.updateMe()`; blocked once any attempt leaves `PENDING`. Tested in `RosterManagementTest.guestCanRenameThemselvesBeforeAttemptsStartButNotAfter` |
| 25 | `POST /events/{eventId}/entrants` | Roster | Organizer (staff add) | `EventEntrant` | Manual-add test (S05) | **Built** — `RosterService.add()`. Tested in `RosterManagementTest.organizerCanAddEditCheckInAndRemoveAnEntrantBeforeAnyAttemptExists`, `.manualAddDisambiguatesDuplicateDisplayNamesLikeGuestJoinDoes` |
| 26 | `PATCH /events/{eventId}/entrants/{entrantId}` | Roster | Organizer | `EventEntrant` | Edit test; duplicate-name disambiguation test | **Built** — `RosterService.update()`, reuses `DisplayNamePolicy.disambiguate()`. Tested in `RosterManagementTest.manualAddDisambiguatesDuplicateDisplayNamesLikeGuestJoinDoes` |
| 27 | `POST /events/{eventId}/entrants/{entrantId}/check-in` | Roster | Organizer/Judge | `EventEntrant` (checkInState) | Check-in state test | **Built** — `RosterService.checkIn()`, via `TenantAccessService.requireJudgeOrOrganizer()`. Tested in `RosterManagementTest.organizerCanAddEditCheckInAndRemoveAnEntrantBeforeAnyAttemptExists` |
| 28 | `POST /events/{eventId}/entrants/{entrantId}/withdraw` | Roster | Organizer | `EventEntrant` (status) | Withdraw-preserves-audit test (R04/PA04) | **Built** — `RosterService.withdraw()`, idempotent, preserves existing attempts. Tested in `RosterManagementTest.removingAnEntrantWithAttemptsIsBlockedAndWithdrawIsUsedInstead` |
| 29 | `DELETE /events/{eventId}/entrants/{entrantId}` | Roster | Organizer, only before attempt assignment | `EventEntrant`, `AuditEvent` | Delete-blocked-after-assignment test; tombstone/audit test | **Built** — `RosterService.remove()`: hard-deletes only when `!attemptRepository.existsByEntrantId`, else `409` directing to withdraw (see DECISIONS.md "Roster remove vs. withdraw"). Tested in `RosterManagementTest.organizerCanAddEditCheckInAndRemoveAnEntrantBeforeAnyAttemptExists` (removal succeeds pre-attempt) and `.removingAnEntrantWithAttemptsIsBlockedAndWithdrawIsUsedInstead` (409 once attempts exist) |
| 30 | `POST /events/{eventId}/rounds` | Round | Organizer, draft/registration-open/registration-locked event (see DECISIONS.md "Round creation…" for the reading of "draft event only") | `Round` | Draft-only-creation test | **Built** — `RoundService.create()`. Tested in `RoundLifecycleTest.roundMovesThroughEveryStateInOrderAndRejectsSkippingAState`, `.onlyOrganizerCanConfigureOrTransitionRounds` |
| 31 | `PATCH /rounds/{roundId}` | Round | Organizer, before live | `Round` | Pre-live-edit test; blocked-after-live test | **Built** — `RoundService.update()`/`applyDraftEdits()`. Tested in `RoundLifecycleTest.roundConfigIsEditableOnlyBeforeLive` |
| 32 | `POST /rounds/{roundId}/prepare` | Round | Organizer | `Round`, `Attempt` slots | Freeze-entrants/ruleset test (R33, R41) | **Built** — `RoundService.prepare()`: freezes `ACTIVE` entrants, allocates `format.attemptCount()` attempts each, skips already-prepared entrants (idempotent). Tested in `RoundLifecycleTest.roundMovesThroughEveryStateInOrderAndRejectsSkippingAState`. (`ScrambleAssignment` is out of scope — scramble generation/vault is M4) |
| 33 | `POST /rounds/{roundId}/ready` | Round | Organizer | `Round` (state) | Transition test | **Built** — `RoundService.ready()`. Tested in `RoundLifecycleTest.roundMovesThroughEveryStateInOrderAndRejectsSkippingAState` |
| 34 | `POST /rounds/{roundId}/start` | Round | Organizer | `Round` (state) | Transition test | **Built** — `RoundService.start()`. Tested in `RoundLifecycleTest.roundMovesThroughEveryStateInOrderAndRejectsSkippingAState` |
| 35 | `POST /rounds/{roundId}/pause` | Round | Organizer | `Round` (state) | Transition test | **Built** — `RoundService.togglePause()`. Tested in `AttemptFlowTest.roundPausePreventsNewSelfTimedStartsButDoesNotAffectAlreadyRunningState` |
| 36 | `POST /rounds/{roundId}/review` | Round | Organizer (or system-triggered on full resolution) | `Round` (state) | REVIEW-after-all-resolved test (R34) | **Built** — `RoundService.review()`: blocks if any non-voided attempt is still `PENDING`. Tested in `RoundLifecycleTest.roundMovesThroughEveryStateInOrderAndRejectsSkippingAState` |
| 37 | `POST /rounds/{roundId}/close` | Round | Organizer | `Round` (state) | Close-blocked-by-pending test (PA09) | **Built** — `RoundService.close()`: blocks if any `PENDING` correction exists for the round's attempts. Tested in `RoundLifecycleTest.roundMovesThroughEveryStateInOrderAndRejectsSkippingAState`; close-blocked-by-pending-correction case in `CorrectionFlowTest` |
| 38 | `GET /rounds/{roundId}/control-state` | Control room | Staff, role-filtered | `Round`, `Attempt`, `EventEntrant` snapshot | Role-filtered-snapshot test (S08) | Not built as a dedicated aggregate route — Tournament Control composes the same data from the existing `GET /rounds/{roundId}/attempts` staff view and `GET /events/{eventId}/corrections` (row 66 and row 52) rather than a new combined endpoint (DECISIONS.md), and now (this audit pass) refreshes that composition live via the row-63 SSE channel rather than only on page load/action |
| 39 | `GET /attempts/{attemptId}` | Attempt | Role-filtered (judge, organizer, or own entrant) | `Attempt` | Authorized-fields-only test | **Built** — `AttemptService.getAuthorized()`: staff first, falls back to guest-entrant ownership. Tested in `AttemptFlowTest.judgeModeRecordsAResultAndEveryEntryCreatesARevision` |
| 40 | `POST /attempts/{attemptId}/start` | Attempt | Competitor, phone mode only, own credential, current attempt | `Attempt` (state) | Mode-gated-control test (R38) | **Built** — `AttemptService.start()`: 403 in judge mode (no such control exists for a competitor in that mode). Tested in `AttemptFlowTest.judgeModeRecordsAResultAndEveryEntryCreatesARevision` (negative case), `.selfTimedModeLetsCompetitorStartStopSubmitAndIsIdempotent` (positive case) |
| 41 | `POST /attempts/{attemptId}/stop` | Attempt | Competitor, phone mode only, own credential | `Attempt` (state) | Mode-gated-control test | **Built** — `AttemptService.stop()`. Tested in `AttemptFlowTest.selfTimedModeLetsCompetitorStartStopSubmitAndIsIdempotent` |
| 42 | `POST /attempts/{attemptId}/submit` | Attempt | Competitor, phone mode | `Attempt` (rawTimeMs, penalty, resultSource=self-timed) | Idempotency test (DUPLICATE_ATTEMPT); penalty-validation test | **Built** — `AttemptService.submitSelfTimed()`: identical resubmit is a no-op success; a different payload on an already-`ACCEPTED` attempt is `409 DUPLICATE_ATTEMPT`; raw time validated to 1–9,999,999ms. Tested in `AttemptFlowTest.selfTimedModeLetsCompetitorStartStopSubmitAndIsIdempotent`, `.dnsAndDnfAreRejectedWithARawTimeAndOkRequiresOneWithinBounds` |
| 43 | `PUT /attempts/{attemptId}/judge-result` | Attempt | Judge | `Attempt`, `ResultRevision` | Judge-only test; revision-recorded test (R09) | **Built** — `AttemptService.recordJudgeResult()`: every call (including the first) inserts a `ResultRevision`; `expectedVersion` mismatch returns `409 STALE_VERSION`. Tested in `AttemptFlowTest.judgeModeRecordsAResultAndEveryEntryCreatesARevision`, `RoundLifecycleTest.judgeAssignedToTheEventCannotConfigureRoundsButCanEnterResults` |
| 44 | `POST /rounds/{roundId}/scramble-batches` | Scramble | Organizer | `ScrambleBatch`, `ScrambleSecret` | Batch-generation test; counts/IDs-only-response test (R46) | **Built** — `ScrambleService.createBatch()`: allocates one primary secret per attempt plus `max(2, ceil(attemptCount*0.10))` extras (00 §7), rejects a round with no attempts (400) and a second batch for the same round (409). Response never contains notation. Tested in `ScrambleFlowTest.batchGenerationAllocatesOneAssignmentPerAttemptPlusExtras`, `.batchGenerationRequiresAPreparedRound`, `.onlyOrganizerCanCreateABatchCompetitorAndStrangerCannot` |
| 45 | `GET /scramble-assignments/{id}/official-view` | Scramble | Assigned scrambler/judge only | `ScrambleAssignment` | Role-gated-view test (R41) | **Built** — `ScrambleService.officialView()`: Organizer/Judge/Scrambler only (`TenantAccessService.requireScrambleStaff`), requires a prior reveal (403 `SCRAMBLE_NOT_AVAILABLE` otherwise), audits every view. Tested in `ScrambleFlowTest.revealIsRoleScopedAndIdempotentAndCompetitorCannotAccessIt`, `.officialViewBeforeRevealIsRejected` |
| 46 | `POST /scramble-assignments/{id}/reveal` | Scramble | Assigned official | `ScrambleAssignment` (revealedAt), `AuditEvent` | Audit-on-reveal test; idempotent-no-bulk-reveal test | **Built** — `ScrambleService.reveal()`: idempotent (repeat calls return the same payload, no duplicate audit event), one assignment per call only (no bulk-reveal endpoint exists). Tested in `ScrambleFlowTest.revealIsRoleScopedAndIdempotentAndCompetitorCannotAccessIt` |
| 47 | `POST /scramble-assignments/{id}/mark-applied` | Scramble | Assigned scrambler | `ScrambleAssignment` (actor/time) | Applied-audit test (S07) | **Built** — `ScrambleService.markApplied()`: requires a prior reveal (409 otherwise), idempotent, records actor/time. Tested in `ScrambleFlowTest.appliedThenCheckedRequiresOrderAndSupportsAnIndependentSecondChecker` |
| 48 | `POST /scramble-assignments/{id}/mark-checked` | Scramble | Assigned scrambler/judge, optional independent checker | `ScrambleAssignment` (actor/time) | Checked-audit test | **Built** — `ScrambleService.markChecked()`: requires a prior "applied" (409 otherwise), and an `independent=true` request from a *different* actor records a distinct second check (07 S07 "a second checker toggle supports independent staff acknowledgment"). Tested in `ScrambleFlowTest.appliedThenCheckedRequiresOrderAndSupportsAnIndependentSecondChecker` |
| 49 | `POST /scramble-assignments/{id}/spoil` | Scramble | Organizer/Judge | `ScrambleAssignment`, replacement assignment from extras | Spoil-consumes-sequence test; replacement-audit test (R45) | **Built** — `ScrambleService.spoil()`: voids the assignment, burns its secret (never reusable), assigns an unused extra to a fresh assignment row, audits with the required reason; a plain Scrambler (no Judge/Organizer) is rejected (403); a clear `409 NO_EXTRA_SCRAMBLES_AVAILABLE` when extras are exhausted. Tested in `ScrambleFlowTest.spoilVoidsConsumesAndAssignsAnUnusedExtraWithAudit`, `.spoilFailsClearlyWhenNoExtrasRemain`, `.onlyOrganizerOrJudgeCanSpoilNotAPlainScrambler` |
| 50 | `GET /attempts/{attemptId}/current-scramble` *(path resolved at M4 — see DECISIONS.md "OD01... M4 implementation decisions" for why this concrete, attempt-scoped path was chosen over `08`'s abbreviated `/guest/.../current-scramble`)* | Scramble | Self-scramble-mode competitor, own credential, only when attempt unlocked | `ScrambleAssignment` | Future-sequence-hidden test (R42) | **Built** — `ScrambleService.currentScrambleForGuest()`: 403 for a non-self-scramble event, 403 until this attempt is the entrant's lowest-numbered still-`PENDING` attempt ("unlocked"), 404 for another guest's attempt, reveals (timestamped) on first access. Rides the existing `/attempts/**` guest-cookie path (no new `SecurityConfig` entry needed). Tested in `ScrambleFlowTest.selfScrambleCurrentScrambleIsGatedByUnlockAndNeverExposesAFutureAttempt`, `.staffPreparedModeNeverExposesCurrentScrambleToCompetitors` |
| 51 | `POST /attempts/{attemptId}/correction-requests` | Correction | Competitor, own attempt | `Correction` | Category/note validation test (P06) | **Built** — `CorrectionService.create()`: own attempt only, requires the attempt already has a non-`PENDING` result. **Authority note:** `08`'s endpoint name says "correction requests"; decisions are restricted to Organizer/Owner only per DECISIONS.md OD16 (genuine `00`/`08`-vs-`02` conflict, flagged there, not silently resolved). Tested in `CorrectionFlowTest.competitorCanRequestACorrectionOnlyForTheirOwnAttempt` |
| 52 | `GET /events/{eventId}/corrections` | Correction | Organizer/Judge | `Correction` | Authorized-list test (S10) | **Built** — `CorrectionService.listForEvent()`. Tested in `CorrectionFlowTest.onlyOrganizerCanDecideACorrectionNotJudgeOrStranger` (list/read access alongside the decision check) |
| 53 | `POST /corrections/{id}/decision` | Correction | Organizer (see OD16) | `Correction`, `ResultRevision` | Decision-preserves-original-value test (R40) | **Built** — `CorrectionService.decide()`: `ACCEPT_NO_RETRY` voids the attempt but preserves the original raw/penalty/status in `ResultRevision` history; `ACCEPT_RETRY` additionally allocates a replacement attempt; `expectedVersion` mismatch is `409`. Tested in `CorrectionFlowTest.acceptNoRetryVoidsTheAttemptButPreservesTheOriginalValueInHistory`, `.acceptRetryVoidsAndCreatesAReplacementAttempt`, `.onlyOrganizerCanDecideACorrectionNotJudgeOrStranger` |
| 54 | `GET /rounds/{roundId}/standings` | Results | Authorized staff (full detail); published-only for others | Computed standings (from `Attempt`) | Provisional-flag test (PA08) | **Built for staff** — `StandingsService.compute()`: groups attempts by entrant, scores via `ScoringEngine`, ranks via `RankingService`, `provisional = round.state != CLOSED`. The published/public-only view is now **built** too, as its own separate route (row 60: `PublicDisplayService.getStandings()`), not a parameter on this staff route — a deliberate separation (DECISIONS.md) so the staff endpoint's authorization and field set never has to branch on publish state. Tested in `StandingsIntegrationTest.revisingAJudgeResultRecalculatesStandings`, `.standingsAreAuthorizedStaffOnlyNotCompetitor` |
| 55 | `POST /rounds/{roundId}/advancement/preview` | Advancement | Organizer | `Round`, `EventEntrant` | Preview-math-visible test (V11, V12) | **Built** — `AdvancementController.preview()` / `AdvancementService.preview()`. Tested in `AdvancementFlowTest` |
| 56 | `POST /rounds/{roundId}/advancement/commit` | Advancement | Organizer | `Round`, next-round `EventEntrant` slots (`round_qualified_entrants`), `AuditEvent` | Idempotency/version-conflict test (R36) | **Built** — `AdvancementController.commit()` / `AdvancementService.commit()`. "Next-round entrant slots" is implemented as a *roster restriction* row per advancing entrant (`RoundQualifiedEntrant`), consumed by `RoundService.prepare()` when it later allocates the next round's actual `Attempt` slots — not a direct `Attempt` write, since attempts for a round are only ever created by that round's own `prepare()` call (unchanged M1-M4 behavior). Tested in `AdvancementFlowTest.commitCreatesNextRoundQualifiedRosterAndIsIdempotent`, `.staleVersionConflictsWithoutCorruptingAnything`, `.concurrentCommitsResultInExactlyOneSuccessAndNoDuplicateRoster` |
| 57 | `POST /events/{eventId}/publish` | Results/Display | Organizer | `Event` (`publishedAt`, `publicSlug`) | Publish test (S12) | **Built** — `EventController.publish()` / `EventService.publish()`. Generates `publicSlug` on first publish only (stable across re-publish). No `PublicSnapshot` table was built (see row 59-60 note); not gated on `EventState` (DECISIONS.md: that state machine barely advances past `REGISTRATION_LOCKED` in practice, so gating publish on it would make the feature unreachable). Tested in `PublishAndPublicFlowTest` |
| 58 | `POST /events/{eventId}/unpublish` | Results/Display | Organizer | `Event` (`publishedAt` cleared, `publicSlug` rotated) | Unpublish test | **Built** — `EventController.unpublish()` / `EventService.unpublish()`. Resolves a doc conflict found before building this (DECISIONS.md): `07` S13 says "Hide by revoking token" (implying a revocable identifier) while `08` names the identifier `publicSlug` (implying a stable slug). Resolution: unpublish rotates `publicSlug` to a fresh value, so a previously shared link is truly dead, not just temporarily unpublished-but-guessable. Tested in `PublishAndPublicFlowTest.publishGeneratesASlugAndUnpublishRotatesItInvalidatingOldLinks` |
| 59 | `GET /public/events/{publicSlug}` | Public display | Public, unauthenticated | `Event` fields via a hand-written allowlist DTO (`PublicEventView`) | Published-fields-only test (PA10) | **Built** — `PublicController.getEvent()` / `PublicDisplayService.getEvent()`, permitted by `SecurityConfig`'s existing `/api/v1/public/**` `permitAll` rule (no change needed there). No `PublicSnapshot` cache table was built — `08` names it "optional," and a live-computed, publish-gated read avoids a second place standings data can go stale; recorded as a genuine, documented simplification in DECISIONS.md rather than silently skipped. 404s for an unknown or unpublished slug, same anti-enumeration pattern used everywhere else in this codebase. Only rounds in READY/LIVE/REVIEW/CLOSED are listed — DRAFT/PREPARING rounds (and their roster) are never named publicly. Tested in `PublishAndPublicFlowTest.draftAndPreparingRoundsAreHiddenButLiveAndClosedAreVisible...`, `.publicDtoHasNoEmailTokenNotesOrScrambleFields`, `ScrambleSecurityGapTest.publicUnauthenticatedEndpointsNeverExposeScrambleNotation` |
| 60 | `GET /public/events/{publicSlug}/standings` | Public display | Public, unauthenticated | `Event`/`Round`/`Attempt` via `PublicStandingsView` allowlist DTO | Published-only-data test | **Built** — `PublicController.getStandings()` (query param `roundId`, matching `08`'s literal path with the round selector as a query string rather than a second path segment — a minor, documented route-shape choice, not a contract deviation in substance). Reuses `ScoringEngine`/`RankingService` directly (no duplicated scoring logic). Withdrawn entrants are excluded entirely; incomplete rounds are flagged `provisional` with per-entrant `completedAttempts`/`totalAttempts` counts rather than hiding the in-progress average (07 S13: "don't present partial averages as final," not "hide them"). Tested in `PublishAndPublicFlowTest.closedRoundIsFinalNotProvisionalAndWithdrawnEntrantIsExcluded`, `.draftAndPreparingRoundsAreHiddenButLiveAndClosedAreVisible...` |
| 61 | `GET /organizations/{orgId}/events/{eventId}/export.csv` | Export | Organizer/Owner | `Event`, `Attempt`, `EventEntrant` (export view) | CSV-column/UTF-8 test (S14) | **Built** — `HistoryController.exportCsv()` / `HistoryService.exportCsv()`. UTF-8 with BOM (Excel compatibility); columns `Round, Rank, Entrant, Outcome, ResultMs, ResultFormatted, BestSingleMs, BestSingleFormatted, AttemptsRawMs, AttemptsFormatted` — satisfying `09`'s "CSV exports raw milliseconds and formatted result" for both the round result and every individual attempt. A new, hand-written `CsvWriter` (no CSV library existed in the repo; none was added) RFC-4180-quotes cells and neutralizes CSV/formula injection (a cell starting `= + - @` or a tab gets a leading `'`) — a real risk the product docs never mention, since a competitor's free-text display name lands directly in an exported cell a staff member may open in a spreadsheet. `AuditService` records `EVENT_EXPORTED`. Tested in `HistoryExportFlowTest.csvContainsRawMsAndFormattedResultAndNeverScrambleNotation`, `.csvSanitizesFormulaInjectionInDisplayNames`, `.onlyOrganizerCanExportCsvJudgeAndStrangerCannot` |
| 62 | `GET /events/{eventId}/audit` | Audit | Owner/Organizer | `AuditEvent` | Restricted-access test | **Built** at M5 — `AuditController.list()`, newly exposing the `AuditEvent` rows every milestone since M1 has recorded via `AuditService` but never had a read endpoint for. Organizer-only (`TenantAccessService.requireOrganizer`); 404 for a non-staff caller. Tested in `HistoryExportFlowTest.auditEndpointIsOrganizerOnlyAndListsRecordedActions` |
| 63 | Real-time event-room subscription (SSE or WebSocket) | Real-time | Authenticated staff or guest, event-scoped subscription; public channel is a separate, published-fields-only stream | Minimal state-delta payloads (`{eventId, eventVersion, eventType, resourceId, changedFields, occurredAt}`) | No-scramble-in-payload test; public-channel-published-fields-only test; rate-limit/unsubscribe-on-scope-change test (R46) | **Built** (this audit pass) — `EventStreamService`/`EventStreamController` using Server-Sent Events (chosen over WebSocket: Spring MVC's `SseEmitter` needs no new protocol/framing, works over the existing HTTPS/cookie-session stack with zero new infra, and 08 explicitly allows either). `GET /events/{eventId}/stream` requires a recognized staff relationship to the event (`TenantAccessService.resolveStaffRole`, same anti-enumeration 404 as every other event-scoped read — "authenticated... subscriptions"); `GET /public/events/{publicSlug}/stream` requires only that the event currently be published. Every envelope matches the contract shape exactly and never carries scramble text or any resource content — callers pass only field *names* (`changedFields`), never values, and the public copy additionally drops `resourceId`/`changedFields` entirely ("public channel emits only published fields"). Wired into round transitions, judge/self-timed results, advancement commit/tie-break, correction create/decide, help request create/resolve, and event publish/unpublish. "Unsubscribe on scope change/logout" is satisfied by the browser's own `EventSource` lifecycle (the web hooks close the connection on unmount/dependency change); explicit per-connection rate limiting beyond the existing per-IP `SimpleRateLimiter` on other endpoints is not separately implemented — recorded as a deferred hardening item in DECISIONS.md, since a pilot-scale deployment (00 §10, one API instance) has a small, known subscriber count per event. "Client reconnects with last event ID or refetches snapshot": the browser's `EventSource` auto-reconnects with backoff, and the web hooks always refetch the REST snapshot on open/reconnect/message rather than trusting (or buffering) stream values, satisfying the "or refetches snapshot" branch without needing a server-side replay buffer. Tested in `EventStreamTest` (staff channel open+authorized vs. stranger 404; public channel 404 before publish vs. open after) |
| 64 | `GET /events/{eventId}/entrants` *(minimal addition, not in `08`'s enumerated list)* | Roster | Organizer/staff, org membership required | `EventEntrant` | Role-authorization test (`RosterAuthorizationTest`); 404-for-non-member test | **Built** — `RosterAuthorizationTest`, `EventLifecycleAndJoinTest`. See `RosterController`'s class comment and DECISIONS.md. |
| 65 | `POST /auth/email/verify` *(implemented at M1; documented in `08`'s endpoint list)* | Auth | Public, possession of the emailed token **and** a chosen password (the password is set here, not at registration — see DECISIONS.md); rate-limited 10/15min per client; CSRF-exempt like register/login (no prior session cookie to echo) | `PendingRegistration` (consumed/deleted), `User` (created here, not at registration) | Full happy-path test (`AuthFlowTest.registerVerifyThenLoginWorks`); single-use-token test (`AuthFlowTest.verificationTokenIsSingleUse`); hijack-prevention test (`AuthFlowTest.aSecondRegistrationForAnUnverifiedEmailInvalidatesTheFirstLinkRatherThanStealingIt`); rate-limit test (`AuthFlowTest.emailVerificationIsRateLimited`). Also verified against a real running browser with Playwright (register → check-your-email → click link → set password → dashboard → logout → login). | **Built and tested** |
| 66 | `GET /api/v1/events/{eventId}/rounds`, `GET /api/v1/rounds/{roundId}` *(M2 minimal additions, not in `08`'s enumerated list)* | Round | Staff (judge or organizer) | `Round` | List/get-scoped test | **Built** — `RoundService.listForEvent()`/`getForStaff()`. Needed by the organizer rounds screen and judge entry UI, which otherwise have no way to render round state. Tested throughout `RoundLifecycleTest` (every test reads round state back via `GET /rounds/{roundId}`) |
| 67 | `GET /api/v1/rounds/{roundId}/attempts`, `GET /api/v1/guest/events/{eventId}/me/attempts` *(M2 minimal additions, not in `08`'s enumerated list)* | Attempt | Staff (round attempts); competitor, own attempts only, across the whole event | `Attempt` | Authorized-list test; own-entrant-only field test | **Built** — `AttemptService.listForRoundAuthorized()`/`listOwnAttempts()`. The judge-entry table and the competitor's personal-results view both need a list, not just single-attempt `GET`. Tested in `AttemptFlowTest.competitorCanListTheirOwnAttemptsAcrossTheEvent` (cross-entrant isolation: another guest in the same event sees an empty list) |
| 68 | `GET /api/v1/attempts/{attemptId}/revisions` *(M2 minimal addition, not in `08`'s enumerated list)* | Attempt | Staff (judge or organizer) | `ResultRevision` | Revision-history-readable test (R09) | **Built** — `AttemptService.history()`. `00` §9 requires an immutable revision audit trail; without a read endpoint it would be unobservable. Tested in `AttemptFlowTest.judgeModeRecordsAResultAndEveryEntryCreatesARevision` |
| 69 | `POST/GET/DELETE /api/v1/events/{eventId}/staff-assignments` *(M2 minimal addition, not in `08`'s enumerated list)* | Event staff | Organizer-only to assign/remove; staff to list | `EventStaffAssignment` | Assign/list/remove test; judge-cannot-self-assign test | **Built** — `EventStaffController`/`EventStaffAssignmentRepository`. `00`/`08` require a judge role scoped independently of org membership (see DECISIONS.md "Judge role modeled as…"), which needs minimal CRUD to actually use. Tested in `RoundLifecycleTest.judgeAssignedToTheEventCannotConfigureRoundsButCanEnterResults` (assignment via this endpoint, then exercised) |
| 70 | `GET /api/v1/events/{eventId}/my-role` *(M3 minimal addition, not in `08`'s enumerated list)* | Event | Any staff (owner/organizer/judge) recognized for this event | Resolved role only, no other data | Own-role-resolution test; stranger-404 test | **Built** — `EventController.myRole()`/`TenantAccessService.resolveStaffRole()`. `00` §5/§9 require visible role display on every staff page; a judge with no org membership had no other way to learn their own role (see DECISIONS.md). Tested in `MyRoleApiTest` |
| 71 | `GET /api/v1/organizations/{orgId}/events/history?state=&from=&to=&q=` *(M5 minimal addition, distinct from row 15's existing `GET /organizations/{orgId}/events`)* | History | Staff, org-scoped | `Event` plus computed `entrantCount`/`roundCount` | Filter test; counts-correct test | **Built** — `HistoryController.history()`/`HistoryService.list()`. A deliberate *separate* route rather than extending row 15's existing endpoint: row 15's `EventView` is consumed by the dashboard and event-creation flow since M1, and widening its shape or filters risked a regression to code this task was told to preserve. `08`'s own route text (`?state=&from=&to=&cursor=`) is literally row 15's text; this row's `q` (name-contains) param is this milestone's own addition for S14's "search... by date/name/status," and `cursor` is not implemented (plain list; the same no-pagination gap already recorded for row 15 and elsewhere, not a new one). Tested in `HistoryExportFlowTest.historyListsAndFiltersByStateAndName`, `.historySummaryCountsEntrantsAndRounds` |

Cross-cutting API conventions that apply to every route above (idempotency keys on writes,
optimistic concurrency via `expectedVersion`/`If-Match`, stable error codes, cursor pagination on
lists, 404-not-403 on inaccessible objects) are verified once per convention rather than once per
route, via the `08` "API security tests" paragraph and R39/R58 above. **M1 status:** optimistic
concurrency (`version` field) and 404-not-403 are built and tested; `Idempotency-Key` header
handling and cursor pagination are not yet built (no M1 endpoint needs pagination; idempotency
keys are deferred to M2 where duplicate attempt submission is a real risk).

---

## Coverage statement

This document maps:
- all **13** V1-must-ship items and all **11** V1 exclusions in `00` §2,
- all mode/default/role/lifecycle/scramble/stack/completion rules in `00` §3–§12 (**49** items,
  R14–R62),
- all **19** scoring conformance vectors and property tests in `09`,
- all **22** screens in `07`,
- all **62** distinct REST routes named in `08` plus the 1 real-time subscription channel (**63
  API surfaces** in total — see section G for the corrected count and the full per-route table;
  the figure of "51" in the first draft of this document was an undercount from not expanding
  every comma-separated route group, and has been corrected here), plus **8 minimal, documented
  additions** not itemized in `08`'s original list (row 64, roster read; row 65, email
  verification; rows 66–69, added at M2 — round list/get, attempt list views, revision history
  read, and event-staff-assignment CRUD; row 70, added at M3 — role resolution for `RoleBanner`;
  row 71, added at M5 — the filterable/counted event-history list, kept separate from row 15's
  existing plain list endpoint — see section G rows 64–71 and DECISIONS.md for why each was
  needed),
- all **28** release-acceptance checklist items plus the event-day/deployment/sign-off process
  items in `12`.

Nothing in the five controlling/behavioral documents (`00`, `07`, `08`, `09`, `12`) was left
unmapped. Narrative-only documents (`01`, `02`, `03`, `04`, `05`, `06`, `10`, `11`) were used to
resolve ambiguity and inform the Area/Evidence columns above but do not introduce separate
requirement IDs, since `00` is controlling and the behavioral contracts (`07`–`09`) are where
narrative intent becomes testable.

## Document consistency checks performed

- Recounted every route in `08-data-api-contract.md` by expanding each comma-separated group into
  individual routes; corrected the prior "51" figure to the verified **62 REST routes + 1
  real-time channel**. See section G.
- Cross-checked that every route added in section G has a corresponding row or coverage note in
  section C (role/behavior rules) so the same route isn't silently treated differently in two
  places.
- Confirmed section D's 19 scoring items, section E's 22 screens, and section F's 28 release-gate
  items against a fresh line-by-line re-read of `09`, `07`, and `12` — counts unchanged from the
  previous version, no corrections needed there.
- Confirmed no product requirement, default, or test expectation was altered while fixing the
  route table — only the API section was expanded and the count corrected.
- **M1 review follow-up:** added row 65 (`POST /auth/email/verify`), which M1 implemented and
  tested for rate limiting but had never added to this table — an undocumented endpoint, now
  fixed. Updated row 1 (`register`) to reflect the non-enumerable response and its rate limit.
  Also added the same `/auth/email/verify` line to `08-data-api-contract.md` itself (see that
  file), since it's the actual "API contract" document and the endpoint was missing from it too,
- **M1 follow-up (double opt-in registration):** updated rows 1 and 65 again to reflect that
  registration no longer collects a password or creates a `User` row, and verification is now
  where both happen — see DECISIONS.md "M1 follow-up: double opt-in registration" for the full
  rationale and test evidence. No route was added or removed; only the request/response contract
  and the "Built" status of row 65 changed (from "partially tested" to fully tested with a
  happy-path case now that tests capture the real token via a test-only `MailService`).
- **M2 (competition engine):** updated rows 24–54 (roster management, round lifecycle, attempt
  lifecycle, correction workflow, standings) from "Not built — M2 scope" to "Built" with actual
  test-method evidence; updated section C rows R04, R09, R14–R18, R19–R24, R33–R40 and section D's
  full 19-item scoring table from planned to built/tested; updated section E rows P04–P07, S05,
  S06, S09–S11 the same way; added rows 66–69 (round list/get, attempt list views, revision-history
  read, event-staff-assignment CRUD) for minimal additions beyond `08`'s enumerated list, and a note
  for the `rounds/[roundId]/standings` screen (not one of `07`'s 22 enumerated screens). Rows/items
  explicitly **not** changed to "Built" (still correctly "Not built"): R36/row 36's advancement
  commit-and-apply (preview/commit as an operational feature — only the pure math, V11/V12, is
  built), S07/S08/S13 (scramble station, Tournament Control, public display — M3/M4/M5 scope), and
  rows 38, 44–50, 55–63 (control-room snapshot, scramble-vault routes, advancement/publish/export/
  public-display routes, real-time channel) — all genuinely out of this milestone's stated scope,
  not silently skipped.
- **M3 (organizer and competitor experience):** closed OD16 (correction-decision authority,
  organizer-only) per explicit instruction — see DECISIONS.md. Updated rows R26, R29, R52, R53
  (role display, confirmation, responsive/keyboard/a11y, status-state components) from planned to
  built, with explicit note of what remains unverified (200%-zoom and full screen-reader audits).
  Updated section E rows P03–P07, S03, S04, S08–S11 from planned/not-built to built, with M3's
  specific additions named per row (category selector and request-ID confirmation on P06; client
  validation, save receipt, undo window, and stale-version handling on S09; required-reason
  enforcement, decision confirmation, and a recent-decisions audit view on S10). Added row 70
  (`GET /events/{eventId}/my-role`) and `Round.startedAt` (migration `V4`) as the only two new
  pieces of server surface this milestone needed — everything else in S08 Tournament Control
  reuses M2 reads. Rows/items explicitly **not** changed to "Built" (still correctly "Not
  built" or "partial"): advancement preview/commit (R36, S11), "Request judge/help" as a
  pre-result general escalation distinct from a correction request (P05 — documented gap, not a
  silent omission), S04's tabbed layout (built as sections on one page instead), S07/S13
  (scramble station, public display — M4/M5 scope), real-time push updates for S08 (row 63,
  still not built) — all genuinely out of this milestone's stated scope or explicitly deferred,
  not silently skipped.
- **M4 (scramble controls)**, recorded here retroactively since the M4 session's own report
  updated rows 44–50 and R41–R46 directly but never added the convention's own summary paragraph:
  resolved OD01 (TNoodle for generation, cubing.js/`TwistyPlayer` for the 3D guide — see
  DECISIONS.md). Added the `scramble` package (batch/secret/assignment entities, encryption
  service, generator wrapper, service, controller), the `SCRAMBLER` event role, and
  `rounds/[roundId]/scramble` + the self-scramble reveal on the competitor waiting room. Updated
  rows 44–50, R41–R46, S07. Rows explicitly **not** changed: 54–63 (advancement/publish/
  export/public-display/real-time — M5/M3-adjacent scope).
- **M4 scramble-secrecy verification gap, closed before M5:** the M4 report's own leakage checks
  covered competitor HTML, competitor API access, and an unauthenticated request, but `08`'s full
  acceptance list also names error responses, logs, caches, exports, and unauthorized staff
  views. Added `ScrambleSecurityGapTest` covering all of these directly (exports are additionally
  covered by `HistoryExportFlowTest` once CSV export existed to test). Found and **fixed one real
  exposure**: `ScrambleDtos.RevealView`/`PrintEntry`'s default record `toString()` would print
  plaintext notation into application logs the moment anyone ever raised Spring MVC's request-
  logging level to DEBUG (its "Writing [...]" log line calls a response body's `toString()`
  verbatim) — found by the new log-capture test, fixed by overriding `toString()` on both records
  to redact `notation`. Also added `Cache-Control: no-store` to every notation-carrying response
  (reveal/official-view/print/current-scramble), since none of them had ever set a caching header
  before. The TNoodle GPL-3.0 SaaS-vs-distribution licensing question remains an explicitly open
  launch-review item (DECISIONS.md) — not resolved, not silently dropped, and the generator/
  license itself was not touched by this pass.
- **M5 (advancement, publishing, public display, history, export):** updated rows 54–62 and 71
  from "Not built — M5 scope" to "Built," section C rows R19–R24 (Scrambler role — corrected a
  stale M4-era "Not built" left over from the prior session), R29, and R36, and section E rows
  S07 (corrected the same stale note), S11–S14. New server surface: `AdvancementService`/
  `AdvancementController` (reusing `AdvancementCalculator`/`RankingService`/`ScoringEngine`
  unchanged), `RoundQualifiedEntrant` (the next-round roster restriction `RoundService.prepare()`
  now consults), `Event.publishedAt`/`publicSlug` + `EventService.publish()`/`.unpublish()`, the
  `pub` package (`PublicController`/`PublicDisplayService`, unauthenticated), and the `history`
  package (`HistoryController`/`HistoryService`/`CsvWriter`). New web routes:
  `rounds/[roundId]/advancement`, `public/[slug]`, `organizations/[orgId]/history`, plus publish/
  unpublish controls and an Event history link on the existing event page. Rows/items explicitly
  **not** changed to "Built" (still correctly "Not built" or "partial," per DECISIONS.md's M5
  decisions, not silently skipped): the optional tie-break *attempt* from `09` (ties always
  advance together instead); a `PublicSnapshot` cache table (public reads compute live instead);
  public name masking (S12); advancement rollback, S12's dedicated tabs, and S14's "copy event
  settings"/retention-policy deletion; row 63's real-time channel (still M3-adjacent, unbuilt).
- **V1 audit pass (post-M5, pre-M6):** a requirement-by-requirement re-audit against `00` §2 and
  `07`/`08`/`09` found that several M4/M5 "Built" rows understated or omitted real gaps, and that
  several deferred M5 items were in fact V1 "must-ship" scope, not legitimate deferrals. Closed in
  this pass, each with new tests and audit events:
  - **Scramble reveal authorization boundary fixed** (00 §7 "revealed only to assigned
    scrambler/judge" vs. the broader org-wide `requireScrambleStaff` the M4 report had used for
    `reveal`/`officialView`/`print`): added `TenantAccessService.requireAssignedScrambleStaff`
    (assigned JUDGE/SCRAMBLER only, no org-wide bypass) and switched those three methods to it;
    `markApplied`/`markChecked` correctly keep the broader check (metadata-only, no notation).
    `ScrambleSecurityGapTest.onlyAnExplicitlyAssignedScramblerOrJudgeCanRevealNotJustAnyOrgMember`.
  - **Production scramble-key fail-fast** (04 "never let production run without its configured
    secret"): `ScrambleEncryptionService` now refuses to start under the `production` Spring
    profile if `TWISTMEET_SCRAMBLE_ENCRYPTION_KEY` is absent or not valid Base64, instead of
    silently falling back to an ephemeral, restart-losing key; the ephemeral fallback remains for
    every non-production profile. `ScrambleEncryptionServiceTest`.
  - **Event clone/archive/complete/reopen** (00 §6, R02/R29/R32, S03/S14): `EventService.clone()`
    (copies config, never entrants/rounds/attempts), `.complete()` (every round CLOSED required),
    `.archive()`/`.reopen()`, and a `requireNotArchived` guard added to every mutating event/round
    method so an archived event is genuinely read-only. `EventArchiveCloneTest`.
  - **Organizer-selected tie-break attempt** (09 — see updated R36/row 55-56 above).
  - **Competitor help/request-judge flow** (07 P05/S08, distinct from a correction request — see
    updated P05 above).
  - **Public name masking, wired end-to-end** (07 S12 — see updated S12 above; the API field
    existed but had no web control and the web `EventView` type didn't even carry it).
  - **Real-time room updates over SSE** (08 "Real-time updates" — see updated S08/S13/row 63
    above; previously entirely unbuilt, not polling-as-substitute).
  New tables: `help_requests`; new column: `events.public_name_mask` (migration
  `V7__name_masking_and_help_requests.sql`). New packages: `help` (`HelpRequestService`/
  `HelpRequestController`), `stream` (`EventStreamService`/`EventStreamController`). Explicitly
  **not** found to be V1-scope gaps, left as documented M6/launch-review items instead (see
  DECISIONS.md): S12's dedicated live/unpublished/published/revisions tabs (every underlying
  capability is already reachable from existing pages — a UI-consolidation deferral, not a missing
  one); advancement rollback; S14's "copy event settings" is now covered by `clone()`'s config-copy
  behavior, but S14's retention-policy deletion remains unbuilt pending the M6 data-retention work;
  password reset/MFA; org member invite/role management endpoints; an `Idempotency-Key` header
  mechanism; cursor pagination; a groups/stations model.
- **M6 (reliability, privacy, accessibility, pilot readiness) — same session, after the V1 audit
  pass above.** Four new top-level review/runbook documents, each with evidence rather than a
  checklist ticked from memory: `OPERATIONS.md` (a real `pg_dump`/`pg_restore` backup/restore
  drill run and verified byte-for-byte this session; the existing actuator-health/audit-logging
  monitoring surface and what's missing; incident procedures for scramble exposure, event outage,
  and migration failure; named launch blockers needing a human decision — hosting/domain,
  production email, error-tracking vendor, support owner, pilot concurrency, legal jurisdiction,
  translations); `SECURITY_PRIVACY_REVIEW.md` (12's full security/privacy checklist item by item;
  found and fixed a real gap — the session/CSRF cookies had neither `Secure` nor `SameSite` set
  despite `SecurityConfig`'s own comment claiming otherwise, now fixed under the production
  profile and tested in `SecurityCookieAttributesTest`; named MFA, password recovery, personal
  data export, and retention/deletion as genuine launch blockers rather than built hastily);
  `ACCESSIBILITY_REVIEW.md` (a real `@axe-core/playwright` scan against five real, data-populated
  pages — found and fixed one critical violation, a `<select>` with no accessible name on the
  event settings page; verified no 360px overflow, no meaningful motion to suppress, and no
  color-contrast violations; found and fixed a missing `aria-live` announcement on Tournament
  Control's live progress summary; honestly flagged 200%-zoom and keyboard-focus-visibility as
  not conclusively tested by this pass's automated proxies, and screen-reader verification of the
  new announcement as still not done with an actual screen reader); `PILOT_CHECKLIST.md` (a
  role-by-role event-day checklist citing the exact screen/action per item). Also closed two
  process gaps the original task's instructions named directly: a printable offline score sheet
  (`rounds/[roundId]/print-sheet`, row added to S08/00 §2 item 12 coverage) for `00` §2 item 12's
  "offline paper fallback," and a real, committed browser E2E test
  (`web/e2e/critical-journey.spec.ts`, run and verified passing multiple times this session) for
  "browser E2E for critical role journeys" — the TRACEABILITY rows above that cite
  `m3_e2e_organizer.mjs`/`m5_e2e.mjs`/etc. as evidence are citing real manual verification runs
  from earlier milestones, but those scripts were only ever written to a session scratchpad, not
  committed to this repository, so they were never a regression-tested or CI-capable asset — a
  gap this session's E2E test closes for the one journey it covers (not retroactively for every
  milestone's own ad hoc script, which would be a much larger undertaking than this pass's
  scope). Not wired into `.github/workflows/ci.yml` yet (needs the workflow to also start a mail
  catcher and both dev servers) — recorded as a follow-up in README, not silently assumed done.

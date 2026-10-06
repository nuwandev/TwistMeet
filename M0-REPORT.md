# M0 report — repository inspection, decisions and milestone plan

Status: **M0 complete; M1 (Product foundation + project setup) now also complete.** §0–§4 and the
open-decisions register below are the original M0 deliverable and are unchanged except for one
route-count correction. §5's milestone table reflects M1 as built, tested (12 JUnit integration
tests plus a real-browser Playwright walkthrough), and documented in `TRACEABILITY.md`/
`DECISIONS.md`. See the end of this file for the M1 completion summary: what works end to end,
commands run, and remaining gaps going into M2.

## 0. Specification package checked

Every file in `product-docs/` was read in full before this report was written:

| # | File | Role |
|---|---|---|
| 1 | `README.md` | Package index / handoff note |
| 2 | `00-authoritative-build-contract.md` | **Controlling specification** |
| 3 | `01-product-strategy.md` | Product purpose, principles, scope rationale |
| 4 | `02-rules-and-integrity.md` | Competition rules and result-integrity policy |
| 5 | `03-experience-and-workflows.md` | Role journeys and screen-level workflow narrative |
| 6 | `04-architecture-security.md` | System architecture and security boundaries |
| 7 | `05-validation-roadmap-launch.md` | Discovery/validation plan, roadmap, release gates |
| 8 | `06-market-review.md` | Competitive landscape review |
| 9 | `07-screen-and-component-spec.md` | Screen/component authority |
| 10 | `08-data-api-contract.md` | Data/API behavior contract |
| 11 | `09-scoring-conformance.md` | Scoring rules and conformance vectors |
| 12 | `10-role-guides.md` | Operator/role help-center baseline |
| 13 | `11-ai-build-playbook.md` | Build process, milestones, agent operating rules |
| 14 | `12-release-acceptance.md` | Release/operations checklist |

All 14 files (13 numbered documents plus the package `README`) were read in full, not sampled.
No file was skipped. Where `01`–`06`, `10` narrative content and `00`/`07`/`08`/`09` behavioral
contracts overlap, `00` controls per its own §0 ("If two documents conflict, follow this file,
then the more specific contract in `07`, `08`, and `09`").

## 1. What's in the repository, what needs to be built

**Current state:** the repository contains only `product-docs/` and this planning output
(`DECISIONS.md`, this report, `TRACEABILITY.md`). One commit existed before this session
(`add project docs`); working tree was clean; no application code, build tooling, CI
configuration, `.gitignore`, or dependency manifests exist. This is a greenfield build — nothing
to preserve, no existing conventions to adapt to.

**Everything in `00` §2 "V1 must ship" needs to be built**: staff/org auth and tenant isolation,
event lifecycle and registration, guest join (QR/URL/short code), roster management, round/attempt
configuration and state machines, the two timer modes, the scramble vault and 3D guide, judge
entry and corrections, Tournament Control, the scoring/ranking engine, advancement, publishing and
public display, CSV export and history, plus the accessibility, security, privacy, backup,
monitoring and documentation baseline from `04`, `05`, `12`.

## 2. Contradictions, missing decisions, risks

- **Precision wording inconsistency (not a contradiction in the controlling doc):**
  `02-rules-and-integrity.md` describes `+2` as "integer centiseconds/milliseconds according to
  chosen precision," which is looser than `00` §4 ("Store and rank by exact integer
  milliseconds"), `04`, `08`, and `09`, which are all unambiguous about integer milliseconds as
  the sole canonical unit. Since `02` is a supporting document and `00`/`08`/`09` are controlling
  or more specific, there is no real conflict to escalate — resolved to **integer milliseconds
  everywhere**, recorded in `DECISIONS.md`.
- **Scramble generator unnamed:** no specific library/service is named in any document; `04` and
  `05` require independent provenance/license/correctness review before use. Not blocking M0–M3;
  must be resolved before M4.
- **"TwistMeet" is explicitly provisional** (`00` title, package `README`) — safe to keep using
  internally; do not bake it into a public domain, trademark filing, or external communication
  without separate clearance.
- **Validation-before-build tension (risk, not a blocker):** `05-validation-roadmap-launch.md`
  frames the entire project as needing organizer interviews and a go/no-go signal (≥3 committed
  pilot organizers) *before* a full build. Proceeding into implementation planning in this
  engagement supersedes that discovery gate; flagging it so the gap is visible rather than
  silently dropped, not re-asking it.
- **Risk:** the Physical Timer + Judge Entry and Competitor Phone Timer modes share most of the
  `Attempt` state machine but have materially different security/fairness rules (`00` §3, §7).
  The highest-risk place for an accidental scramble leak or mislabeled result source is code that
  handles both modes through shared paths. Flagging for extra test attention at M2/M4.
- **No blocking contradictions found** between `00` and the more specific contracts (`07`, `08`,
  `09`) — where they overlap, the later documents sharpen `00` rather than conflict with it.
- **Route-count error found and corrected:** the first pass of `TRACEABILITY.md` stated "51
  distinct API endpoints" in `08-data-api-contract.md`. Recounting by expanding every
  comma-separated route group (e.g. `/registration/open`, `/lock`, `/reopen` is three routes, not
  one) gives **62 distinct REST routes plus 1 real-time channel (63 API surfaces)**. Corrected in
  `TRACEABILITY.md` section G, which now gives every route its own row with module, authorization,
  data, and evidence.
- **Product name and branding are unresolved**, not a contradiction but worth restating here:
  "TwistMeet" is explicitly provisional (`00` title, package `README`). Recorded as OD02 in
  `DECISIONS.md`'s open decisions register, with the operating rule that all user-visible
  branding must stay in one configurable value until a name is chosen.
- **Fourteen other decisions are genuinely unresolved** across product, technical, legal, brand
  and operations categories — see `DECISIONS.md`'s open decisions register (OD01–OD14). None of
  them block M1; their required-by milestones range from M1 (pick a migration tool, pick an email
  provider for production) to M4 (scramble generator) to M6/public-beta (hosting target, legal
  review, children's-use policy, incident owner, subprocessor list, pilot concurrency figure).

## 3. Requirement-to-screen/API/data/test traceability

See `TRACEABILITY.md` for the complete, non-sampled map: every numbered V1 requirement and
exclusion in `00`, every mode/default/role/lifecycle rule in `00` §3–§11, every screen in `07`,
every one of the **62 REST routes + 1 real-time channel** in `08` (section G, corrected from an
earlier miscount of 51 — see below), every conformance vector and property test in `09`, and
every checklist item in `12`, each mapped to an implementation area, authorization/role, relevant
data, and the test or release evidence that will verify it (or marked not-applicable with a
reason).

## 4. `DECISIONS.md` review against the contract

Reviewed `DECISIONS.md` line by line against `00` and the supporting documents:

- **Stack decision** — adopts the reference stack from `00` §10 as-is. Correct: `00` §10 only
  requires documenting a deviation when an *existing* repository has a different compatible
  stack; there is no existing code, so this is not a deviation, just the default taking effect.
- **Defaults applied** (puzzle, round, mode, scramble policy, precision, tie-break, correction cap,
  visibility, timer inspection, advancement ties) — each one is copied verbatim from `00` §4/§9
  and `09`, with no alteration. These are **settled choices, kept as-is**, not reopened.
  Specifically the defaults are listed in `00` §4, final advancement tie rule in `00` §4 and `09`
  "Advancement."
- **Precision resolution** — correctly identifies `02`'s looser wording and resolves to
  milliseconds per the controlling and more specific documents, not an invented deviation.
- **Open items** (scramble generator, hosting target, brand/domain) are correctly deferred to the
  milestone that needs them (M4, M6, later) rather than blocking now.
- **No undocumented deviation was found.** `DECISIONS.md`'s "Deviations from documents" section
  correctly remains empty — nothing in the file departs from a specified behavior; it only
  records defaults and a wording clarification. No settled default or product requirement was
  changed in this pass.
- **Added, not changed:** this pass added a product-name/branding note and a 14-item open
  decisions register (OD01–OD14) to `DECISIONS.md`, covering every genuinely unresolved product,
  technical, legal, brand, and operations decision found across the full document package. These
  are additions, not replacements of prior content; the stack decision and the settled defaults
  (puzzle, mode, scramble policy, precision, tie-break, correction cap, visibility, timer
  inspection, advancement ties) are unchanged.

## 5. Milestone plan (per `11-ai-build-playbook.md`)

Same milestone sequence as the initial M0 report, with M1 now including the greenfield
project-setup tasks the first report omitted (none of this is application code — scaffolding,
tooling and CI configuration only):

| Milestone | Scope | Checks run at exit |
|---|---|---|
| **M0** — complete | Inventory, full traceability map (62 routes + 1 realtime channel, corrected), `DECISIONS.md` review, open decisions register (OD01–OD14), branding note | No unknown project instructions; no unresolved decision blocks M1; package fully read and confirmed; route count verified against `08` |
| **M1** — complete | `.gitignore`; Gradle-wrapped Spring Boot API (Java 21) with Flyway migrations; Next.js/TypeScript web app; Docker Compose for local Postgres + mail catcher; `README` local-start instructions; GitHub Actions CI (format/lint, test, build for both API and web); staff register/login/logout; organizations with owner membership; tenant-isolated event create/edit/lifecycle (draft→open→locked→reopened) with join-code rotation; guest join with duplicate-name disambiguation and event-scoped credential; organizer-only roster read; seeded demo event | CI config written and exercised locally (`./gradlew check && ./gradlew build`, `npm run lint && npm run build`) — all green; 12 JUnit integration tests pass (cross-tenant denial, guest event-boundary, lifecycle/join-gating, roster authorization); full browser flow (register→org→event→open registration→guest join→roster visible→lock blocks late join) verified with Playwright against the real running app, not just unit tests; demo seed verified joinable end-to-end |
| **M2** Competition engine | Immutable ruleset snapshot; round/attempt state machines; scoring module covering every vector in `09`; roster management; judge entry + append-only revisions | All `09` conformance + property tests pass; no direct competitor edit path exists; correction path preserves source history |
| **M3** Organizer/competitor UI | Event wizard, roster, control room, judge entry, waiting room, results, error/empty/offline states | End-to-end create→publish flow works at 360px and desktop; keyboard and screen-reader critical paths checked |
| **M4** Scramble controls + 3D guide | Generator dependency/license review; encrypted payload; role-scoped reveal; extra sequences; print; move guide; applied/checked log; self-scramble gated to casual phone mode | Security tests prove no scramble leakage across API/HTML/cache/logs/public view; sample physical-cube guide usability checked |
| **M5** Advancement, display, history | Standings; tie/percentage preview; commit; public display; organization history; CSV export | Concurrency/idempotency tests pass; public display is read-only and reveals only published fields |
| **M6** Reliability, privacy, pilot | Offline judge-entry queue + printed fallback; backups/restore drill; monitoring; account deletion/export; terms/privacy placeholders reviewed; accessibility audit; security check; pilot checklist | Pilot run with real people and a physical timer; every unchecked `12` item listed; owner sign-off |

### M1 greenfield setup detail (built)

- `.gitignore` at repo root covers both API (Gradle build output, `.gradle/`) and web
  (`node_modules`, `.next`, build output) plus `.env*` (except `.env.example`), IDE files, logs.
- Pinned toolchain: Java 21 (Gradle toolchain in `api/build.gradle.kts`), Gradle 8.10.2 (wrapper
  committed under `api/gradle/wrapper/`), Node 22.x (`web/.nvmrc` + `package.json` `engines`),
  npm lockfile (`web/package-lock.json`) committed. Migration tool decided as **Flyway** (OD05,
  recorded in `DECISIONS.md`).
- `README.md` at repo root: `docker compose up`, API run command (plain and `seed` profile), web
  install/dev commands, and the checks section below.
- CI: `.github/workflows/ci.yml`, two jobs (`api`, `web`) on every push/PR. `api` runs
  `./gradlew check` (Spotless format check + JUnit tests) then `./gradlew build`, against a
  `postgres:16-alpine` service container. `web` runs `npm ci`, `npm run lint` (ESLint), and
  `npm run build` (which also type-checks). No deployment step — verification only, as planned.

## 6. Blockers needing your decision

**Nothing blocks starting M1.** The full open decisions register is in `DECISIONS.md`
(OD01–OD14); summarized by urgency:

- **Needed during M1, but with a safe default available so M1 can proceed:** database migration
  tool (OD05 — default Flyway, needs confirmation) and outgoing email provider for production use
  (OD04 — local dev can use a mail catcher in the meantime).
- **Needed before M4:** scramble generator/library selection and review (OD01) — no safe default
  exists; must be an explicit reviewed choice, not invented.
- **Needed before M6:** hosting/domain target (OD03), privacy/legal reviewer and launch
  jurisdictions (OD06), children's-use policy if minors may participate (OD07), expected pilot
  concurrency figure for load testing (OD13).
- **Needed before public beta, not before:** incident owner/support contact/status page (OD11),
  subprocessor list (OD12), pricing model (OD10), translation languages (OD09).
- **No fixed deadline, resolve whenever convenient:** final product name/domain (OD02 — branding
  stays configurable and neutral until then), display-name content-filter specifics (OD08 — a
  conservative default is already assumed), object storage provider (OD14 — deferred by design).

## 7. Recommendation

M0 and M1 are both complete. Recommending **M2 — Competition engine** (ruleset snapshot,
round/attempt state machines, the scoring module covering every `09` conformance vector, roster
management, judge entry with append-only revisions) as the next milestone. Waiting for review
before starting M2.

## 8. M1 completion summary

### What works end to end

Verified in two independent ways: 12 JUnit integration tests (`AuthFlowTest`,
`CrossTenantAccessTest`, `EventLifecycleAndJoinTest`, `RosterAuthorizationTest`) running the real
Spring context against a real PostgreSQL database, and a real-browser walkthrough (Playwright
driving actual Chromium against the running API and the running Next.js dev server, not a mock):
register a staff account → log in → create an organization → create a draft event → open
registration → copy the join link/code shown on the event page → join as a guest in a separate
browser context (no shared cookies) with display-name disambiguation on repeat names → organizer
sees the guest appear on the roster → lock registration → a late guest's join attempt is
correctly rejected → join-code rotation invalidates the old code. The seeded demo event
(`SPRING_PROFILES_ACTIVE=seed`) is joinable the same way.

### Commands run and results

| Command | Result |
|---|---|
| `cd api && ./gradlew check` | **BUILD SUCCESSFUL** — Spotless format check clean; 12/12 JUnit tests pass |
| `cd api && ./gradlew build` | **BUILD SUCCESSFUL** — produces `build/libs/twistmeet-api-0.1.0-m1.jar` |
| `cd web && npm run lint` | **No ESLint warnings or errors** |
| `cd web && npm run build` | **Compiled successfully**, type-checked, 8 routes built |
| Playwright E2E walkthrough (register→org→event→open→join→roster→lock→reject-late-join) | **PASS**, run repeatedly for stability after fixing two real bugs found this way (see below) |

### Real bugs found and fixed while verifying in a browser (not just unit tests)

1. **Missing CORS configuration.** The web app (`:3000`) and API (`:8080`) are different origins;
   cookie-based auth needs explicit CORS with credentials, which had not been added. Fixed with a
   `CorsConfigurationSource` restricted to the single configured web origin. The JUnit suite never
   caught this because it calls the API directly, not through a browser enforcing CORS.
2. **A stale-`currentTarget` bug in the dashboard's "create organization"/"create event" forms.**
   Both handlers read `e.currentTarget.reset()` *after* an `await`, by which point React has
   already nulled `currentTarget` (a well-known gotcha, not a pooling issue) — the request itself
   succeeded, but the resulting exception was swallowed by the `catch` block and shown as "Failed
   to create organization," hiding a working feature behind a false error. Fixed by capturing the
   form element before the first `await`. Found only by running the real app in a loop, not by
   `next lint`/`next build`, which stayed green throughout — recorded in `DECISIONS.md` as a
   concrete example of why build/lint passing is not the same as the feature working.

### Changed/added files

- `api/` — new Gradle project: `build.gradle.kts`, `settings.gradle.kts`, Gradle wrapper, full
  Spring Boot source tree under `src/main/java/com/twistmeet/api/` (packages: `common`, `auth`,
  `org`, `event`, `registration`, `config`), one Flyway migration
  (`src/main/resources/db/migration/V1__init_m1_schema.sql`), `application.yml`, and the test
  tree under `src/test/java` (4 test classes + `support/` test harness) with
  `src/test/resources/application-test.yml`.
- `web/` — new Next.js project generated via `create-next-app`, with `src/lib/` (branding
  config, API client, shared types), and pages for home, sign-in, dashboard, event detail, join
  (+ pre-filled-code variant), and the guest waiting room; `src/app/globals.css` carries the
  `07` design tokens. `.env.example`, `.nvmrc`.
- `docker-compose.yml` — Postgres + MailDev.
- `.gitignore` (root), `README.md` (root), `.github/workflows/ci.yml`.
- `DECISIONS.md`, `TRACEABILITY.md`, this file — updated for everything above.

### Remaining gaps going into M2 (not blockers, named so they aren't lost)

- `POST /events/{eventId}/registration/reopen` is implemented but has no dedicated test.
- Join-code rate limiting (`SimpleRateLimiter`) is implemented but not covered by an automated
  test (manually exercising the limiter would require either 20+ scripted requests or exposing a
  test hook; deferred rather than adding either under this milestone's scope).
- MFA, password reset, and staff invitations (`08` endpoints 4–7, 11–13) are not built — all
  explicitly out of M1's stated scope (MFA is "required before public beta" per `00` §10; staff
  invitations are an M2+ roster/staff-management concern).
- Guest/public role-filtered event detail (`GET /events/{eventId}` for non-staff callers) is not
  built; guests currently only get the event name via the join response, which is enough for M1's
  "join by link/code" scope but will need revisiting once public event pages are built (M3/M5).
- The web app's pages are functional but not yet styled/polished to the full `07` component spec
  (no `StatusBadge`/`ConfirmDialog`/etc. as reusable components yet) — reasonable for a foundation
  milestone whose stated goal was a "shared UI foundation," not the organizer/competitor UI itself
  (that's M3).

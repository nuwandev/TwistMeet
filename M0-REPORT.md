# M0 report — repository inspection, decisions and milestone plan

Status: **M0 in progress, no application code written.** This report supersedes the earlier
chat-only M0 summary (same content, corrected table formatting, saved to the repository per
`11-ai-build-playbook.md` M0 exit criteria: "Output architecture inventory, requirement
traceability and `DECISIONS.md`").

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

## 3. Requirement-to-screen/API/data/test traceability

See `TRACEABILITY.md` for the complete, non-sampled map: every numbered V1 requirement and
exclusion in `00`, every mode/default/role/lifecycle rule in `00` §3–§11, every screen in `07`,
every endpoint in `08`, every conformance vector and property test in `09`, and every checklist
item in `12`, each mapped to an implementation area and the test or release evidence that will
verify it (or marked not-applicable with a reason).

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
  records defaults and a wording clarification. No change made to `DECISIONS.md`'s decisions
  themselves in this review; only this confirmation is new.

## 5. Milestone plan (per `11-ai-build-playbook.md`)

Same milestone sequence as the initial M0 report, with M1 now including the greenfield
project-setup tasks the first report omitted (none of this is application code — scaffolding,
tooling and CI configuration only):

| Milestone | Scope | Checks run at exit |
|---|---|---|
| **M0** (this session) | Inventory, full traceability map, `DECISIONS.md` review | No unknown project instructions; no unresolved blocking decision; package fully read and confirmed |
| **M1** Product foundation + project setup | `.gitignore`; pinned toolchain and dependency versions for API (Spring Boot/Java) and web (Next.js/TypeScript); Docker Compose for local Postgres; local start instructions (`README`/`CONTRIBUTING`); CI pipeline running format/lint, test and build for both API and web; org/staff auth with MFA hooks; tenant-isolated schema/migrations; event creation + guest join/QR/short-code; shared design system scaffold; seeded demo event | CI green on a trivial change; cross-tenant authorization tests pass; owner can create org/event; guest joins without account; dev/test commands recorded in `README` |
| **M2** Competition engine | Immutable ruleset snapshot; round/attempt state machines; scoring module covering every vector in `09`; roster management; judge entry + append-only revisions | All `09` conformance + property tests pass; no direct competitor edit path exists; correction path preserves source history |
| **M3** Organizer/competitor UI | Event wizard, roster, control room, judge entry, waiting room, results, error/empty/offline states | End-to-end create→publish flow works at 360px and desktop; keyboard and screen-reader critical paths checked |
| **M4** Scramble controls + 3D guide | Generator dependency/license review; encrypted payload; role-scoped reveal; extra sequences; print; move guide; applied/checked log; self-scramble gated to casual phone mode | Security tests prove no scramble leakage across API/HTML/cache/logs/public view; sample physical-cube guide usability checked |
| **M5** Advancement, display, history | Standings; tie/percentage preview; commit; public display; organization history; CSV export | Concurrency/idempotency tests pass; public display is read-only and reveals only published fields |
| **M6** Reliability, privacy, pilot | Offline judge-entry queue + printed fallback; backups/restore drill; monitoring; account deletion/export; terms/privacy placeholders reviewed; accessibility audit; security check; pilot checklist | Pilot run with real people and a physical timer; every unchecked `12` item listed; owner sign-off |

### M1 greenfield setup detail (planned, not yet implemented)

- `.gitignore`: standard Java/Gradle-or-Maven + Node/Next.js ignores (build output, `.env*`,
  IDE files, `node_modules`, coverage, local Docker volumes) — no secrets or environment files
  ever committed, per `00` §10 and `04`.
- Pinned toolchain: exact Java LTS version, Spring Boot version, Gradle/Maven wrapper committed;
  exact Node LTS version (`.nvmrc`/`engines` field), package manager lockfile committed for the
  web app. `00` §10: "Pin supported dependency versions at project start and lock them."
  Dependency choices (migration tool, test frameworks, lint/format tools) recorded in
  `DECISIONS.md` when selected.
- Local start instructions: `README`/`CONTRIBUTING` documenting `docker compose up` for Postgres,
  API run command, web dev-server command, migration command, and how to run the seed script —
  so a new contributor or reviewer can run the stack without tribal knowledge.
- CI checks: a pipeline (e.g., GitHub Actions) running, for both API and web, on every push/PR —
  format/lint check, full test suite, and a production build — failing the build on any violation.
  No deployment step in this CI at M1; CI is verification-only until a staging target exists.

## 6. Blockers needing your decision

None block the start of M1. Two items need input before their respective milestone, not now:
scramble generator/library choice (before M4), and target hosting/staging environment and domain
(before M6 staging deployment).

## 7. Recommendation

Still recommending **M1 — Product foundation + project setup** as the first implementation
milestone, now including the greenfield scaffolding above. Waiting for review before writing any
application code.

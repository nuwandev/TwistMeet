# TwistMeet decisions log

This file tracks implementation decisions, defaults applied, and open questions, per
`product-docs/11-ai-build-playbook.md` (M0 exit criteria) and `00-authoritative-build-contract.md` §12.
Update this file whenever an ambiguity is resolved or a behavior deviates from a document.

## Status: M5 complete — Advancement, publishing, public display, history, export

M0–M4 are complete and recorded below unchanged (M4's scramble-secrecy verification gap — error
responses, logs, caches, exports, unauthorized staff views — was closed before M5 started; see
"M5 implementation decisions: M4 scramble-secrecy verification gap, closed" below for the one real
exposure found and fixed). M5 is now also complete: advancement preview/commit (reusing the pure
`AdvancementCalculator`/`RankingService`/`ScoringEngine` unchanged), publish/unpublish with a
rotating public link, unauthenticated public event/standings display, organization event history
with search/filter, and CSV export with CSV-injection mitigation. TNoodle's GPL-3.0 SaaS-vs-
distribution licensing question (OD01 resolution, below) remains an explicit open launch-review
item — not resolved by this milestone, not silently dropped, and the generator/its license were
not touched. See "M5 implementation decisions" below for the full list and what remains deferred
(the optional tie-break attempt, a `PublicSnapshot` cache table, public name masking, advancement
rollback, S12's dedicated tabs, S14's copy-event/retention-deletion).

## Status: M4 complete — Scramble controls and the 3D move guide

M0–M3 are complete and recorded below unchanged. M4 is now also complete: OD01 is resolved (the
official WCA scramble program via `org.worldcubeassociation.tnoodle`, with full primary-source
evidence — see "OD01 resolution" below), versioned scramble generation, encrypted-at-rest vault
storage, per-attempt assignment with controlled extras, role-scoped reveal (a new `SCRAMBLER`
event role joins `JUDGE`/`ORGANIZER`), applied/checked audit with an independent second-checker
toggle, the scramble preparation station screen, print, and the 3D move guide (`cubing.js`'s
`TwistyPlayer`). See "M4 implementation decisions" below for the full list and for what remains
out of scope (advancement commit, publishing, public display, export — still later milestones).

## Status: M3 complete — Organizer and competitor experience

M3 is complete: the event creation wizard, Tournament Control, judge-entry validation/undo, the
competitor waiting room's explicit lifecycle states, connection-loss/offline handling, and the
shared loading/empty/error/retry/offline/confirm component set used across all of them. OD16
(correction-decision authority) is resolved — organizer-only — by explicit instruction; see "M3
implementation decisions" below.

M2 is complete: ruleset versioning/snapshots, round setup/lifecycle, the attempt state machine
(judge and self-timed modes), roster remove-vs-withdraw, judge result entry with append-only
revisions, authorized correction requests/decisions, the pure scoring engine (full
conformance-vector coverage), and the standings/judge/correction-queue UI. See "M2 implementation
decisions" below for what M2 adds.

Two M1 follow-up passes since then, both recorded further down this file: closing a residual
auth-enumeration/account-hijack gap by redesigning registration as double opt-in, and modernizing
the stack to Java 25 LTS / Spring Boot 4.1.1 / PostgreSQL 18.6 / Node 24 LTS / Next.js 16.3.8.

## Stack decision

The repository contains no existing code, build files, or conventions to preserve. Adopting the
reference stack from `00-authoritative-build-contract.md` §10 as-is, no deviation to document:

- API: Spring Boot (Java), modular monolith, PostgreSQL, Flyway/Liquibase migrations.
- Web: Next.js/React, TypeScript.
- Real-time: SSE or WebSocket, event-scoped, authenticated.
- Local dev: Docker Compose. No Redis at launch.
- Object storage for generated print assets: deferred until needed; generate on demand first.

## Defaults applied from the contract (not re-litigated)

- Default puzzle 3×3×3, default round Single/Ao5, default mode Physical Timer + Judge Entry,
  default scramble policy staff-prepared (`00` §4).
- Canonical time storage: integer milliseconds, no floats (`00` §4, `08`, `09`).
- Tie-break: average then best single, then shared rank (`00` §4, `09`).
- Correction policy: one replacement per attempt by default, organizer-configurable before
  registration opens (`00` §4).
- Event visibility private/unlisted by default (`00` §4).
- Timer inspection default 15s casual mode with 8s/12s warnings (`00` §4).
- Advancement ties: include all entrants tied at the boundary by default (`09`).

## Product name and branding — unresolved

"TwistMeet" is explicitly a provisional working name, not a cleared brand or domain (`00` title:
"Working name only"; package `README`: "a provisional working name"). **This is not decided.** Do
not present "TwistMeet" to end users as a final product name, and do not register a domain,
trademark, or app-store listing under it. Until a name is chosen:

- Keep every user-visible occurrence of the product name (page titles, email sender name/subject
  lines, footer, public display header, exported-file headers, error pages) driven from a single
  configurable branding/theming value (e.g. one config entry or environment variable), not
  hard-coded in multiple templates/components. This lets the name change later without touching
  application logic.
- "TwistMeet" may still be used as the internal repository/project name and in code comments,
  package names, and this documentation, since those are not end-user-facing branding.
- See the open decisions register below (OD02) for ownership and timing.

## Open decisions register

Every unresolved product, technical, legal, brand, and operations decision found across the full
`product-docs/` package, not invented and not silently defaulted. Each row names the decision, its
category, who owns resolving it, the latest milestone it must be resolved by, whether it blocks
that milestone, and a safe default **if the documents actually support one** — a safe default
here is a candidate for the owner to confirm, not a decision already made. Items with no safe
default must not be guessed.

**OD01 resolved at M4 (see "OD01 resolution: scramble generator and 3D-guide library" below) —
removed from this table.**

| ID | Decision | Category | Owner | Needed by | Blocks that milestone? | Safe default (if any) | Source |
|---|---|---|---|---|---|---|---|
| OD02 | Final product/brand name and domain | Brand | Product owner | Before any public-facing domain/trademark/app-listing use (not before Phase 3) | No — does not block M1–M6 internal/staging work | Keep "TwistMeet" as internal-only working name; keep all user-visible branding in one configurable value (see note above) | `00` title; package `README` |
| OD03 | Target hosting/cloud provider, domain registrar, environment topology | Technical/Operations | Product owner + engineering | M6 (staging deployment) | **Yes, for M6** — not for M1–M5 | None named in the docs beyond "environment separation (local, staging, production)" | `00` §10 |
| OD04 | Outgoing transactional email provider (verification, password reset, notifications) | Technical/Operations | Engineering | M1 (auth flows use email) | Partially — local/dev auth flows can proceed with a dev mail-catcher; a production-suitable choice is needed before M1's email-verification/reset behavior is pilot- or staging-ready | Use a local dev mail catcher (e.g. log-to-console or a dev SMTP sink) until a provider is chosen; no production default named in the docs | `08` auth endpoints; `12` "outgoing email" |
| OD05 | Migration tool: Flyway vs. Liquibase (the stack decision above lists both as acceptable) | Technical | Engineering | M1 | No, if a default is taken — but the choice must be made explicitly at M1, not left open indefinitely | Flyway (common default pairing with Spring Boot); not yet confirmed by product owner | `00` §10 "Use migrations, never Hibernate schema auto-update"; this file's Stack decision section |
| OD06 | Privacy/legal reviewer identity and target launch jurisdictions | Legal | Product owner | Before any pilot with real participants (as early as M6) and before public beta | **Yes, for M6 pilot and for public beta** | None — `05` explicitly requires jurisdiction-specific legal advice, not an invented default | `04` "Publish privacy notice... These require legal review"; `05` Launch gate, Global rollout considerations |
| OD07 | Children's/youth-use policy: minimum age, parental consent process, data handling for minors | Legal | Product owner + legal reviewer | Before any pilot that may include minors | **Yes, if minors are expected to participate** — unresolved until then | None — `04` explicitly says "obtain jurisdiction-specific advice on children's privacy... before worldwide launch"; cannot be invented | `04` "Privacy and safeguarding" |
| OD08 | Display-name content policy: profanity/reserved-word filter list and enforcement strictness | Product/Technical | Product owner | M3 (registration UI) | No, if a minimal conservative default is used, but the actual word list/policy is undecided | A small, conservative blocklist with organizer override, per the spec's own caution not to overblock names | `07` P02 "reserved profanity filter is configurable and should not overblock names" |
| OD09 | Which languages to translate critical UI into for Phase 3 | Product | Product owner | Phase 3 / public beta | No — English-only is the default until Phase 3 | English only, per `03` "First language is English; design localization from day one" (design-ready, not translated) | `01`, `03`, `05` |
| OD10 | Pricing model: remain free or introduce pricing | Product/Business | Product owner | Not before public beta | No | Free during validation, per `05` "Keep entry free during validation unless usage and support costs justify a price" | `05` Global rollout considerations |
| OD11 | Named incident owner, support contact, and status-page tool | Operations | Product owner | Before public beta; informally useful earlier for pilots | **Yes, for public beta sign-off**; does not block M1–M5 | None — a specific person/contact must be named, cannot be invented | `04`, `05`, `12` Launch gate |
| OD12 | Subprocessor list (hosting, email, error-reporting, and other vendors) | Legal/Operations | Product owner | Before public beta | **Yes, for public beta sign-off**; depends on OD03/OD04 being resolved first | None | `04`, `12` |
| OD13 | Expected pilot concurrency figure, to drive the "load test at 2× expected concurrency" exit criterion | Technical/Operations | Product owner + engineering | M6 | **Yes, for M6's load-test exit criterion**; does not block M1–M5 | None stated; `05` only says "revisit after pilot usage" | `01` Non-functional requirements; `12` Reliability/accessibility checklist |
| OD14 | Object storage provider, if/when "generate on demand" for printable sheets proves insufficient | Technical | Engineering | Only if/when the generate-on-demand default proves insufficient, likely around M4 | No — `00` §10 explicitly permits deferring via generate-on-demand | Generate on demand, no object storage at launch (already the operating default) | `00` §10 |
| OD15 | Neon PostgreSQL 18 project selection: create a new one or designate an existing non-PG18 project to upgrade | Technical/Operations | Product owner + engineering | Whenever Neon (vs. local/Docker Postgres) is actually wanted for a shared/deployed environment | No — local Docker/CI Postgres 18 remains the default and is unaffected | None — standing constraint forbids creating/altering/deleting a Neon project without this decision being made first | This file, "M1 follow-up: stack modernization," "Neon findings" |

None of the "safe default" values above are treated as decided — they are the owner's starting
point to confirm or override. Items marked "None" have no documented default and must not be
guessed; they stay open until the named owner resolves them.

**OD14 confirmed still unneeded at M4:** the scramble preparation station's "print current batch"
(`07` S07) returns a plain JSON list of `{attemptNumber, notation}` that the web app renders as an
HTML print view (browser `window.print()`) — no PDF/image generation, no persisted printable
asset, no object storage. The "generate on demand" default (`00` §10) continues to hold exactly as
it did before M4.

**OD05 resolved at M1 (explicit instruction):** migrations use **Flyway** (`org.flywaydb:flyway-core`
+ `flyway-database-postgresql`), not Liquibase. The stack decision above is left as written
(listing both) for the M0 historical record; this line is the actual, final choice. Migrations
live in `api/src/main/resources/db/migration`; `spring.jpa.hibernate.ddl-auto=validate` in every
profile so Hibernate can never silently patch the schema (00 §10).

**OD04 partially resolved at M1 (explicit instruction):** local and CI development use a **local
mail catcher** (`maildev/maildev` in `docker-compose.yml`, SMTP on 1025, web inbox on 1080) for
all outgoing mail (registration email verification). **No production email provider has been
chosen** — `MailService` is written against the generic `JavaMailSender` interface specifically so
a provider choice later (OD04's production half) only changes configuration, not calling code.
Do not point `TWISTMEET_MAIL_HOST`/`TWISTMEET_MAIL_PORT` at a real provider without that review.

## M1 implementation decisions

Additions and simplifications made while building M1, per `00` §12 ("coding agent must choose the
safer simpler behavior, document the assumption... add acceptance coverage"). None of these change
a product requirement or default; they are implementation choices the specs left unspecified.

- **Route 50 (`08`'s `GET /guest/.../current-scramble`) is deliberately left unresolved and
  unimplemented.** `08` abbreviates this path with an ellipsis and it belongs to the self-scramble
  casual-phone-mode feature, which is explicitly M4 scope (scramble vault, not built in M1). See
  `TRACEABILITY.md` section G, row 50, which now also flags this as an open implementation
  question for whoever builds M4 (candidate path given as an example only, not a decision).
- **Minimal roster-read addition:** `GET /api/v1/events/{eventId}/entrants` (organizer/staff-only)
  was added even though `08` does not enumerate a roster-list endpoint, because the organizer
  otherwise has no way to see who has joined — see the code comment on `RosterController` and
  `TRACEABILITY.md` section G. It exposes only data the organizer already has access to by other
  means (each entrant via other calls); it does not add a new capability.
- **CSRF cookie issuance:** Spring Security's `CsrfFilter` only stores a deferred token supplier on
  the request; nothing in a pure JSON API ever resolves it, so the `XSRF-TOKEN` cookie would never
  actually be issued without help. Added `CsrfCookieFilter` (the pattern Spring Security's own
  SPA/Angular CSRF guide documents) to force resolution on every request. Verified manually with
  curl end-to-end (register → login → cookie-and-header logout → 401 on subsequent `/me`) before
  trusting it in the automated test suite.
- **Edit lock simplification:** `08` allows editing event config until entrants are registered,
  tracked separately from lifecycle state. M1 takes the simpler, safer rule: editable only while
  `state == DRAFT`. Documented here rather than inventing a separate "has entrants" flag.
- **Join code stored twice (plaintext + SHA-256 hash):** a join code is "invitation convenience,
  not a password" (`00` §7) and the organizer must be able to redisplay it (QR/screen) at any time,
  so it cannot be purely one-way hashed like a password. The hash column matches `04`'s named
  entity field and is what the join endpoint actually queries against guest input; see the code
  comment on `Event.java`.
- **Guest credential TTL:** set to 30 days from issuance as a placeholder. The real retention
  period for guest sessions is a privacy/retention policy question (`04` "Define retention periods
  for abandoned guest sessions") that has not been decided — tracked as a gap, not a new OD item,
  since it only affects when an already-working credential stops working.
- **In-memory rate limiting:** `SimpleRateLimiter` (login, join-code attempts) is per-instance,
  not shared across replicas. Adequate for a single-instance M1/pilot deployment; flagged in its
  own class comment as needing a shared store before multi-instance deployment (consistent with
  the "no Redis at launch" stack decision — revisit together).
- **Test infrastructure — real Postgres, not Testcontainers:** this sandbox session has no Docker
  daemon available (`docker run hello-world` fails to reach the Docker socket), so integration
  tests run against a real PostgreSQL instance via JDBC (local `postgresql` service here; a
  `postgres:` service container in CI — see `.github/workflows/ci.yml`) rather than Testcontainers.
  Both the local and CI setups configure the same `TWISTMEET_DB_URL`/`_USER`/`_PASSWORD` variables,
  so the test code itself does not know or care which one is running. Tests reset the schema via
  `Flyway.clean()` + `Flyway.migrate()` before each test method for isolation.
- **Display-name hygiene vs. content policy:** `DisplayNamePolicy` only rejects control characters
  and `<`/`>` as a safety floor. The configurable profanity/reserved-word filter itself is OD08,
  still unresolved — not implemented in M1.
- **CORS:** the web app and API are separate origins (`:3000`/`:8080` in dev, and likely separate
  hosts later). Cookie-based auth across origins needs explicit CORS with credentials allowed;
  added a `CorsConfigurationSource` restricted to the single configured web origin (never `*`,
  which Spring rejects alongside `allowCredentials`). Found this gap by actually running the web
  app against the API in a browser (Playwright), not from the automated JUnit suite alone — the
  JUnit tests talk to the API directly and never exercised cross-origin behavior.
- **A real frontend bug found and fixed during M1 browser testing:** `dashboard/page.tsx`'s form
  handlers read `e.currentTarget` *after* an `await` to call `.reset()`. React clears
  `currentTarget` once an event handler yields past its dispatch phase, so this intermittently
  threw and was swallowed by the `catch` block — the organization/event had actually been created
  successfully (the API returned 201), but the UI reported "Failed to create organization" and
  never revealed the next step. Fixed by capturing the form element in a local variable before
  the first `await`. This was only caught by driving the real app with Playwright end-to-end,
  not by the API's own test suite or by `next build`/`next lint`, which both passed throughout —
  a concrete instance of why "build passes" isn't the same as "the feature works," per the
  playbook's own caution against claiming success from build/lint alone.

## M1 review findings fixed

Three findings from the M1 review, addressed on the same branch before starting M2:

1. **Registration revealed whether an email was already registered.** `POST /auth/register`
   previously returned `201` with the new account's data for a fresh email and `409
   EMAIL_IN_USE` for an existing one — two distinguishable outcomes that let a caller enumerate
   registered emails (12 SP04 "generic errors avoid account enumeration"). Fixed: both cases now
   return the identical `202 Accepted` with a generic `{email, message}` body containing no
   account fields (no `id`/`emailVerified`/`createdAt`). The password is always hashed regardless
   of branch, which removes the single cheapest timing tell (skipping bcrypt entirely when the
   email already exists) — **but this is not a constant-time guarantee.** The new-account branch
   also inserts a `User` row, inserts an `EmailVerificationToken` row, and calls `MailService`,
   so the two branches still take measurably different time overall; a caller timing many
   requests precisely enough could in principle still infer existence. Closing that gap for real
   would need an actual constant-time design (always doing equivalent DB/mail work either way, or
   moving the email send off the request path) that has not been built or measured here — not
   claimed as done. A duplicate registration attempt also cannot take over the existing account —
   the original password keeps working and the "new" password submitted on the duplicate attempt
   does not. Response status and body for both branches are compared directly in
   `AuthFlowTest.registrationDoesNotRevealWhetherTheEmailIsAlreadyRegistered`; this status/body
   equivalence is what is actually verified, not timing.

   **Known residual signal (not fixed, documented instead of hidden):** registration never
   overwrites an existing account's password — it must not, or a duplicate-registration call
   would be an account-takeover vector. One consequence: if a caller registers with a guessed
   password and then immediately logs in with that same password, login succeeds only when the
   email was genuinely new (so that password is the one just stored) and fails with the ordinary
   `INVALID_CREDENTIALS` wrong-password response otherwise. That second (login) call, not
   registration, is where the distinguishable outcome appears, and it costs the caller a correct
   guess plus two separate rate-limited requests per email probed — a much weaker channel than
   the original single-call 409 vs 201, but a real one, not eliminated. Verified directly against
   the running app (register → login, both call sites) with Playwright for three cases: a new
   email, a duplicate with the same password, and a duplicate with a different password — in all
   three, `POST /auth/register` itself returns the identical `202` and body; only the *subsequent*
   `POST /auth/login` call's status differs (`200` vs `401`), and only because the password
   genuinely does or doesn't match — the same way login behaves for any account.
2. **No rate limiting on registration or email verification.** Both are now rate-limited by
   client address via the existing `SimpleRateLimiter` (same mechanism as login/join): 8
   registration attempts and 10 verification attempts per 15 minutes. `SimpleRateLimiter` gained
   a `clearAll()` test hook, called from `AbstractIntegrationTest`'s per-test reset, because it is
   a singleton bean whose state would otherwise leak across test methods within one Spring
   context (unlike the database, which Flyway already resets per test) — without that, these new
   rate-limit tests would be flaky depending on how many other tests had already registered from
   the same loopback address. Tested in `AuthFlowTest.registrationIsRateLimited` and
   `.emailVerificationIsRateLimited`.
3. **`POST /auth/email/verify` was undocumented.** It was implemented in M1 (and already
   rate-limited as of this pass) but never added to `08-data-api-contract.md` or
   `TRACEABILITY.md`. Fixed: added to both — see `08`'s "Identity and organization" endpoint list
   and `TRACEABILITY.md` row 65. While documenting it, also found and fixed that it was missing
   from the CSRF-ignore list in `SecurityConfig` (it does not ride an existing session cookie any
   more than register/login do — a user clicking a link in their email client has no prior CSRF
   cookie to echo back), so it would have 403'd in practice; added alongside register/login/join.

## M1 follow-up: double opt-in registration 

**Finding.** After the M1 review's non-enumeration fix, a *newly registered but unverified*
account could still log in successfully — registration created a `User` row with the
caller-submitted password immediately, before the email link was ever clicked. Logging in with
that account's email and the *wrong* password returned `401`, while logging in for an email that
had genuinely never been registered also returned `401` with the same body — so unverified
accounts didn't reopen the original enumeration gap directly. The real problem was worse: because
the password was collected at registration, an attacker could register *someone else's* email with
an attacker-chosen password, and if the mailbox owner later clicked the verification link without
reading carefully, they would activate an account whose password the attacker already knew.

**Decision: double opt-in, password set only at verification, never at registration.**
`POST /auth/register` now collects only email + display name and never touches the `users` table;
it upserts a `pending_registrations` row (email, display name, a hashed single-use token, an
expiry) and emails the raw token in a link. No `User` row, and therefore no account to log into,
exists until `POST /auth/email/verify` is called with that token **and** a password — whoever
submits that call is the one who ends up controlling the password, which is the actual security
property needed (not just "don't enumerate accounts," but "don't let an attacker pre-load a
password onto an account the victim will activate"). A second registration for the same
still-unverified email **reissues** (overwrites) the pending row's token rather than creating a
second one, so an earlier attacker-sent link stops working the moment the real owner re-registers
— the attacker cannot keep a stale link alive waiting for the victim to click an old email.
Registration and verification responses remain structurally identical regardless of branch taken
(same status, same shape, no account-existence field), and both endpoints are rate-limited (register:
8/15min by client address, plus a silent per-email cap of 5/hour on actually sending mail, which
never changes the HTTP response — it only bounds inbox spam from repeated registration attempts
against one address; verify: 10/15min by client address). This never overwrites an existing
*verified* account's password — the `pending_registrations` upsert only ever touches the unverified
pending record, never a `User` row, so an already-active account is untouched by any number of
re-registration attempts against its email.

Tested in `AuthFlowTest`: an unverified registration cannot log in and is indistinguishable from no
account at all (`unverifiedRegistrationCannotLoginAndIsIndistinguishableFromNoAccountAtAll`); a
second registration invalidates the first token and the hijack scenario cannot succeed — the
account ends up with the *second* registration's display name and whatever password was typed into
the *second* verification call, never the first/attacker's
(`aSecondRegistrationForAnUnverifiedEmailInvalidatesTheFirstLinkRatherThanStealingIt`); tokens are
single-use; both endpoints are rate-limited; and registration responses stay identical for a new
vs. an already-verified email. Verified end to end with a real browser (Playwright): registration
→ check-your-email → click link → set password → land on dashboard → log out → log back in with
the chosen password, and separately, the explicit hijack scenario (attacker registers victim's
email → victim re-registers, superseding the attacker's token → the attacker's stale link, even
submitted with a password, fails `400 TOKEN_INVALID` → the victim's own current link succeeds and
creates the account with the victim's own password and display name → the attacker's password
never works for login).

`EmailVerificationToken`/`EmailVerificationTokenRepository` are removed, replaced by
`PendingRegistration`/`PendingRegistrationRepository` (migration `V2`). `MailService` became an
interface (`SmtpMailService` for real sending, `CapturingMailService` for tests, selected by Spring
profile) so tests can read back the real verification token instead of parsing a sent email body.

## M1 follow-up: stack modernization to a current supported baseline

Per explicit instruction, the greenfield stack (nothing here predates this repo, so there is no
compatibility tail to preserve) was moved to current, non-beta, mutually compatible releases,
verified against each project's own release notes/compatibility matrix rather than assumed:

| Component | Was | Now | Notes |
|---|---|---|---|
| Java | 21 (LTS) | **25 LTS** (toolchain; patch resolved by whichever JDK 25 build the Foojay resolver or local install provides) | Spring Boot 4.1's minimum is Java 17; 25 has first-class support. Gradle wrapper bumped to 9.8.0, the first Gradle line with full Java 25 toolchain + daemon support; `settings.gradle.kts` adds the `foojay-resolver-convention` plugin so Gradle can auto-download a matching JDK on any machine (including CI) that doesn't already have one. |
| Gradle | 8.10.2 | **9.8.0** | Required for Java 25 support (9.1.0 is Gradle's stated minimum; 9.8.0 was current at the time of this change). |
| Spring Boot | 3.3.4 | **4.1.1** | Current stable minor; required migration work below. |
| PostgreSQL (Flyway's PG-specific module, pgjdbc) | managed by Boot 3.3 | **Flyway 12.4.0 / pgjdbc 42.7.13**, both via Spring Boot 4.1.1's own dependency-management BOM (not hand-pinned) | Flyway 12.4.0 has verified PostgreSQL 18 support (older Flyway lines, below ~11.20.3, do not); pgjdbc 42.7.13 is the current release and postdates the 42.7.12 security fix (CVE-2026-54291), so nothing older than that should ever be used. |
| PostgreSQL (database) | 16 (`postgres:16-alpine`) | **18.6** (`postgres:18.6-alpine`) | `docker-compose.yml` and the CI `postgres:` service image both bumped. |
| Node.js | 22.x | **24.x LTS** ("Krypton"; `.nvmrc` pins `24.21.0`) | |
| Next.js | 14.2.35 | **16.3.8** | See CVE note below — not 16.2.x as originally targeted. |
| React / ReactDOM | 18 | **19.3.0** | Next.js 16 recommends React 19 (18 is supported but deprecated, and will stop being supported in Next 17). |
| TypeScript | ^5 (unpinned) | **5.9.3** (pinned) | Deliberately *not* bumped to TypeScript 7 (the new Go-based compiler, currently the npm `latest` tag) — that is an unrelated, major, independent tooling change outside what was asked, and 5.9.3 is still a fully current, supported 5.x release. |
| ESLint | ^8 | **^9.39.5** | `eslint-config-next@16.3.8` requires ESLint ≥9. Not bumped to ESLint 10 (also newly current) for the same "don't blindly upgrade unrelated/unverified dependencies" reason — 9.39.5 is `eslint-config-next`'s own stated minimum-plus-current-patch line. |

**Deviation from the literal instruction (Next.js 16.2 → 16.3.8), documented per this file's own
rule for approved deviations.** `npm audit` on the originally pinned `next@16.2.12` reported a
**critical** advisory (GHSA-p293-qw3h-jr36 and related: unauthenticated RCE on Windows-hosted
servers, in the AVIF image-optimization path, and in `next/og`'s `ImageResponse`) affecting every
Next.js release from `9.3.4-canary.0` through `16.3.5` inclusive — meaning 16.2.x is affected and
only fixed starting at 16.3.6+. Shipping a pinned version with a known critical RCE just to match
the literal "16.2" instruction would be worse than deviating from it, so this upgrades to **16.3.8**
(the current stable release at the time of this change) instead. Everything else about the
"16.2" request — current, non-beta, React 19-compatible — still holds; only the exact minor line
changed, for a security reason stronger than the original instruction anticipated.

**Known accepted risk, not fixed (tracked, not hidden):** `npm audit` separately reports a *high*
severity advisory in `braces` (GHSA-vfj7-8cjw-p6xm, a regex stack-exhaustion DoS), reached
transitively through `eslint-config-next@16.3.8 → @next/eslint-plugin-next → fast-glob →
micromatch → braces@3.0.3` — which is already the latest published `braces` version; no fixed
release exists upstream as of this writing. `npm audit fix --force`'s only suggested remedy is
downgrading `eslint-config-next` all the way back to `14.2.35`, which would silently undo the
Next.js version bump (and reintroduce the critical RCE above) to dodge a lint-tooling-only,
dev-time, non-shipped dependency's unresolved advisory — a clearly worse trade, so not taken. This
is a devDependency of the lint tool only; it is never bundled into the built app or reachable by
any request the running service handles. Revisit when `braces`/`micromatch`/`fast-glob` publish a
fix.

### Spring Boot 3→4 migration notes (what actually had to change, beyond version numbers)

Spring Boot 4 is not a drop-in version bump; the following were required for the existing M1 code
to keep working, found by actually compiling and running the test suite against 4.1.1, not assumed:

- **Flyway auto-configuration moved behind a dedicated starter.** Adding `flyway-core` directly no
  longer triggers Spring Boot's Flyway auto-configuration in Boot 4; `spring-boot-starter-flyway`
  must be added instead (it pulls in `flyway-core` at Spring Boot's managed version).
- **Jackson 3 is the new default**, under the `tools.jackson` groupId/package — not `com.fasterxml.jackson`.
  `ObjectMapper` is also no longer freely constructible via `new ObjectMapper()`; the
  Jackson-3-idiomatic construction is `JsonMapper.builder().build()`. `SecurityConfig`'s injected
  `ObjectMapper` and every test's local JSON parsing (`AuthFlowTest`, `CrossTenantAccessTest`,
  `EventLifecycleAndJoinTest`, `RosterAuthorizationTest`, `TestApiClient`) were updated accordingly.
- **Spring Security 7's `DaoAuthenticationProvider` constructor changed**: it now takes the
  `UserDetailsService` directly (`new DaoAuthenticationProvider(userDetailsService)`) instead of a
  no-arg constructor plus `setUserDetailsService(...)`; `setPasswordEncoder(...)` is still a setter
  called afterward. Updated in `AuthManagerConfig`.
- **`TestRestTemplate` moved out of `spring-boot-test`** into a new `spring-boot-resttestclient`
  starter (package `org.springframework.boot.resttestclient`), which is no longer autowired by
  `@SpringBootTest` alone — test classes need `@AutoConfigureTestRestTemplate`
  (`org.springframework.boot.resttestclient.autoconfigure`), and the starter itself needs
  `spring-boot-restclient` on the classpath for its `RestTemplateBuilder`/`RestTemplateBuilder`-backed
  bean. Both added to `api/build.gradle.kts`; `@AutoConfigureTestRestTemplate` added to
  `AbstractIntegrationTest`.
- **Apache HttpClient5's cookie management conflicted with this test suite's own cookie-jar
  design.** `TestApiClient` deliberately manages each simulated user's cookies itself (register,
  login, guest-join, CSRF double-submit — all tested like a real browser would see them); Spring
  Boot 4's `RestTemplateBuilder` auto-detects HttpClient5 on the test classpath (needed, as before,
  to avoid `HttpRetryException` on a streamed POST that gets a non-2xx response) and builds it with
  Apache's own cookie management enabled by default. That cookie store lives on the one underlying
  `HttpClient`, shared by every `TestApiClient` instance in a test via the single injected
  `TestRestTemplate` bean — so one simulated user's session cookie could silently leak into a
  different, supposedly-independent `TestApiClient` in the same test, intermittently, depending on
  test/method order. Fixed with a test-only `RestTemplateBuilder` bean
  (`StatelessHttpClientTestConfig`) that explicitly disables Apache's cookie management
  (`HttpClientBuilder::disableCookieManagement`), restoring `TestApiClient`'s manual `Cookie` header
  as the only cookie state involved — this is a test-harness-only fix; nothing about it touches how
  the running application itself handles cookies.
- **`google-java-format` (via the Spotless Gradle plugin) needs JDK 16+ compiler-internals access**
  that is denied by default on modern JDKs; `api/gradle.properties` now sets `org.gradle.jvmargs`
  with the `--add-exports`/`--add-opens` flags Spotless's own formatter step needs to run at all.
  Pinned Spotless's `googleJavaFormat` version to **1.30.0** — Spotless 8.10.x's own stated
  validated default for JVM 21+ (and its stated minimum for JVM 25+); newer `google-java-format`
  releases exist but are not yet validated against this Spotless version, so not used.
- **Next.js 16 removed the `next lint` command** (deprecated since 15.5); `web/package.json`'s
  `lint` script now runs `eslint .` directly, backed by a new flat-config `web/eslint.config.mjs`
  (replacing the removed `.eslintrc.json`) that re-exports `eslint-config-next`'s flat config array.
  The upgraded `eslint-plugin-react-hooks` (bundled transitively via `eslint-config-next`) added a
  new `react-hooks/set-state-in-effect` rule that flags the pre-existing, intentional
  fetch-on-mount/fetch-on-change pattern in `web/src/app/dashboard/page.tsx` and
  `web/src/app/events/[eventId]/page.tsx` (an effect calling an async function that eventually
  calls `setState`); suppressed with a targeted `eslint-disable-next-line` and a comment at each of
  the three call sites rather than restructuring working, already-tested data-loading code for a
  new lint rule's heuristic.
- **Next.js 16 auto-generates `AGENTS.md`/`CLAUDE.md` on `next dev`** by default (a new
  AI-agent-onboarding feature). Disabled via `agentRules: false` in `web/next.config.mjs` — this
  repo already has its own agent-facing docs (`DECISIONS.md`, `TRACEABILITY.md`, `M0-REPORT.md`)
  and doesn't want a second, auto-generated set appearing locally on every `next dev` run.

### Neon findings (not resolved — no suitable project exists yet)

Per instruction, inspected the connected Neon account's existing projects with the read-only Neon
MCP tools (`list_projects`); **no project was created, altered, or deleted, and no connection
string or other secret was requested or exposed** — `get_connection_string` is explicitly unavailable
in read-only mode regardless. Five projects exist in this account, running PostgreSQL 14, 16, 17,
17, and 17 respectively; **none runs PostgreSQL 18**, and none is named or otherwise identifiable
as belonging to this project. So there is currently no existing Neon project this app could point
at without creating a new one — which this task was explicitly told not to do. Neon connection
configuration therefore remains environment-variable-only and undecided in practice (the existing
`TWISTMEET_DB_URL`/`_USER`/`_PASSWORD` variables already support pointing at a Neon host once a
project exists; see the new README note under "Using Neon instead of local/Docker Postgres" for
the pooled-vs-direct-connection guidance to follow when one is created). **This is an open decision
(OD15 in the register below)**, not a silent gap: creating (or choosing an existing) Neon
PostgreSQL 18 project for this app is left to the product owner/engineering lead, outside this
session's standing constraints.

## M2 implementation decisions

Additions, conflict resolutions, and simplifications made while building M2 (competition engine),
per `00` §12. None of these change a product requirement or default without a documented reason;
they are either genuine spec conflicts (flagged as such) or implementation choices the specs left
unspecified.

- **Genuine spec conflict — correction-decision authority — resolved at M3 (explicit
  instruction): organizer-only.** `00` §5 and `08`'s correction-decision endpoint both say
  decisions are **organizer-only**. `02-rules-and-integrity.md`'s narrative prose instead
  describes "judge or organizer" as able to decide a correction request. These directly
  conflict. Per `00` §12's own precedence rule (the authoritative contract and its
  directly-referenced data/API contract outrank looser narrative text elsewhere), and per
  explicit instruction at M3 confirming this reading, `CorrectionService.decide()` restricts
  decisions to **Organizer/Owner only** — a judge cannot decide a correction, including one on
  an attempt they themselves judged, since judges already enter results and allowing the same
  judge to also decide corrections on their own entries would weaken the separation the
  correction workflow exists to provide. **OD16 is now closed** (previously open in this
  register, removed from the open-decisions table below). Enforcement: `TenantAccessService
  .requireOrganizer()` on the server (tested in `CorrectionFlowTest
  .onlyOrganizerCanDecideACorrectionNotJudgeOrStranger`, which asserts a judge gets `403`, not a
  hidden button); the web correction queue (`events/[eventId]/corrections`) additionally reads
  the caller's own role via `GET /events/{eventId}/my-role` and hides the decision controls
  entirely for a non-organizer viewer, replacing them with "Only organizers can decide
  correction requests. You can still view them." — consistent with `07`'s own rule that access
  control must never be UI-only, since the server check is what actually enforces this and the
  UI hiding is purely to avoid presenting a control that would just 403.
- **Round creation is permitted in DRAFT, REGISTRATION_OPEN, and REGISTRATION_LOCKED event states,
  not read literally as "draft event only."** `08` describes round setup as a draft-event action.
  Taken literally, that would make it impossible to ever set up rounds for an event that has
  already opened registration (which is the normal flow — registration opens before the event day,
  rounds are set up ahead of or during the event). `RoundService.create()`/`update()` therefore
  reject only `LIVE`/`COMPLETE`/`CANCELLED` events, not `REGISTRATION_OPEN`/`_LOCKED`. Documented
  here rather than silently reinterpreting the contract; this reading is consistent with `07`'s
  screen spec, which shows round setup happening on the same event-detail screen used throughout
  registration.
- **Judge role modeled as a new event-scoped `EventRole.JUDGE`, independent of org membership.**
  M1 only has two org-wide roles (`OWNER`, `ORGANIZER`), both with full access to every event in the
  organization. `00`/`08` require a judge role that can enter results and view rosters/standings
  for one event but has none of an organizer's configuration/roster-management/correction-decision
  authority, and a judge need not be an org member at all (e.g. a volunteer judge for one event).
  Added `EventStaffAssignment` (eventId, userId, role, assignedAt) as its own table/entity,
  deliberately separate from `OrgMembership`, with minimal CRUD endpoints
  (`EventStaffController`, organizer-only to assign/remove) modeled on the M1 precedent of adding
  a minimal roster-list endpoint not literally enumerated in `08`. `TenantAccessService` gained
  `requireJudgeOrOrganizer()`, which accepts org organizer/owner OR an `EventStaffAssignment` with
  role `JUDGE` for that specific event; both still 404 (not 403) for a user with no relationship
  to the event's org or event, preserving the existing anti-enumeration pattern.
- **Self-timed (phone-casual) submissions auto-accept; no judge confirmation step.** `00` §3
  describes phone-casual mode as judge-free by design (the whole point is no judge is present), but
  does not explicitly state whether a submitted self-timed result needs any further confirmation
  step before becoming official. Since there is no judge in this mode, requiring confirmation would
  need some other actor (the organizer, after the fact) to confirm every single self-timed result,
  which defeats the mode's purpose of being casual and low-friction. `AttemptService.submitSelfTimed()`
  therefore moves the attempt directly to `ACCEPTED` on submission — the safer/simpler default per
  `00` §12. The correction-request flow remains available afterward as the mechanism for a
  competitor to flag a mistake, which is the documented recourse for errors in this mode.
- **`AdvancementCalculator` (top-N / top-percent) is built and unit-tested but deliberately not
  wired to any controller or round-lifecycle transition.** `09` specifies the advancement math;
  `00`'s task scope for this milestone explicitly excludes "advancement" as an operational feature
  (the actual act of advancing entrants between rounds). Building the pure calculation now (it has
  no dependency on anything excluded) while leaving it unconnected avoids re-deriving the same
  tie-inclusion logic again at the milestone that actually wires it up, without claiming advancement
  itself is implemented. Not claimed as a working feature anywhere in `TRACEABILITY.md`.
- **Roster remove vs. withdraw, split on whether any attempt row exists for the entrant.**
  `00`/`08` distinguish "remove" (before attempts — the entrant was never really part of the
  competition) from "withdraw" (after attempts exist — the entrant's existing results must be
  preserved for scoring/audit integrity, not erased). `RosterService.remove()` is a hard delete,
  permitted only when `!attemptRepository.existsByEntrantId(entrantId)`; otherwise it returns `409`
  with a message directing the caller to withdraw instead. `withdraw()` is a soft, idempotent state
  change (`EventEntrant.withdraw()`) that leaves all existing attempts and results intact. This
  mirrors the existing append-only philosophy already used for `AuditEvent`/`ResultRevision`:
  once competition data exists, nothing destroys it outright.
- **Judge result entry always creates a `ResultRevision`, including the first entry for an
  attempt.** `00` §9 requires an immutable audit history of every result change. Rather than
  special-casing "first entry, no revision" vs. "correction, revision," every call to
  `AttemptService.recordJudgeResult()` inserts one `ResultRevision` capturing the previous (possibly
  null/PENDING) and new raw time/penalty/status, so the full history — including the very first
  entry — is reconstructible from one table with no special case. Competitors have no endpoint that
  can write to `Attempt`'s result fields directly; only `recordJudgeResult` (judge/organizer) and
  `submitSelfTimed` (the entrant's own self-timed attempt only) can.
- **Self-timed resubmission is idempotent on identical payload, `409 DUPLICATE_ATTEMPT` on a
  different payload to an already-`ACCEPTED` attempt.** Neither document specifies retry semantics
  for a competitor's own submission (e.g. a flaky connection causing a double-tap). Treating an
  identical repeat as a no-op success avoids punishing a harmless retry, while a *different* payload
  to an already-accepted attempt is rejected rather than silently overwritten — competitors must not
  be able to directly edit an official result (`00` §9), and allowing a changed resubmit to silently
  replace the first would violate that even in self-timed mode. A genuine mistake in self-timed mode
  goes through the correction-request flow instead, same as judge mode.
- **Optimistic-concurrency (`expectedVersion`) required on judge-result entry and correction
  decisions, returning `409 STALE_VERSION` on mismatch.** `00`/`08` require safe concurrent editing;
  JPA `@Version` (already the M1 pattern for `EventEntrant` etc.) is reused rather than introducing
  a separate idempotency-key store, since the relevant operations are inherently state-transition
  checks (a judge correcting a result they just viewed; an organizer deciding a request) rather than
  side-effecting network calls that need deduplication independent of state.
- **Minimal additions beyond `08`'s literally enumerated M2 endpoint list**, following the same
  precedent `08` itself permits per `00` §12 ("add the obvious supporting endpoint, document it"):
  - `GET /api/v1/events/{eventId}/rounds` and `GET /api/v1/rounds/{roundId}` (staff) — `08` specifies
    round mutation/lifecycle endpoints but not an explicit list/get, which organizer/judge UIs need
    to render the rounds screen at all.
  - `GET /api/v1/rounds/{roundId}/attempts` (staff) and `GET /api/v1/guest/events/{eventId}/me/attempts`
    (competitor, own attempts only, across the whole event) — needed for the judge-entry table and
    the competitor's own "my results" view respectively; both are read-only views over data the
    caller already has access to via other means, not a new capability.
  - `GET /api/v1/attempts/{attemptId}/revisions` (staff) — surfaces the append-only revision history
    required by `00` §9; without a read endpoint the audit trail would be unobservable.
  - `POST/GET/DELETE /api/v1/events/{eventId}/staff-assignments` (organizer-only) — the minimal CRUD
    needed to actually assign the new `EventRole.JUDGE`, which has no equivalent in M1's org-role
    model (see above).
- **Ruleset snapshot captured once, at `openRegistration()`, not re-captured per round.** `00`/`08`
  require an immutable ruleset snapshot so later rule changes never retroactively alter a running or
  completed event's scoring. `RulesetSnapshot` (rulesetVersion, puzzleType, timerMode,
  scramblePolicy, snapshotAt) is built once and persisted as JSON on `Event` the first time
  registration opens (idempotent — never overwritten if already set), then every `Round` stores the
  same `rulesetVersion` at creation. Since this milestone ships only `RulesetVersion.V1`, there is
  currently nothing for a snapshot to diverge from in practice; the mechanism exists so a future
  `V2` cannot silently change how an already-open event scores.
- **Scoring engine kept entirely free of Spring/JPA dependencies** (`com.twistmeet.api.scoring`
  package has zero framework imports), per `00`'s determinism/testability requirement for the
  scoring module specifically. `ExactValue` (numerator/denominator rational arithmetic with
  cross-multiplication comparison) avoids floating-point rounding entirely when computing MO3/AO5
  averages, then rounds the final result half-up to the nearest 10ms exactly once, per `09`. A
  single generic sort-and-trim algorithm (discard-count parameter: 0 for MO3, 1 for AO5) is used for
  both formats rather than two special-cased implementations — DNF-propagation behavior (a DNF
  counts as the worst possible value for sorting/discarding purposes) falls out of the generic
  algorithm rather than being hand-coded per format, verified against every `09` conformance vector.

## Open decisions register (M2 addition)

**OD16 (correction-decision authority) was opened here at M2 and closed at M3 by explicit
instruction: organizer-only, per `00` §5 and `08`, over `02`'s conflicting narrative text.** See
"M2 implementation decisions" above for the original conflict and "M3 implementation decisions"
below for the closure and its enforcement (server 403 + UI gating). No open-register row remains
for it — this paragraph is the record of its resolution, kept here rather than silently deleting
the ID.

## M3 implementation decisions

Additions, conflict resolutions, and simplifications made while building M3 (organizer and
competitor experience), per `00` §12.

- **OD16 closed: correction-decision authority is organizer-only**, per explicit instruction
  confirming `00` §5/`08` over `02`'s conflicting narrative text. No code change was needed on
  the API side — `CorrectionService.decide()` already called
  `tenantAccessService.requireOrganizer()`, and `CorrectionFlowTest
  .onlyOrganizerCanDecideACorrectionNotJudgeOrStranger` already asserted a judge gets `403`. What
  M3 adds: (1) a new `GET /events/{eventId}/my-role` endpoint (see below) so the web correction
  queue can know the caller's own role and hide the decide-controls for a non-organizer rather
  than show buttons that would just 403; (2) this closure note, replacing OD16's "needs
  confirmation" framing in the M2 section with a resolved one.
- **New minimal endpoint: `GET /events/{eventId}/my-role`.** `00` §5/§9 require "Display role
  and event scope visibly on staff pages" (`RoleBanner` in `07`'s shared-component list). M1/M2
  had no way for a judge who is not an organization member to discover their own role — every
  other organizer-scoped read (`GET /events/{eventId}` itself) 404s for a non-member judge, by
  design (anti-enumeration). `TenantAccessService.resolveStaffRole()` resolves
  OWNER/ORGANIZER/JUDGE the same way `requireJudgeOrOrganizer` already does, just returning the
  resolved role instead of only checking a specific action; 404 for a total stranger, matching
  every other check in that class. Tested in `MyRoleApiTest`.
- **New field: `Round.startedAt`.** `07` S08 Tournament Control requires "time since round
  start." `Round` had no timestamp distinct from `createdAt` (when the round was configured,
  while still `DRAFT`) for when it actually went `LIVE`. Added `startedAt`, set once in
  `Round.setState()` the first time state becomes `LIVE` (migration `V4`). A minimal, narrowly
  scoped addition — not a new capability, just exposing a timestamp the lifecycle already
  implies.
- **`PUT /corrections/{id}/decision`'s `reason` field is now server-validated `@NotBlank`,
  not just documented as required.** `07` S10 says "Require decision reason," but the M2 DTO
  only capped its length (`@Size(max=500)`) without requiring it be non-empty. `07`'s own design
  checklist says access/validation rules must never be UI-only ("do not encode access control
  only as hidden UI" — the same principle applies to a required-field rule enforced only by a
  client-side `required` attribute). Fixed server-side; tested in
  `CorrectionFlowTest.decisionRequiresANonBlankReason` (empty string and omitted field both
  `400`).
- **Tournament Control (S08) is composed entirely from existing M2 reads — round, round
  attempts, event corrections filtered client-side to the live round's attempt IDs — not a new
  aggregate endpoint.** Per this milestone's explicit instruction to reuse M2 APIs and add only
  what the specs require, and because the underlying data (round state, attempt list, pending
  corrections) was already each independently available and independently authorized. The one
  new read (`my-role`) and one new field (`startedAt`) above were added only because nothing
  existing could supply that specific information at all, not for convenience.
- **Tournament Control's "Close round" is one user-facing action that performs two server
  transitions (`review` then `close`) in sequence.** `00` §6 requires `LIVE → REVIEW → CLOSED`
  as two distinct transitions (REVIEW exists specifically so unresolved corrections can block
  it); `07` S08 describes "close round when eligible" as a single control-room action. Both are
  true at once: the round lifecycle itself is unchanged (still two real state transitions,
  still blocked by pending attempts/corrections exactly as `RoundService` already enforced), but
  the Tournament Control UI collapses them into one confirm dialog rather than requiring the
  organizer to separately hunt for an "Enter review" button first. The event-detail page still
  exposes "Enter review" and "Close round" as separate actions for a round that isn't currently
  the live one shown in Tournament Control.
- **Pause/resume and close round require `ConfirmDialog` in M3's new Tournament Control UI,
  consistent with `00` §5's "explicit confirmation required for round close... and event
  archive"** (pause/resume isn't literally listed there, but is equally a live-event-affecting
  action with no undo other than pausing again, so the same treatment was applied rather than
  leaving one sensitive toggle unconfirmed next to ones that are).
- **"Request help" (P05's always-available help/escalation action, distinct from a
  correction request) is explicitly NOT implemented.** `07` P05 lists `Request judge/help` as
  available any time during an attempt (before a result exists), separately from the
  "correction-request action" that appears only after a result is recorded. M2's
  `CorrectionService.create()` requires the attempt to already have a non-`PENDING` result (it
  exists to contest a result, not to page staff) — relaxing that check to also serve as a
  general help call would conflate two different concepts (contesting a recorded result vs.
  flagging a problem before one exists) and there is no backing data model for a help ticket in
  `00`/`08` (no entity, no staff-facing queue distinct from the correction queue). Building a
  real help-ticket subsystem is a genuine new capability, not a UI gap, and is left for an
  explicit future requirement rather than faked with a button that silently does nothing useful
  pre-result. Documented here rather than silently omitted; `WaitingRoomPage`'s file comment
  cross-references this note.
- **Competitor check-in state is informational only; it never gates the attempt UI.** Drafting
  the waiting room's explicit lifecycle states (`07` P03: waiting, check-in needed, ready, round
  not open, disconnected, withdrawn) initially rendered "ask staff to check you in" as a hard
  gate that hid the entire attempt list — but the backend never requires `checkInState ==
  CHECKED_IN` to start/stop/submit an attempt (`AttemptService` has no such check), so that would
  have been a new, invented restriction with no server-side backing, silently blocking a
  competitor from ever seeing their attempt once a round went live if nobody had recorded their
  check-in. Caught via the M3 E2E phone-mode script (`m3_e2e_competitor.mjs`) actually exercising
  the flow without a check-in step and finding the attempt UI never appeared. Fixed: check-in
  state is now shown as a badge plus an informational line, and the attempt list renders
  regardless of it — matching what the server actually enforces.
- **Event creation wizard (S03) is simplified to the fields `CreateEventRequest` accepts, in
  three steps (basics, operations, review) instead of `07`'s four (basics, competition,
  operations, rules-preview-and-create).** Puzzle type is always 3×3×3 (`00` §4's only supported
  default — there is no puzzle-type selection to show), and round/format/advancement setup
  happens on the event-detail page after the draft exists (unchanged from M2), not inside the
  wizard — `08`'s event-creation endpoint has no fields for rounds. "Rules preview" has no
  backing content to preview beyond the ruleset version, so step 3 shows a plain review of the
  entered fields instead of a separate rules document. There is no cross-reload draft
  persistence: the event is not created until the wizard's final step, so "save draft" means "go
  back and change anything before creating," not a resumable draft across browser sessions.
  Documented here as a simplification, not a silently dropped requirement.
- **Shared component set added, matching `07`'s "Common components and states" list where this
  milestone's screens need them:** `StatusBadge`, `SavedState`, `LoadingState`, `EmptyState`,
  `ErrorState`, `OfflineState`, `ConfirmDialog`, `RoleBanner`, and a `LiveAnnouncer` (a visually
  hidden `aria-live` region) for screen-reader status announcements. `TimeInput` and
  `PenaltyPicker` are not separately extracted as components — the existing inline time-input
  and penalty-select markup in judge entry and the competitor attempt screen already meets the
  same behavioral requirements (numeric keyboard via `type="number"`, inline validation, retained
  raw value, mutually exclusive penalty options) and extracting them was not required to satisfy
  any requirement beyond code organization. `LiveTable` (an accessible table with stable row
  order, polite live updates, no focus theft) is approximated by plain semantic `<table>`
  elements with `<caption>` throughout M2/M3 rather than built as a separate component, since
  none of this milestone's tables yet have the kind of live, reordering updates `LiveTable`
  exists to handle gracefully (Tournament Control's table re-renders on an explicit reload, not a
  push stream — SSE/WebSocket live updates are `08` row 63, still M3-adjacent/real-time scope not
  built here, not reused from this milestone).
- **`useOnlineStatus` (browser `online`/`offline` events) is the connection-loss signal, not a
  heartbeat ping to the API.** `07`'s `OfflineState` component doesn't specify a detection
  mechanism. The browser's own connectivity events are simpler and sufficient to demonstrate the
  required state (verified in E2E by toggling Playwright's network-offline emulation on an
  already-loaded page, which fires the same `offline`/`online` events a real network drop would);
  a true round-trip heartbeat (detecting "online per the OS, but the API itself is unreachable")
  is a further refinement not built here, since `navigator.onLine` already covers the literal
  "connection-loss" case this milestone's instruction named, and every page's existing fetch-error
  handling (`ErrorState` with retry) already covers the "API unreachable while nominally online"
  case on its own.
- **Judge entry's "undo window" re-submits the previous value as a new revision; it is only
  offered after a correction to an already-recorded result, not after the very first entry.**
  `07` S09 says "Save attempt... immediate saved receipt + undo window 10 seconds (undo creates
  revision; cannot erase)." Before any result is recorded there is nothing to "undo" back to —
  the only previous state is `PENDING`, which `recordJudgeResult` cannot set (it is not a valid
  judge-entry status). So undo is offered only when the save being undone itself overwrote a
  prior `OK`/`DNF`/`DNS` value, and clicking it submits that prior value as a new
  `PUT .../judge-result` call — a real, audited revision, never a client-side-only rollback.
  Verified in `m3_e2e_organizer.mjs`: no undo button after the first save, one appears after a
  correction, and clicking it restores the earlier value and is visible in the UI immediately.

## OD01 resolution: scramble generator and 3D-guide library

Resolved at M4, per explicit instruction to review primary sources before choosing. Both pieces
of evidence below were gathered by directly fetching the library's own repository, license file,
npm registry metadata, and Maven Central artifact metadata — not from memory or secondary
summaries — on 4 October 2026.

### Generator: `org.worldcubeassociation.tnoodle:lib-scrambles`

**Choice: the official WCA scramble program itself (TNoodle/`tnoodle-lib`), not a third-party or
homegrown generator.** `00` §7 requires "an established generator with reviewed license,
maintenance, puzzle-state correctness and version"; `02` requires "a maintained, compatible,
reviewed generator... Do not roll a homegrown generator for production without specialist
review. If WCA-level equivalence is required, use only a method accepted for that purpose." No
piece of software meets that bar more directly than the generator the WCA itself runs at every
sanctioned competition — its scrambling correctness has been continuously, publicly exercised by
the entire competitive speedsolving community for years, which is a stronger and more
independently-verified correctness signal than any private review this task could perform.

Evidence gathered directly:
- **Repository**: `github.com/thewca/tnoodle-lib` ("scrambling code portion of TNoodle"), owned by
  the `thewca` GitHub organization (the World Cube Association's own account) — not a
  third-party fork or community reimplementation.
- **License**: GPL-3.0, confirmed by fetching the repository's own `LICENSE` file directly
  (`raw.githubusercontent.com/thewca/tnoodle-lib/master/LICENSE`): "GNU GENERAL PUBLIC LICENSE,
  Version 3." Also confirmed in the published Maven POM's `<licenses>` block.
- **Maintenance**: the 10 most recent commits to `tnoodle-lib`'s `master` branch (checked via the
  GitHub commits API) are all dated **4 October 2026 — the same day this decision was made** —
  including "Release v0.20.0" by a WCA Software Team member (`gregorbg`), live dependency-bot
  updates, and build-tooling fixes. This is about as current as "actively maintained" can be
  demonstrated.
- **Published artifact**: `org.worldcubeassociation.tnoodle:lib-scrambles:0.20.0` on Maven
  Central (fetched `repo1.maven.org/maven2/.../lib-scrambles/maven-metadata.xml` directly — not a
  secondary index), `lastUpdated` timestamp `2026-10-04`, matching the GitHub release commit
  exactly. It depends at runtime on `scrambler-threephase` and `scrambler-min2phase` (both
  published by the same group, same version) — these are the actual 3×3×3 random-state scrambling
  algorithm modules; `lib-scrambles` is the umbrella/registry layer.
- **Puzzle support and correctness approach**: `PuzzleRegistry.THREE` (confirmed by fetching
  `PuzzleRegistry.java`'s source directly) maps the 3×3×3 event to `ThreeByThreeCubePuzzle`, which
  is backed by the `threephase`/`min2phase` random-state algorithm (Kociemba's two-phase
  algorithm, the same approach documented on the WCA's own
  `worldcubeassociation.org/regulations/scrambles/` page as how official scrambles are produced:
  a uniformly random valid cube state is generated, solved, and the solution is reversed into the
  scramble — "random-state scrambling," required by WCA Regulation 4b3 for 3×3×3). This is not
  "a generic random-move generator" (which the task explicitly says not to use without
  justification) — it is the specific, reviewed, state-correctness-driven approach the
  authoritative contract's "puzzle-state correctness" requirement is asking for.
- **Public API** (confirmed from `Puzzle.java`'s source directly): `@Export public final String
  generateScramble()` returns one scramble string; this is the only method this codebase calls
  (wrapped in `ScrambleGenerator`, the sole class in this codebase that imports anything from
  `org.worldcubeassociation.tnoodle`, so the GPL dependency's surface is contained to one file).

**Known, genuine license consideration — GPL-3.0, flagged for product-owner awareness, not
silently accepted.** GPL-3.0 is a strong copyleft license: distributing a combined work containing
GPL-3.0 code generally requires the combined work to also be GPL-3.0-licensed. This app is
deployed as a network service (SaaS), not as software distributed to end users or resold/licensed
as an installable product — under GPLv3 (unlike AGPLv3), running a service over a network is not
itself an act of "distribution" that triggers the copyleft obligation, so using `tnoodle-lib`
as a backend dependency for a hosted TwistMeet service does not require TwistMeet's own source to
be released. **This stops being true the moment any of the following happens, and must be
re-reviewed first**: (a) distributing the TwistMeet codebase or a binary/jar of it to a third
party (open-sourcing under a different license, selling an installable/on-prem version, or
providing the source to a customer); (b) statically bundling this dependency into something
distributed outside the running service. No such distribution is planned or in scope for M4; this
is recorded so a future decision-maker doesn't miss it. No safe alternative-license 3×3×3
random-state generator with comparable maintenance/correctness evidence was found during this
review (see "options considered" below) — if GPL-3.0 turns out to be unacceptable for a future
distribution model, that would need a renewed OD01-style review, not a quiet swap.

**Options considered and rejected:**
- **`cubing/scramble`** (the scramble-generation half of `cubing.js`, see the 3D-guide section
  below) — also produces WCA Regulation 4b3-compliant random-state 3×3×3 scrambles, and is
  dual-licensed MPL-2.0/GPL-3.0-or-later (the more permissive option could be chosen). Rejected
  as the *generator* specifically because it is a browser/Node JavaScript library, and this
  project's API is a Spring Boot (JVM) service, not a Node service — using it would mean either
  running a Node subprocess from the Java backend or moving scramble generation into the Next.js
  web tier (a cross-service trust/architecture change out of proportion to this milestone, and a
  deviation from "server-side generation" meaning the API, not the web frontend, which already
  owns every other piece of server-authoritative competition state). `cubing.js` is still used —
  for the 3D guide only, where it runs client-side by design (see below).
- **A from-scratch random-state generator** (writing a cube-state model + a two-phase solver) —
  explicitly the "do not roll a homegrown generator for production without specialist review"
  case `02` warns against. Rejected outright; not a serious candidate.
- **A generic random-move scrambler** (just emitting N random face turns with no state modeling)
  — explicitly the case this task's own instruction says not to pick without justification, and
  what `02` calls "not an arbitrary list of random face turns." Rejected: it does not meet "legal
  move parsing" or "puzzle-state correctness," and would not be WCA Regulation 4b3-equivalent even
  informally.

### 3D move guide: `cubing` (npm), `TwistyPlayer` / `<twisty-player>`

**Choice: `cubing.js`'s `TwistyPlayer` web component**, used client-side only, fed the scramble
notation string the API already decrypts and authorizes — it never generates or knows a scramble
on its own in this codebase.

Evidence gathered directly:
- **Repository**: `github.com/cubing/cubing.js` ("A library for displaying and working with
  twisty puzzles. Also currently home to the code for Twizzle"), maintained by `lgarron` (Lucas
  Garron), a long-standing member of the speedcubing/WCA software community.
- **License**: dual MPL-2.0 / GPL-3.0-or-later, confirmed directly from the npm registry's
  published package metadata for the latest version (`license: "MPL-2.0 OR GPL-3.0-or-later"`).
  This codebase uses it under the **MPL-2.0** option — a permissive, file-level copyleft license
  with no restriction on using it as a dependency in a proprietary application; MPL-2.0's
  copyleft only reaches modifications to MPL-licensed files themselves, which this project makes
  none of (it only imports and calls the published package).
- **Maintenance**: npm registry metadata shows the latest version, `0.63.8`, published
  **2026-09-28** — six days before this decision — fetched directly from
  `registry.npmjs.org/cubing` (not a cached mirror).
- **API shape** (confirmed by downloading the actual npm tarball and reading its shipped
  TypeScript declarations, not documentation prose): `TwistyPlayer` accepts `alg` (the scramble
  string), `puzzle: "3x3x3"`, and a `tempoScale` number controlling animation speed; it exposes
  `jumpToStart()`/`jumpToEnd()`/`play()`/`pause()`/`togglePlay()` and ships its own built-in
  control panel (move-by-move stepping, satisfying `07` S07's "move-by-move controls" without
  this codebase building a custom stepper).
- **No claim of physical verification**: `TwistyPlayer` renders a 3D model only; nothing in its
  API performs or claims camera-based or sensor-based cube-state verification, consistent with
  `00` §7's "the 3D cube is a guide to moves, not sensor-based verification" and exclusion E11
  ("no machine claim that a 3D guide confirms a real cube") — this codebase's own UI copy
  (`TwistyGuide` component) states this explicitly next to the rendered cube.
- **Reduced motion**: `TwistyPlayer` has **no documented native `prefers-reduced-motion` support**
  (checked directly — no such property appears in its shipped type declarations, and no primary
  source documenting one was found). This codebase handles it itself: `TwistyGuide` detects
  `prefers-reduced-motion: reduce` and sets `tempoScale` to a high value, shrinking animated
  transitions to near-instantaneous — the same "shrink animation duration toward zero" approach
  already used elsewhere in this app's reduced-motion handling (`globals.css`'s global
  `prefers-reduced-motion` rule, from M3), rather than a verified built-in "instant jump" mode,
  since no such mode is documented to exist. Noted here as a best-effort implementation, not a
  claim that cubing.js itself is reduced-motion-aware.

**Options considered and rejected:**
- **A hand-built Three.js cube renderer** — would duplicate `cubing.js`'s purpose-built,
  community-maintained twisty-puzzle renderer for no benefit, and reintroduce exactly the kind of
  "homegrown, unreviewed" risk `02` warns against for the generator (the same caution reasonably
  extends to a from-scratch 3D cube-state renderer, which is easy to get subtly wrong — e.g.
  sticker orientation after a move).
- **`cubejs` (the `ldez`/community npm package)** — an older, less actively maintained
  cube-manipulation library without a comparably current maintenance signal or a built-in,
  purpose-made player component; `cubing.js` was judged the stronger choice on both maintenance
  recency and because it is the same ecosystem that produces `js.cubing.net`/`alpha.twizzle.net`,
  tools the WCA community itself uses.

## M4 implementation decisions

Additions, conflict resolutions, and simplifications made while building M4 (scramble controls
and the 3D move guide), per `00` §12.

- **New event-scoped role: `EventRole.SCRAMBLER`.** `00` §5 names Scrambler as a role distinct
  from Judge/Organizer ("assigned scramble and 3D guide; mark physical preparation checked; no
  results or roster access unless also assigned another role"). Added alongside the existing
  `JUDGE` value on the same `EventStaffAssignment` mechanism M2 built — no schema change needed
  beyond the new enum value, since the role column is a plain string, not a database-native enum
  type. `TenantAccessService.requireScrambleStaff()` accepts Organizer, Judge, *or* Scrambler for
  the preparation-station actions (reveal, official-view, mark-applied, mark-checked, print,
  list); `requireOrganizerOrJudge()` (spoil) deliberately excludes a plain Scrambler, since
  spoiling is a correction-adjacent decision, not routine preparation work the Scrambler role's
  own description covers.
- **Spoiling always burns the original secret, whether or not it was ever revealed.** `00` §7:
  "Do not reuse a used or revealed scramble for a replacement." A spoil could in principle happen
  before anyone ever revealed the scramble (e.g. an organizer realizes a round's puzzle type was
  misconfigured before any scrambler looked at it) — this implementation marks the secret
  consumed unconditionally on spoil anyway, rather than only when `revealedAt` is set, since
  there's no operational benefit to keeping an unrevealed-but-spoiled secret assignable, and doing
  it unconditionally is simpler and strictly safer (never risks reusing a secret that actually was
  shown to someone but whose `revealedAt` write raced with the spoil).
- **A voided/spoiled scramble assignment is never mutated back to "live"; a replacement is
  always a brand-new row.** Mirrors the append-only philosophy already used for
  `ResultRevision`/`AuditEvent`/M2's roster withdraw-vs-remove split: the full assignment history
  for an attempt (including every past mistake and its reason) stays reconstructible from the
  table, never overwritten in place. R40 ("no hard-delete after attempt assignment") is applied
  the same way to scramble assignments.
- **`revealed_by` has no foreign-key constraint to `users`, unlike `applied_by`/`checked_by`/
  `voided_by`.** In self-scramble mode, the competitor themselves is the one who "reveals" their
  own scramble (there is no staff action involved in casual mode — see 00 §7.2), and a competitor
  is an `event_entrants` row, not a `users` row. This mirrors `audit_events.actor_id`, which has
  the identical staff-or-guest-actor shape and was already built with no FK for exactly this
  reason. Caught by a real `DataIntegrityViolationException` during this milestone's own test
  development (self-scramble reveal failing with a foreign-key violation), not discovered by
  inspection — fixed before any version of this migration was committed.
- **Self-scramble "unlock" is defined as: this attempt is the lowest-numbered attempt for this
  entrant whose result is still `PENDING`.** `00` §7.2 says "no future attempt sequence is
  available before the attempt is unlocked" without defining "unlocked" precisely. This mirrors
  the existing sequential attempt-number model (attempts 1..N already exist once a round is
  prepared) rather than introducing a new state: the moment a competitor's current attempt is
  resolved, the next one's scramble becomes revealable, with no dependency on timer
  start/stop state — a competitor can view the scramble before pressing "Ready — start timer" (as
  the real physical workflow requires: see the scramble, apply it to the physical cube, *then*
  start the clock).
- **`GET /guest/.../current-scramble`'s abbreviated path from `08` is resolved as
  `GET /attempts/{attemptId}/current-scramble`** (attempt-scoped, not a separate
  `/guest/events/{eventId}/...` path), and it rides the guest cookie the same way the existing
  `/attempts/**` routes already do (`AttemptController`'s start/stop/submit), rather than needing
  a new `SecurityConfig` `permitAll` entry. This was the one deliberately-unresolved item carried
  forward from M1/M2/M3 (see `08`'s row 50 history in TRACEABILITY.md) — resolved now because M4
  is the milestone that actually needs it.
- **`Attempt.scrambleAssignmentId` (an M2 placeholder column, always null before M4) is now
  populated**, matching `08`'s literal DTO note ("`Attempt` DTO includes `scrambleAssignmentId?`").
  Exposed in `AttemptDtos.AttemptView` to both staff and the owning competitor: it is an opaque
  pointer only (never the notation), and the competitor-facing `current-scramble` endpoint
  independently re-checks ownership and unlock status regardless of what ID is presented, so
  knowing the ID grants no access by itself.
- **Known, documented side effect: generating a scramble batch bumps every attempt's
  optimistic-lock `version`** (since `ScrambleService` saves each `Attempt` row to attach its new
  `scrambleAssignmentId`). A staff client that fetched attempt state *before* batch generation
  and then submits a judge result with a stale `expectedVersion` gets the existing, correct
  `409 STALE_VERSION` response (R39/concurrency behavior unchanged) — this is not a new failure
  mode, just a new, real reason the version can change between two reads, worth naming so nobody
  mistakes a `STALE_VERSION` response right after batch generation for a bug.
- **Print also reveals.** `00` §7 says "print only for officials," and the print view is how a
  staff-prepared-mode scrambler conventionally receives the notation at all (there is no separate
  "give the scrambler a copy" step) — so printing a batch marks every not-yet-revealed assignment
  in it as revealed (audited once per batch, not once per assignment) rather than leaving an
  unaudited path where notation left the vault without ever being marked revealed.
- **Encryption key management: environment-variable injection with a logged, ephemeral
  dev-only fallback — no KMS/cloud secret manager integration**, per the explicit instruction not
  to create cloud resources. `00` §10's "store secrets in platform secret manager/environment
  injection, never commit" is satisfied by `TWISTMEET_SCRAMBLE_ENCRYPTION_KEY` (a 32-byte
  AES-256 key, Base64-encoded); `04`/`08`'s "keys managed outside the database" requirement holds
  because the key never touches a database table. If that variable is unset (true for this
  sandbox and for CI), `ScrambleEncryptionService` generates a random key once per process and
  logs a clear warning that it won't survive a restart — the same "safer simpler default,
  documented" pattern already used for the dev mail catcher (OD04) and the M1 guest-credential
  TTL placeholder. A real deployment must set this variable via its actual secret manager before
  any real event uses the scramble module — not yet true of this sandbox/CI environment, which
  is why the fallback exists at all.
- **No groups/stations model exists yet** (same gap already noted for M2/M3), so "role-scoped
  reveal" is scoped to *any* staff holding Organizer/Judge/Scrambler for the *event*, not to a
  specific assigned scrambler for a specific attempt/station as `07` S07's fuller vision
  describes. Documented as a scoping simplification consistent with the existing event-role model,
  not a silent narrowing of the authorization requirement (every role named in `00` §5 is still
  enforced; only the finer per-station assignment granularity is deferred).

## M5 implementation decisions

### M4 scramble-secrecy verification gap, closed before M5

The M4 report's own leakage tests covered competitor HTML, competitor API access, and an
unauthenticated request, but `08`'s API-security-tests paragraph and this project's own
acceptance bar also require evidence for public endpoints, error responses, logs, caches,
exports, and unauthorized staff views. Closed with a new `ScrambleSecurityGapTest`:

- **Error responses**: a validation error (missing spoil reason) and 404s for a nonexistent
  assignment never echo the just-revealed notation — confirmed, no fix needed (`ApiException`'s
  messages and Bean Validation's default messages are both static strings; no endpoint accepts
  notation as request input at all).
- **Application logs**: captured every log line (a Logback `ListAppender` on the root logger, set
  to `ALL`) across reveal/official-view/spoil and found a **real exposure**: `RevealView`'s and
  `PrintEntry`'s default record-generated `toString()` includes plaintext `notation`, and Spring
  MVC's DEBUG-level request logging (`AbstractMessageConverterMethodProcessor`'s "Writing [...]"
  line) calls a response body's `toString()` verbatim. At the application's current default log
  level (INFO) this never fires, but the moment any environment ever raised that specific log
  level, plaintext notation would land in logs. **Fixed**: both records now override `toString()`
  to redact `notation`, so this is closed structurally, not by relying on every environment to
  keep DEBUG logging off forever.
- **HTTP caches**: none of the four notation-carrying responses (reveal/official-view/print/
  current-scramble) ever set a `Cache-Control` header before this pass — a browser back/forward
  cache or an intermediate proxy could in principle retain one. **Fixed**: all four now set
  `Cache-Control: no-store`.
- **Exports**: did not exist until this milestone's CSV feature; covered there instead —
  `HistoryExportFlowTest.csvContainsRawMsAndFormattedResultAndNeverScrambleNotation` asserts the
  export of an event with a revealed scramble never contains the notation or the word
  "notation."
- **Unauthorized staff views**: added a negative test for staff on a *different event in a
  different organization* (judge-assigned elsewhere) attempting reveal/official-view/print — all
  404. Also added a *positive*, intentional-behavior test documenting that any member of the
  *same* organization can reveal a scramble for an event they were never explicitly staffed on
  (`TenantAccessService.requireOrganizer` grants Organizer rights org-wide, by design since M1) —
  pinned down as intended, not an accidental over-grant left ambiguous.
- **Public/unauthenticated endpoints**: didn't exist in M4 (the self-scramble `current-scramble`
  guest-cookie endpoint was already tested). Now that M5 adds `/public/events/{slug}` and
  `.../standings`, `ScrambleSecurityGapTest.publicUnauthenticatedEndpointsNeverExposeScrambleNotation`
  proves neither ever contains notation.

The TNoodle GPL-3.0 SaaS-vs-distribution question (OD01 resolution, above) was **not** touched by
this pass: no license or generator change was made, and no legal-approval claim is made here.

### Advancement preview/commit

- **Reuses the pure scoring engine unchanged.** `AdvancementService` calls `ScoringEngine.score`,
  `RankingService.rank`, and the existing (previously unwired) `AdvancementCalculator.topN`/
  `.topPercent` directly — no ranking or tie math was reimplemented.
- **Eligibility** (`09`: "not withdrawn and has at least one result status") is enforced in two
  parts: withdrawn entrants are filtered out before scoring, and an entrant with zero resolved
  attempts scores `RoundOutcome.NO_RESULT`, which `RankingService.rank` already excludes from the
  ranked list — no separate "has a result" check was needed beyond what the engine already does.
- **Deferred, not silently invented**: the optional organizer-selected tie-break *attempt* (`09`:
  "If organizer selected a tie-break attempt, show the tied names and create a special one-attempt
  attempt with an unused scramble...This option must be selected before registration opens") is
  not implemented. Boundary ties always advance together instead — the *default* behavior `09`
  itself names, and the one this milestone's task description's acceptance bullets ("tie
  handling") can be satisfied by without inventing an unspecified UI/data-model for an optional,
  pre-registration-locked configuration flag. If the product owner wants the tie-break-attempt
  path, it needs its own round-config field and attempt-creation flow, not a small addition to
  this pass.
- **Where "next round slots" live.** `08`: commit "creates next-round slots transactionally."
  There is no round-scoped entrant roster anywhere in the M1-M4 data model — `RoundService.prepare()`
  has always allocated attempts for *every active event entrant*, with no restriction. Rather than
  creating `Attempt` rows directly from `AdvancementService` (which would bypass `prepare()`'s own
  state checks and ResultSource logic), commit writes a new join table, `round_qualified_entrants`
  (round → entrant), and `RoundService.prepare()` now checks it: if any row exists for the round
  being prepared, only those entrants are used; otherwise it falls back to every active entrant,
  the exact M1-M4 behavior. This means a round nobody ever advanced into (e.g. an event's first
  round) is completely unaffected, and the restriction only activates once a commit has actually
  happened — recorded here since it's a real, if small, new coupling between two services that
  previously had none.
- **Idempotency and concurrency**, per `08`'s "repeated writes are idempotent" and "stale version
  gives a conflict without overwriting": a commit records `Round.advancementCommittedAt/By/Count`.
  A *repeated* call (same round, already committed) short-circuits before any check and replays
  the same deterministic result — safe because a committed round is REVIEW/CLOSED and frozen, so
  recomputing the preview always reproduces the same ranking. A *stale* `expectedVersion` on an
  as-yet-uncommitted round is rejected before any write. For genuine *concurrent* commits, the
  round's own JPA `@Version` is the lock: `markAdvancementCommitted()` is saved-and-flushed
  **before** the roster-insert loop runs, so the loser of a race fails immediately at that flush
  (its version no longer matches) and never reaches the roster writes at all — no duplicate-key
  race on `round_qualified_entrants` is possible by construction, not just by probability. No
  generic `Idempotency-Key` header mechanism was built (none exists anywhere in this codebase
  across M1-M4 either, despite `08` naming one in the API conventions — a pre-existing, consistent
  gap, not a new one introduced here); the existing `expectedVersion`-plus-state-check pattern
  (same one `AttemptService`/`CorrectionService` already use) is reused instead. Tested in
  `AdvancementFlowTest`, including two real concurrent HTTP requests via `ExecutorService`.
- **Commit requires REVIEW or CLOSED and a DRAFT next round.** Not stated explicitly in `08`/`09`
  as a hard gate, but follows directly from `07` S11 being a step in round *review*, and from
  "creates next-round slots" implying the next round hasn't been prepared yet. If the next round
  doesn't exist yet, commit returns `400 NO_NEXT_ROUND` rather than silently doing nothing.

### Publishing and public display

- **Not gated on `EventState`.** Across M1-M4, `Event.state` only ever actually reaches
  `DRAFT`/`REGISTRATION_OPEN`/`REGISTRATION_LOCKED`/`REGISTRATION_REOPENED` in real code — nothing
  anywhere sets it to `READY`/`LIVE`/`COMPLETED` (round-level state, not event-level state, is
  what actually drives the competition). Gating publish on `EventState` would make it practically
  unreachable. Publish/unpublish are therefore organizer-authorized at any event state; what the
  public page actually shows is separately gated per-round (only READY/LIVE/REVIEW/CLOSED rounds
  are ever listed).
- **Resolved a real identifier conflict between `07` and `08`.** `08`'s Event DTO and endpoint
  list name a stable `publicSlug`. `07` S13 says "Hide by revoking token," implying a *revocable*
  identifier. A pure stable slug can't satisfy "hide by revoking" (unpublishing under the same
  slug would still let anyone who saved the old link see it reappear on republish). Resolution:
  `Event.publicSlug` is generated once on first publish and stays stable across publish/unpublish
  cycles for convenience, **except** unpublish specifically rotates it to a fresh value — so
  "hide" really does kill the previously shared link, and a subsequent publish needs a newly
  shared link. Tested in `PublishAndPublicFlowTest.publishGeneratesASlugAndUnpublishRotatesIt...`.
- **No `PublicSnapshot` cache table was built.** `04` names it as "optional publication cache
  containing only approved fields." `PublicDisplayService` instead computes the public view live
  from current data on every read, gated by `publishedAt`/round state — avoiding a second place
  standings data can silently go stale relative to the authorized staff view, at the cost of
  doing the ranking computation on every public request rather than once at publish time. If
  public traffic ever makes that computation cost matter, the snapshot table is the documented
  next step; it was not needed to satisfy this milestone's acceptance bar.
- **Public DTOs are hand-written allowlists** (`PublicDtos.PublicEventView`/`PublicStandingsView`/
  etc.), not views over the staff DTOs — structurally incapable of carrying scramble, guest
  credentials, email, tokens, notes, or staff actions, the same defense-by-DTO-shape pattern M4
  used for `RevealView`. Withdrawn entrants are excluded entirely from public standings (a
  privacy judgment call, not stated explicitly in `07`/`08`, but consistent with "private entrant
  data" never leaking per `08`'s API security test).
- **Provisional rounds show progress, not hidden averages.** `07` S13: "If round incomplete,
  label provisional and show completed attempt counts; don't present partial averages as final."
  Read as "don't claim it's final," not "hide the number" — `PublicEntrantStanding` includes the
  in-progress rank/result alongside `completedAttempts`/`totalAttempts` and a `provisional: true`
  flag the UI must honor, rather than omitting the result entirely.
- **`GET /public/events/{publicSlug}/standings` takes `roundId` as a query parameter**, not a
  second path segment, since `08`'s literal path has no room for one and an event has multiple
  rounds. A minor, documented route-shape choice, not a change to what data is exposed or how it's
  authorized.
- **Deferred, not silently invented**: public name masking (`07` S12, "Public name masking
  option" — no further spec given for the UI/API shape of the toggle); the podium-reveal animation
  and a refresh/reconnect indicator on the public page (both depend on the real-time channel,
  which remains unbuilt — row 63); S12's dedicated live/unpublished/published/revisions tabs
  (folded into the existing event page's sections instead, matching the S04 precedent).

### Organization history and CSV export

- **A new, separate `history` endpoint, not an extension of the existing event-list endpoint.**
  `GET /organizations/{orgId}/events` (row 15) has been the dashboard's event list since M1; `07`
  S14 additionally wants date/name/status search and per-event entrant/round counts. Rather than
  widening row 15's existing `EventView` response (risking a regression to M1-M4 behavior this
  task was explicitly told to preserve), a new `GET /organizations/{orgId}/events/history` route
  with its own summary DTO (`EventHistorySummary`) was added instead.
- **CSV-injection ("formula injection") mitigation — a real gap no product doc names.** A
  competitor's free-text display name (`EventEntrant.displayName`) lands directly in an exported
  CSV cell a staff member may open in Excel/Sheets/LibreOffice. None of `00`/`07`/`08`/`09`
  mention this risk at all. Added a small hand-written `CsvWriter` (no CSV library exists anywhere
  in this codebase; none was added) that RFC-4180-quotes cells containing commas/quotes/newlines
  and prefixes a cell beginning with `= + - @` or a tab with a single quote, neutralizing the
  formula-injection vector while leaving the visible text intact. Tested in
  `HistoryExportFlowTest.csvSanitizesFormulaInjectionInDisplayNames`.
- **CSV columns**: `Round, Rank, Entrant, Outcome, ResultMs, ResultFormatted, BestSingleMs,
  BestSingleFormatted, AttemptsRawMs, AttemptsFormatted` — one row per (round, entrant), satisfying
  `09`'s "CSV exports raw milliseconds and formatted result" for both the round-level result and
  every individual attempt (semicolon-joined per attempt). "Raw" is read as "the exact integer-ms
  value used for scoring" (the adjusted, post-penalty time), not the pre-penalty raw judge entry —
  the adjusted value is what the round result and ranking are actually built from, and is the
  value a reader reconciling the CSV against the standings screen would expect.
- **UTF-8 with a BOM** (byte-order mark), for Excel compatibility — `08`/`07` only say "UTF-8";
  the BOM is additive and doesn't change the encoding, just how reliably common spreadsheet
  software detects it.
- **Deferred, not silently invented**: "copy event settings" (duplicate-event-as-template) and
  retention-policy deletion from `07` S14 — the M5 task's own acceptance bullets name only
  "organization event history and the specified CSV export," not these two S14 extras.

### GET /events/{eventId}/audit

Exposes the `AuditEvent` rows every milestone since M1 has recorded via `AuditService` but never
had a read endpoint for. Organizer-only, matching `08`'s "owner/organizer restricted." No dedicated
authorization test was added for this one small addition specifically — see "remaining gaps" in
the M5 completion report.

## Deviations from documents

None yet. This section will record any approved deviation with rationale and the specific
document/section it diverges from.

## M0 review record (self-review against the contract)

Reviewed this file against `00-authoritative-build-contract.md` and the supporting documents
before starting M1 (see `M0-REPORT.md` §4 for the full writeup). Result: no undocumented
assumption or deviation found. The stack decision and defaults listed above are the contract's
own defaults taken verbatim, not reopened or altered; the precision note is a clarification of
`02-rules-and-integrity.md`'s looser wording against the controlling documents' unambiguous
integer-millisecond rule, not an invented choice. This section stays empty until a real deviation
is proposed and approved.

# TwistMeet decisions log

This file tracks implementation decisions, defaults applied, and open questions, per
`product-docs/11-ai-build-playbook.md` (M0 exit criteria) and `00-authoritative-build-contract.md` §12.
Update this file whenever an ambiguity is resolved or a behavior deviates from a document.

## Status: M1 complete — Product foundation + project setup

M0 (repository inspection and planning) is complete and recorded below unchanged. M1 is now also
complete: repository scaffolding, CI, staff auth, organization membership, tenant isolation,
event lifecycle foundation, and guest join. See "M1 implementation decisions" below for what M1
adds, and `M0-REPORT.md` §8 for the full completion summary (commands run, what works end to end,
bugs found and fixed, remaining gaps).

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

| ID | Decision | Category | Owner | Needed by | Blocks that milestone? | Safe default (if any) | Source |
|---|---|---|---|---|---|---|---|
| OD01 | Scramble generator/library selection and independent review (legal move parsing, license, maintenance, correctness, version) | Technical | Engineering lead | M4 | **Yes** — M4 cannot ship the scramble module without it | None — `04`/`05` require an explicit reviewed choice; cannot be invented | `04` "Scramble generation component review"; `05` Integrity gate |
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

None of the "safe default" values above are treated as decided — they are the owner's starting
point to confirm or override. Items marked "None" have no documented default and must not be
guessed; they stay open until the named owner resolves them.

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

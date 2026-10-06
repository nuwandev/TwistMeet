# Security and privacy review (M6)

Against `product-docs/12-release-acceptance.md`'s "Security and privacy" checklist. Each item
below is either verified with evidence (a test, a code citation, or a manual check run this
session), fixed where a real gap was found, or recorded as an open launch-review item the specs
don't let this task resolve unilaterally.

## Cross-tenant isolation

**Verified.** Every event-scoped or org-scoped read/write goes through
`TenantAccessService` (`requireMembership`/`requireOrganizer`/`requireJudgeOrOrganizer`/
`requireOrganizerOrJudge`/`requireScrambleStaff`/`requireAssignedScrambleStaff`), which is the
single place cross-tenant access is decided (its own class comment states this explicitly, and a
spot check of every controller in `event`, `competition`, `scramble`, `help`, `registration`,
`org` confirms no controller re-implements the check). Every one of these methods returns 404 for
a total stranger to the resource and 403 for a recognized-but-unauthorized actor, consistently —
the anti-enumeration pattern this codebase uses everywhere. Tests exist per-feature
(`RosterAuthorizationTest`, `ScrambleSecurityGapTest`, `CorrectionFlowTest`,
`EventArchiveCloneTest`, etc.) rather than one giant cross-tenant suite, but collectively cover
list, detail, mutation, and export for every major resource. The one surface this checklist names
that wasn't previously covered — **realtime** — is now covered too: `EventStreamTest` asserts a
stranger gets 404 on the staff SSE channel, same as every other read.

**Guessed IDs**: every lookup is by UUID (128-bit random, not sequential), so enumeration by
guessing is already infeasible at the ID-space level, independent of the 404/403 authorization
layer on top.

## Role matrix

**Verified**, with one real gap found and fixed this session: scramble reveal was previously
reachable by any organization member, not just the assigned scrambler/judge the contract names
(see DECISIONS.md "Scramble reveal authorization boundary" and
`ScrambleSecurityGapTest.onlyAnExplicitlyAssignedScramblerOrJudgeCanRevealNotJustAnyOrgMember`).
Every other sensitive command has a role test: round transitions (organizer-only), judge-result
entry (judge-or-organizer), correction decisions (organizer-only, per OD16), scramble
batch/spoil/mark-applied/mark-checked (scramble-staff or narrower), advancement commit
(organizer-only), event lifecycle transitions (organizer-only).

**"Hidden UI cannot substitute for backend denial"**: verified by construction, not just by
testing the UI — every web page calls the same REST endpoints a non-browser client would, with no
separate "admin API" the UI alone knows about; every test above calls the API directly (via
`TestApiClient`), bypassing the web UI entirely, which is exactly how a real attacker bypassing
the UI would behave.

## Guest credentials

**Verified.** `GuestCredential` is a random token (`SecretTokens`, backed by
`java.security.SecureRandom`), stored hashed (SHA-256) at rest — `GuestAuthResolver` looks up by
`tokenHash`, never a plaintext token column. `GuestCredential.isValid(Instant)` enforces
expiry. A credential is strictly event-scoped: `requireEntrantForEvent` returns the same 404 for
"no session" and "session belongs to a different event," so a credential for event A cannot be
used to probe whether event B exists, let alone access it. "Join code cannot access a joined
entrant's account" holds structurally: joining issues a *new* guest credential scoped to the
newly created entrant; the join code itself is never a bearer credential for an existing entrant,
and `JoinController`/the join-code hash lookup is rate-limited (`SimpleRateLimiter`) against
enumeration.

## CSRF / XSS / session / password / MFA / rate-limit

- **CSRF**: double-submit cookie pattern (`CookieCsrfTokenRepository`), applied to every mutating
  request except the credential-bootstrap endpoints (register/login/email-verify/join), which
  have no prior session to carry a CSRF cookie and are rate-limited instead — a deliberate,
  documented tradeoff (`SecurityConfig`'s own class comment), not an oversight.
- **Session cookie Secure/SameSite — real gap found and fixed this session.**
  `SecurityConfig`'s own comment claimed "a secure, HttpOnly, SameSite cookie"; curling the
  running dev server showed the session cookie carried only `HttpOnly`, and the CSRF cookie
  carried neither attribute at all. Fixed: `application-production.yml` sets
  `server.servlet.session.cookie.secure=true`/`same-site=lax` (production profile only — a
  `Secure` cookie is never sent over the plain HTTP that local/CI dev uses, which would otherwise
  break every authenticated dev flow); `SecurityConfig`'s CSRF cookie customizer applies the same
  attributes to the CSRF cookie, also production-gated, while keeping it JS-readable
  (`withHttpOnlyFalse`, since the web client must read it to echo back `X-XSRF-TOKEN`). Verified
  by `SecurityCookieAttributesTest` (both cookies' actual `Set-Cookie` header content, under a
  real `{test, production}` Spring context, not just a config-file read).
- **XSS**: the API returns `application/json` throughout (not HTML with user data interpolated
  server-side), and the web app is React/Next.js, which escapes all rendered text by default —
  no `dangerouslySetInnerHTML`/raw HTML injection point exists anywhere in the pages reviewed
  this session (control room, judge entry, public display, event pages, print sheet). Response
  headers already include `X-Content-Type-Options: nosniff` and `X-Frame-Options: DENY`
  (confirmed via the same curl check above) — standard defense-in-depth Spring Security applies
  by default.
- **Password**: BCrypt (`BCryptPasswordEncoder`); minimum length 10 (max 200) enforced
  server-side via `@Size(min = 10, max = 200)` on `VerifyEmailRequest.password`
  (`AuthDtos.java`) — not merely a client-side check, verified by reading the bean-validated DTO
  directly.
- **MFA**: **not implemented.** No V1 screen spec or `00` §2 item names MFA as V1 scope, so this
  is correctly scoped to M6/launch-review rather than invented, but a pilot that will handle
  competitor PII should have this on the launch-readiness list before any public (non-pilot)
  launch. Recorded as a launch blocker in OPERATIONS.md's equivalent list / DECISIONS.md.
- **Rate limiting**: `SimpleRateLimiter` covers register/login/email-verify (`AuthController`)
  and join-code resolution (`JoinController`) — the specific endpoints `08` names for rate
  limiting. It is in-memory/per-instance, consistent with `00` §10's single-instance-per-
  environment deployment shape; a horizontally-scaled deployment would need a shared store
  (Redis or similar) instead, same caveat as `EventStreamService`'s in-process pub/sub
  (DECISIONS.md).
- **Email verification and recovery tested**: `AuthFlowTest` covers the full register → capture
  → verify → login path, single-use-token enforcement, and the unverified-registration-hijack
  case. "Recovery" (password reset for an already-verified account) is **not implemented** — no
  `POST /auth/password/reset` or equivalent exists. This is a real, user-facing gap (a locked-out
  organizer has no self-service recovery path today) and is named explicitly as a launch blocker
  in DECISIONS.md's "genuinely deferred" list from the V1 audit pass, not newly discovered here
  but worth restating in a security context: until this exists, account recovery is a manual,
  out-of-band operator action.

## Scramble payload exposure

**Verified**, already covered extensively by `ScrambleSecurityGapTest` before this session (error
responses, logs, caches, exports, unauthorized staff views — the M4 gap-closure pass) and by this
session's `EventStreamTest`/`EventStreamService` construction (the real-time channel carries
field *names*, never values, so there is no code path for scramble text to reach it). Re-verified
by reading `EventStreamService.publish()` this session: callers pass only `Set<String>`
`changedFields`; nothing in that class ever serializes an `Attempt`/`ScrambleAssignment` object or
field value onto the wire.

## Scramble dependency license, provenance, version

- **TNoodle** (`org.worldcubeassociation.tnoodle:lib-scrambles:0.20.0`) — **GPL-3.0, SaaS-vs-
  distribution licensing question remains explicitly open** (OD01 in DECISIONS.md). Not resolved
  by this review; a qualified legal reviewer must review before any public launch, exactly as the
  constraints on this task require. Not touched, not worked around, not silently assumed safe.
- **cubing.js** (`cubing` npm package, used for the 3D move guide) — version `0.63.8`,
  dual-licensed `MPL-2.0 OR GPL-3.0-or-later`. This product can elect MPL-2.0 (the more
  permissive of the two options the package itself offers), which is a weak (file-level) copyleft
  license compatible with a closed-source product shipping it as an unmodified dependency —
  **lower risk than TNoodle's GPL-3.0**, but still worth the same legal reviewer's sign-off before
  public launch, since "elect the permissive option of a dual license" is itself a decision with
  legal weight, not a purely technical one.
- **Everything else** (Spring Boot/Security/Data JPA/Validation/Mail/Actuator, Flyway, PostgreSQL
  JDBC driver, Next.js, React) is Apache-2.0/MIT/BSD-family — no copyleft obligations relevant to
  a closed-source product.
- **`npm audit` (web), triaged this pass (pre-integration review):** 5 high-severity advisories,
  all one chain, all devDependency-only:
  `eslint-config-next@16.3.8` (direct devDependency, matches the installed Next.js version) →
  `@next/eslint-plugin-next@16.3.8` → `fast-glob@3.3.1` → `micromatch@4.0.8` → `braces@3.0.3`
  (GHSA-vfj7-8cjw-p6xm, CVSS 7.5, stack-exhaustion DoS via deeply nested brace-expansion
  patterns, CWE-674).
  - **Production-reachable? No.** `npm ls braces micromatch fast-glob eslint-config-next
    --omit=dev` returns empty — none of the four packages exist anywhere in the production
    dependency tree; they are pulled in only by ESLint's own Next.js plugin, used at
    lint-time/CI, never bundled into `next build`'s output or loaded at runtime.
  - **Is the vulnerable code path even reachable here?** No. The DoS requires an attacker-
    controlled glob/brace pattern reaching `micromatch`/`braces`. In this chain, the only
    patterns ever passed are the plugin's own internal globbing of this repository's source
    tree during lint — never user input, never anything from an HTTP request.
  - **Fix available? No safe one.** `npm audit`'s own suggested fix is downgrading
    `eslint-config-next` to `14.2.35` — a semver-major downgrade two Next.js releases behind
    the one actually running (16.3.8), which would lose real lint coverage for this app's
    actual framework version. Worse, it would not even remove the vulnerability: `braces`'s
    advisory range is `<=3.0.3`, and `3.0.3` is the latest version `braces` has ever published
    (full registry version list checked, newest is `3.0.3`) — there is currently **no patched
    `braces` release to upgrade to**, at any `eslint-config-next`/`fast-glob`/`micromatch`
    version. Per this task's explicit instruction not to downgrade past a security fix or
    dismiss an advisory without evidence: **not downgraded** (it would be a real regression for
    zero actual security gain, since the same unpatched `braces` would still be pulled in by
    the older toolchain too) and **not silently dismissed** (documented here with the exact
    dependency path and reachability evidence above).
  - **Disposition:** accepted, dev-tooling-only risk — **not a merge/release blocker**. Re-check
    `npm audit` after any future `eslint-config-next`/Next.js upgrade, and again once upstream
    publishes a patched `braces` (track GHSA-vfj7-8cjw-p6xm).
  - No equivalent dependency-vulnerability scan was run against the Gradle dependency tree in
    this pass — worth wiring into CI (OWASP dependency-check or similar) as a follow-up.
- **Secrets external to source control**: verified — `application.yml`/`application-
  production.yml` contain no secrets, every credential (`TWISTMEET_DB_*`,
  `TWISTMEET_SCRAMBLE_ENCRYPTION_KEY`, mail credentials) is `${ENV_VAR}` interpolation with a
  dev-only default, and `ScrambleEncryptionService` now refuses to start in production without
  the real key set via environment (this session's earlier fix) rather than silently falling
  back to a file-embedded or ephemeral one.

## Data collection, retention, export, delete, public-name display, child-use policy

- **Public-name display**: `Event.publicNameMask` (this session) lets an organizer substitute
  "Competitor N" for a competitor's real display name on the public scoreboard — the one
  public-display privacy control the specs name.
- **Data export**: `GET /organizations/{orgId}/events/{eventId}/export.csv` exports event results
  (organizer-only). As of this acceptance pass, a personal-data subject-access export also exists
  for an individual asking "what data do you have about me": `GET /api/v1/me/export` (staff —
  profile, organization memberships, event staff assignments) and
  `GET /api/v1/guest/events/{eventId}/me/export` (competitor — entrant record, own attempts,
  corrections and help requests they filed). Both return only data the caller could already read
  through other endpoints; see `PrivacyService`/`PersonalDataExportAndDeletionTest`.
- **Data deletion/retention**: **the policy-independent mechanism is now built; the retention
  policy itself is still an open owner decision, tracked in `DATA_RETENTION_DECISIONS.md`.**
  `POST /api/v1/me/deletion-request` lets a staff member record an idempotent deletion request
  (`User.deletionRequestedAt`) without the app performing any automated deletion — no cascading
  delete, no anonymization, nothing destructive happens automatically. Acting on the request (what
  to delete or anonymize, after what retention window, how to preserve dispute/audit needs) is a
  manual operator step once `DATA_RETENTION_DECISIONS.md`'s open decision is made. This was a
  deliberate scope split from the earlier M6 pass's "not implemented" state: building a safe,
  policy-independent *request* mechanism is low-risk and valuable now; inventing a retention
  period or performing destructive deletion without a documented policy is not something this task
  does unilaterally.
- **Child-use policy**: no age-gating or parental-consent flow exists anywhere (registration asks
  only for email/display name). Whether this pilot's competitors include minors, and what that
  requires (COPPA in the US, GDPR-K in the EU, or similar depending on jurisdiction), is a legal/
  policy decision this task cannot make — recorded as an open question for the "legal/privacy
  jurisdiction(s) for the pilot" launch blocker already named in OPERATIONS.md.

## Privacy notice, terms, cookie notice, subprocessors, security contact

**None of these exist** — no privacy policy, terms of service, cookie notice, subprocessor list,
or published security contact. All five require actual legal/business content this task cannot
author on its own (a privacy policy is a legal document, not a code artifact) and a jurisdiction
decision to even scope correctly. Recorded as launch blockers, not attempted.

## Summary

| Item | Status |
|---|---|
| Cross-tenant tests (list/detail/mutation/export/realtime/guessed IDs) | **Verified** |
| Role matrix / hidden UI cannot substitute for denial | **Verified**, scramble-reveal gap fixed this session |
| Guest credentials (random/hashed/revocable/event-scoped) | **Verified** |
| CSRF | **Verified** |
| Session/CSRF cookie Secure+SameSite | **Fixed this session** |
| XSS defenses | **Verified** (React escaping + security headers) |
| Password policy | **Verified** (BCrypt, server-side `@Size(min=10,max=200)`) |
| MFA | **Not implemented** — launch blocker |
| Password recovery | **Not implemented** — launch blocker |
| Rate limiting | **Verified** for the named endpoints |
| Scramble payload exposure | **Verified** (no payload in error/log/cache/export/public/realtime) |
| TNoodle license review | **Explicitly open** — legal reviewer required before public launch |
| cubing.js license | Lower-risk (MPL-2.0 electable); still needs legal sign-off |
| Dependency vulnerability scan | **Triaged** — `npm audit`'s 5 high-severity advisories are one devDependency-only, non-production-reachable chain (`eslint-config-next`→`braces`, GHSA-vfj7-8cjw-p6xm) with no upstream patch yet; not downgraded (would regress Next.js lint coverage for zero security gain), not a merge blocker. Gradle dependency scan still **not run** |
| Secrets external to source control | **Verified** |
| Public name masking | **Built this session** |
| Personal data export | **Built this session** — `/api/v1/me/export` (staff), `/api/v1/guest/events/{eventId}/me/export` (competitor) |
| Data retention/deletion | **Mechanism built this session** (idempotent deletion-request flag, no automated deletion); **retention policy itself is still an open owner decision** — see `DATA_RETENTION_DECISIONS.md` |
| Child-use policy | **No policy exists** — jurisdiction-dependent, open |
| Privacy notice / terms / cookie notice / subprocessors / security contact | **None exist** — launch blockers |

Nothing in this review claims "worldwide launch ready." Several items above are explicit, named
launch blockers requiring a human/legal decision this task cannot make unilaterally.

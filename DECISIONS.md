# TwistMeet decisions log

This file tracks implementation decisions, defaults applied, and open questions, per
`product-docs/11-ai-build-playbook.md` (M0 exit criteria) and `00-authoritative-build-contract.md` §12.
Update this file whenever an ambiguity is resolved or a behavior deviates from a document.

## Status: M0 — repository inspection and planning only

No application code exists yet. This entry records the M0 findings and the defaults that will
govern M1 onward unless the product owner overrides them.

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

## Open items requiring product-owner decision

See the chat report for the full list; tracked here for traceability:

1. **Precision unit**: contract text is inconsistent between milliseconds and centiseconds in a few
   places (see Contradictions in the M0 report). Resolving to **integer milliseconds** everywhere,
   per `00` §4 ("Store and rank by exact integer milliseconds") and `08`/`09`, since these are the
   more specific/authoritative documents per the conflict-resolution order in `00` §0 preamble.
2. **Scramble generator library**: no specific library named. Must be selected and reviewed before
   M4 per `04` and `05` (Integrity gate). Not blocking M0–M3.
3. **Hosting/staging environment target**: not specified (cloud provider, domain). Not blocking
   early milestones; needed before M6 staging deployment.
4. **Brand/domain/name**: "TwistMeet" is explicitly provisional (`00` title, `README`). No action
   needed until public-facing naming matters (not before Phase 3).

## Deviations from documents

None yet. This section will record any approved deviation with rationale and the specific
document/section it diverges from.

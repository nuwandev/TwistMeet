# AI build and handoff playbook

This playbook is designed to be pasted into a coding agent together with the full `product-docs` directory. It makes the agent build in reviewable stages rather than generating a huge unverified code dump.

## How to hand off

1. Give the coding agent the ZIP and ask it to extract/read `00-authoritative-build-contract.md` first, then all numbered documents. Tell it these are requirements, not inspiration.
2. Point it at the actual repository. Require it to inspect the existing files, `README`, project guidance, dependency manifests, test setup, deployment setup and git status before editing. No secret values should be pasted into the chat.
3. Ask for a requirements map: requirement ID → screen/API/data/test. It should list conflicts, missing decisions and scope exclusions. The user-facing product defaults in the contract resolve normal implementation choices; ask only for blocking choices, not preferences already settled.
4. Require milestones below, running checks after each milestone and reporting exact commands/results. Do not approve broad public deployment based only on code generation.
5. Keep the docs in the repo and require the agent to update `DECISIONS.md`, API docs, migrations and user guides whenever implementation changes a specified behavior.

## Master prompt for the coding agent

> You are implementing TwistMeet, a small-club speedcubing event platform. The attached `product-docs` directory is the authoritative product and implementation specification. Read every document before editing. `00-authoritative-build-contract.md` controls conflicts. Preserve the user's existing repository conventions where compatible; the reference stack is Spring Boot Java modular monolith, PostgreSQL and Next.js/React TypeScript. Do not replace the stack or existing project without first reporting why.
>
> First inspect the repository, current branch/status, project instructions, build/test scripts, migrations, auth, deployment and existing UI. Do not expose secrets. Return a concise inventory, proposed module map, requirements traceability table, risks, assumptions and staged plan. Make no product-scope additions. Do not claim WCA sanction, certified timing, verified physical cubes or automatic cheating prevention.
>
> Implement in the milestones in `11-ai-build-playbook.md`. At each milestone: make a small coherent change; add meaningful tests for contract behavior; run relevant tests/build/lint; inspect rendered screens at mobile and desktop sizes; fix failures; update docs; report changed files, checks and remaining risks. Do not write tests that just mirror implementation; test scoring, authorization, state transitions, secrecy, idempotency and user flows.
>
> Security rules: tenant and role authorization server-side on every operation; guest credentials event-scoped and secret; scramble sequences never exposed to public/competitor endpoints in staff-prepared mode; no secrets in repository; append-only audit for result corrections and scramble reveals. Use migrations, not production auto-DDL. Keep result computation deterministic in integer milliseconds. Follow privacy-minimization and accessibility requirements.
>
> Build a staging-ready product and documented deployment procedure. Never use production credentials, create public infrastructure, publish a domain, or send invitations to real users without the product owner's explicit authorization. Before saying complete, execute the release checklist in `12-release-acceptance.md` and identify every unchecked item.

## Milestones and exit criteria

### M0 Repository and decision map

Output architecture inventory, requirement traceability and `DECISIONS.md`. Exit: no unknown project instructions; no unresolved decision blocking tenant/auth/data design; dev/test commands recorded.

### M1 Product foundation

Organization/staff auth; organization isolation; schema/migrations; event and guest registration; shared design system; sample seed event. Exit: owner can create an organization/event; guest joins by QR URL/code without a competitor account; authorization tests prove cross-tenant isolation.

### M2 Competition engine

Immutable ruleset snapshot; round/attempt state machines; scoring module and all vectors in `09`; roster management; manual judge entry and audit revisions. Exit: deterministic tests pass; no direct competitor edit; correction path preserves source history.

### M3 Organizer and competitor UI

Event wizard, roster, control room, judge entry, waiting room, results and error/empty/offline states. Exit: end-to-end flow from event creation through published result works at 360px and desktop; keyboard and screen-reader critical paths checked.

### M4 Scramble controls and 3D guide

Review dependency/license; generator validation; encrypted payload; role-scoped reveal; extra sequence; print; move guide; applied/checked log; self-scramble mode gated to casual phone timer. Exit: security tests demonstrate no leakage across APIs/HTML/cache/logging/public view; sample physical cube guide usability checked.

### M5 Advancement, display and history

Standings, tie/percentage preview, commit, public display, organization history, CSV export. Exit: concurrent update/idempotency tests pass; display is read-only and reveals only published fields.

### M6 Reliability, privacy and pilot

Offline judge-entry queue/printed fallback, backups/restore, monitoring, account deletion/export, terms/privacy placeholders reviewed, a11y audit, security check, pilot checklist. Exit: pilot uses actual people and physical timer; known risks documented and owner signs off.

## Coding agent operating rules

- Never silently resolve a product conflict. Log it and use stated default or ask one concise blocker question.
- Never report a successful test that was not run. Separate “not run”, “failed” and “passed”.
- Keep fixtures/data synthetic; do not import real children’s names or event data into tests.
- Do not use real organizer/competitor email addresses in demo seeds.
- Do not install an unreviewed scramble library, tracking SDK, payment SDK or unrelated plugin.
- Do not use a timer result to claim anti-cheat or latency compensation.
- Do not publicly deploy or send external communications without explicit approval.
- On implementation discovery that alters scope, stop and present the concrete impact before proceeding.

## Product owner review after each milestone

The owner checks real user behavior and visuals, not only source code: create an event; join via QR; add/remove/withdraw; enter results; request and decide a correction; close/advance; use display; simulate poor network; attempt unauthorized access; inspect mobile layout and guides. Record feedback in issues linked to requirement IDs. Accept/reject milestone before moving to next high-risk area.

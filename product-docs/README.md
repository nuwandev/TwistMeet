# TwistMeet product specification and build handoff

Version 2.0 · 4 October 2026

TwistMeet is a provisional working name for a small-club speedcubing event platform. This package is intended as an implementation handoff to a software team or coding agent. The authoritative contract defines a scoped, shippable V1; it does not claim the product is market-proven or WCA-sanctioned.

## Read first

1. [Authoritative build contract](00-authoritative-build-contract.md) — product boundary, V1 features, defaults, modes, rules, roles, architecture and completion definition. This is the controlling specification.
2. [Screen and component specification](07-screen-and-component-spec.md) — every required screen, permission, UI state and interaction.
3. [Data and API contract](08-data-api-contract.md) — entities, routes, security semantics, errors and real-time behavior.
4. [Scoring conformance](09-scoring-conformance.md) — exact calculations, tie/advancement rules and test vectors.
5. [Role guides](10-role-guides.md) — organizer, competitor, judge, scrambler, spectator and platform operator guides.
6. [AI build playbook](11-ai-build-playbook.md) — master prompt, milestone order and coding-agent constraints.
7. [Release acceptance](12-release-acceptance.md) — product/security/accessibility/operations launch gates.

## Supporting product documents

- [Product strategy and requirements](01-product-strategy.md)
- [Competition rules and integrity](02-rules-and-integrity.md)
- [Experience and workflows](03-experience-and-workflows.md)
- [System architecture and security](04-architecture-security.md)
- [Validation, roadmap and launch](05-validation-roadmap-launch.md)
- [Market and source review](06-market-review.md)

## Handoff note

Give the coding agent the entire package, not just the master prompt. Require it to inspect the actual repository and create a requirements traceability map before coding. Have it build and validate one milestone at a time. A deployable product still requires product-owner review, real pilots, security and accessibility checks, jurisdiction-specific privacy/legal review and explicit production launch approval.

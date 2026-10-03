# Release acceptance and operations checklist

A staging build is not a public launch. Every item is checked by a named owner, with evidence and date. Any critical/high security, data integrity, scoring or scramble secrecy defect blocks release.

## Product acceptance

- [ ] Event creation wizard creates a draft, validates time zone, defaults, rules and visibility.
- [ ] QR, URL and short code join work on current mobile browsers; rotation invalidates old code; throttling prevents enumeration.
- [ ] Guests can join without an account, see rules/mode and receive only event-scoped access.
- [ ] Organizer can add/edit/remove before start, withdraw after start, check in and lock registration without losing audit history.
- [ ] Physical timer mode supports judge result, +2, DNF, DNS, save receipt and correction audit.
- [ ] Phone mode clearly labels self-timed; current scramble is assigned only when unlocked; future scramble is inaccessible.
- [ ] Scramble roles, 3D guide, applied/checked status, extra scramble and print are usable; physical verification is human attestation only.
- [ ] All scoring vectors/property tests pass; standings are provisional until complete; tie and advancement math are visible and reproducible.
- [ ] Round close blocks unresolved attempts/corrections; advancement requires preview + explicit commit.
- [ ] Public display reveals only organizer-published fields and never future scrambles or private information.
- [ ] Organization history, event clone, CSV export, archive and deletion/retention behavior work.

## Security and privacy

- [ ] Cross-tenant tests cover list, detail, mutation, export, realtime and guessed IDs.
- [ ] Role matrix tested for every sensitive command; hidden UI cannot substitute for backend denial.
- [ ] Guest credentials are random, hashed at rest, revocable/expiring; join code cannot access a joined entrant’s account.
- [ ] CSRF/XSS/session/password/MFA/rate-limit checks complete; email verification and recovery tested.
- [ ] Scramble payload not present in competitor/public DTOs, browser cache, logs, analytics or error tracker for staff-prepared mode.
- [ ] Scramble dependency license, provenance, version and algorithm review recorded; secrets are external to source control.
- [ ] Data collection, retention, export, delete, public-name display and child-use policy reviewed for pilot jurisdictions.
- [ ] Privacy notice, terms, cookie notice if needed, subprocessors and security contact published.

## Reliability/accessibility

- [ ] Database migration forward and rollback strategy documented; production schema update is migration-based.
- [ ] Automated encrypted backup verified; restore drill to isolated environment passed.
- [ ] Venue network-loss test: saved vs queued state is clear; reconnect does not duplicate results; conflicts require human resolution.
- [ ] Printed fallback score sheet is usable; staff rehearsed it.
- [ ] Monitoring covers uptime, API errors, queue, database, backups, realtime disconnects and suspicious scramble access.
- [ ] Status page/support route and incident response owner exist.
- [ ] Keyboard-only, screen reader, 200% zoom, reduced-motion and contrast checks for join, judge entry, control room and display.
- [ ] At 360 CSS px no horizontal page scroll for core flows; targets meet touch-size baseline.
- [ ] Load test at 2× expected pilot concurrency and event-day realtime fan-out passes; actual expected scale recorded.

## Event-day checklist

### Organizer

- [ ] Confirm event timezone, registration roster, mode/timer source, rules, ties and advancement.
- [ ] Confirm judges/scramblers have correct event roles; test one sample entry and display.
- [ ] Prepare scramble sheets/extras in private; test cube guide on physical cube; avoid screen sharing future sequences.
- [ ] Download blank offline score sheets; identify who reconciles them.
- [ ] Confirm venue network, charging and display equipment; have support contact.

### Closeout

- [ ] Resolve pending result/correction states; confirm advancement and final placements.
- [ ] Publish only intended names/data; display timing source and provisional/final state.
- [ ] Export and archive; tell participants where results are visible and how to report a correction/privacy concern.
- [ ] Record operational issues and review within 48 hours.

## Deployment runbook outline

1. Provision staging/production database, application hosting, object storage if used, secret manager, domain/TLS and monitoring.
2. Set distinct environment variables; validate no production secrets in build artifacts/logs.
3. Back up current production database before migration; run reviewed migration; smoke-test health/auth/event/join/result/display.
4. Deploy web/API compatible versions; monitor error rate, latency, database connections, queue and realtime.
5. Roll back app if critical workflow fails; restore DB only under incident lead approval after assessing write loss. Prefer forward-fix migrations where safe.
6. For scramble exposure, disable reveal endpoint and affected round; rotate unused batches, preserve audit evidence, notify owner, investigate affected assignments and follow incident policy.
7. For event outage, direct event staff to the printed scorecard procedure; never ask them to share credentials or future scrambles publicly.

## Public launch sign-off

Product owner, technical owner, privacy/legal reviewer for launch regions, security reviewer and at least one pilot organizer sign off. The product owner explicitly authorizes public production deployment. Record scope, known limitations, ruleset version, scramble generator version, support coverage and rollback point.

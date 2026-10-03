# Validation, roadmap and launch

## First decision: validate before building the whole platform

The concept is promising enough to test, but the category is not empty. Before investing in accounts, histories and elaborate animation, run the proposed event with existing tools and interview actual organizers. The key unknown is whether they want a better day-of-event workflow strongly enough to switch, not whether QR registration or a timer can be coded.

## Discovery plan

### Interviews and observation

Talk to 8–12 organizers across clubs, schools, shops and workplace groups, plus 8–12 competitors/judges. Ask them to walk through their most recent event, including registration, scramble prep, timer handling, score corrections, connectivity, advancement and publishing. Observe one live event if possible. Ask what they currently use and what failed. Avoid pitching the full feature set before hearing their workflow.

### Competitive task test

Run the same small event setup task using RecordRanks/Cubing Contests, Cubing.co, WCA Live where appropriate, and a spreadsheet/manual scorecard. Compare time, errors, role clarity and mobile use. Do not infer feature gaps from marketing pages alone; verify in a real account/product.

### Prototype test

Test clickable prototypes for: 60-second join; judge result entry; correction request; scramble guide; control-room attention list; projector view. Include one-handed phone use, low vision, screen-reader/keyboard path and weak network.

### Go/no-go signals

Proceed to a pilot build if at least three independent organizers agree to run a real event using the prototype, identify a recurring pain not solved well by their current tool, and accept the proposed result-trust limitations. If they primarily need results/history, recommend an existing platform instead of building a competitor without a wedge.

## Roadmap

### Phase 0 — Evidence and product fit

- Interviews, event observation, competitive hands-on test, rules consultation, clickable prototype.
- Decide whether to focus on school/club event-day control, beginner education, or tournament administration; do not claim all at once.
- Outputs: validated problem statement, tested event rules, initial brand/domain check and scoped design.

### Phase 1 — One-room pilot MVP

- 3×3 only; organizer accounts; event and guest join; roster; one-round Ao5; judge entry for physical timer; DNF/DNS/+2; deterministic results; manual advancement; print sheets; public/private display; audit log; CSV export.
- Defer phone timing beyond a clearly labeled experimental casual mode. No camera, Bluetooth, payment or global rankings.
- Run 3–5 supervised pilots with an offline scorecard fallback.

### Phase 2 — Repeatable club events

- Multi-round presets, staff roles, correction workflow, scramble batch/assignment with reviewed generator, official-only 3D guide, event history, copy event, resilient sync, accessibility and localization baseline.
- Add phone self-timer only after device-latency and abuse tests, and label results accordingly.

### Phase 3 — Public beta

- Self-serve onboarding, moderation/reporting, account deletion/export, support process, service monitoring, incident response, backups/restore drills, privacy/terms/legal review, translated critical UI, abuse/rate-limit controls.
- Test in several countries and network/device conditions before broad promotion.

### Phase 4 — Optional advanced capabilities

Each is a separate business case: smart-cube integration, camera-assisted check, remote competition evidence, more puzzles, multi-event scheduling, organization subscriptions or payment. Validate demand and legal/vendor costs before committing.

## Release gates

### Rules gate

- Versioned ruleset reviewed by a knowledgeable cubing organizer.
- Boundary examples for averages, DNF/DNS, +2, ties, cutoff rounding and correction/reopen behavior independently verified.
- Public documentation accurately distinguishes unofficial event results from WCA results.

### Integrity gate

- Scramble generator/library provenance, license, algorithm and supported state checked by a qualified reviewer.
- Security tests prove future scramble secrecy across API, cache, logs, analytics, public display and competitor role.
- Extra scramble/correction path is rehearsed with real people; printed fallback is usable.

### Accessibility/usability gate

- WCAG 2.2 AA audit for join, judge entry, control room and display; keyboard and screen-reader testing; reduced-motion setting.
- First-time organizer can create and run a test event with no coaching.

### Reliability/security gate

- Cross-tenant authorization tests; rate limit and session checks; backup restore; network loss/reconnect test; conflict review; dependency/security scans; load test for expected pilot concurrency.
- No unresolved critical/high security issue. Named incident owner and support contact.

### Launch gate

- Privacy notice and terms reviewed for initial markets; organization admin controls tested; data deletion/export tested; subprocessor list published; status page and support channel working.
- Pilot organizers confirm rules, data visibility and timer mode were clear to competitors.

## Global rollout considerations

“Worldwide” is a later distribution goal, not one legal switch. Start with a small set of markets and adapt based on privacy, children’s-data rules, consumer protection, accessibility, tax/payment obligations if payments are ever added, data residency/transfer rules, language and time-zone handling. Make public events opt-in. Keep entry free during validation unless usage and support costs justify a price.

## Operations and support

- Publish event-day quickstart, organizer checklist, role guide, known limitations and offline score sheets.
- Maintain a status page and incident communication template.
- Support reports for incorrect result, exposed scramble, harassment/privacy concern, account access and event outage; route privacy/security incidents to a restricted channel.
- Establish correction/dispute retention policy and response target. Never ask users to submit sensitive personal data in public support channels.
- On each release, record rules version, scramble component version, migration, rollback, monitoring and pilot owner.

## Initial launch plan

1. Use the product internally at a small friendly event, with physical timer plus a judge.
2. Run two partner club pilots, one with weak venue internet; use printed backup.
3. Review every mismatch, correction and point of confusion; interview organizer and competitors within 48 hours.
4. Fix blockers and rerun; do not broaden feature scope to compensate for confusing core workflow.
5. Invite a small public beta cohort; publish limitations and collect opt-in feedback.
6. Decide from repeat use and organizer willingness to recommend/pay whether to continue, narrow, integrate with existing platforms or stop.

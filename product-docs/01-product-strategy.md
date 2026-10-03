# Product strategy and requirements

## Product purpose

TwistMeet helps a club, school, workplace, shop or group of friends run a small unofficial speedcubing event without spreadsheets, confusing rule interpretation or a dedicated technical operator. It joins registration, round configuration, attempt capture, score calculation, advancement, live monitoring and event history. The product succeeds when an organizer can create and run a fair, understandable first event with little training.

## Product thesis

There is room to test a simpler event-day experience focused on casual in-person organizers and beginners. Competition management and smart-cube products already exist, so “all-in-one” alone is not a defensible distinction. The testable differentiation is: quick guest registration; clear role-based event-day operations; beginner-friendly scramble preparation; reliable physical-timer entry; and an engaging live display, designed together.

## Audiences and jobs

| Audience | Job | Primary need |
|---|---|---|
| First-time organizer | Run a fun event without learning a competition platform | Guided setup, sensible presets, safe defaults, easy fixes |
| Club organizer | Run repeat events and retain results | Reusable settings, roles, history, exports |
| Judge / scorekeeper | Record attempts accurately | Fast entry, clear penalties, correction history |
| Competitor | Know where to go and what to do | Clear status, accessible instructions, visible personal results |
| Spectator | Follow the event | Public display with clear standings and no private data |

## Product principles

1. **Organizers can explain the rules before play.** Every event exposes its attempt format, timer method, penalties, ranking and advancement rules before registration.
2. **Human judgment remains visible.** Judges and organizers own rulings and corrections; the application records who changed what and why.
3. **Use the simplest appropriate mode.** Physical timer plus judge entry is the default for in-person trust. Phone timing is explicitly casual. Remote self-timing is its own event type.
4. **Protect future scrambles.** Only roles that need a scramble can see it, and only when operationally necessary.
5. **Do not confuse a guide with verification.** 3D visualization teaches notation and helps a scrambler follow a sequence; it does not certify a physical cube state.
6. **Works on ordinary phones and unreliable venue Wi-Fi.** Registration and public viewing should work on mobile web; scoring has a rehearsed offline fallback.
7. **Accessible by default.** Do not rely on color, animation, sound, precise gestures or a single language for essential status.

## Initial release boundary

### Included

- Organization workspace and organizer accounts; event creation, duplication and archive.
- Public/private event page; QR and short join code; guest competitor registration by display name; organizer add/edit/remove; registration lock.
- One event at launch: 3×3×3; one or more rounds; configurable attempt format from supported presets.
- Presets: Quick (qualifier and final), Single round, and Custom. The organizer sees expected duration and number of attempts.
- Physical timer/manual result mode as the default in-person workflow; attempts entered by judge with time, +2, DNF, DNS and note; correction log.
- Phone timer mode for unofficial casual events, with attempt lifecycle and organizer-visible status. Results are marked self-timed.
- Scramble generation from an audited component or trusted service, assignment by round and attempt, role-controlled reveal, printable scramble sheets, and a 3D move guide for officials.
- Ranking, Ao5/Mean of 3/Best-of formats as configured, advancement by top N or percentage, tie-break policy, live control room, public display, CSV export and event history.
- Event rule summary, accessible instructions, privacy notice, account deletion and data export controls.

### Explicitly deferred

- WCA-sanctioned competition administration or claims of official result validity.
- WCA ID integration, national/world records or global ranking claims.
- Camera-based cube-state recognition, smart-cube Bluetooth, predictive anti-cheat, automatic adjudication.
- Multi-event mega competitions, team events, arbitrary sports formats and user-authored executable rule logic.
- Built-in payments/prizes, public social network, chat, direct messaging and child profiles.
- Native iOS/Android apps, translation into every language, enterprise identity integrations.

## Functional requirements

### Organization and event

- An organizer can create a workspace, invite other staff by role, create an event, choose public/private visibility, set capacity/timezone, and copy a past event's configuration.
- An event has an immutable event ID, readable URL, expiring/rotatable join code, lifecycle state, selected ruleset version and audit trail.
- The organizer can export entrants and results, correct spelling, remove a duplicate, lock registration and archive an event. Destructive actions require confirmation and are audited.
- A finished event is read-only by default. A privileged correction reopens it with reason, identity and timestamp retained.

### Registration and identity

- Joining requires only a valid event code/link and a display name, subject to organizer-defined name policy. Email/phone is not required for a guest entrant.
- The app must explain that display names are visible to participants and spectators if the event is public.
- Duplicate-name handling offers a number suffix or organizer disambiguation; it must not silently merge people.
- A guest receives a short-lived, revocable event credential. It is not a durable account or an authentication secret suitable for cross-event identity.

### Rounds, attempts and scoring

- Support round formats only from a versioned, tested catalogue. Each attempt is stored raw; derived best/average/rank are reproducible from raw results and ruleset version.
- Time entry supports millisecond precision (display precision configured consistently); no floating-point arithmetic for canonical score calculation.
- Penalties are explicit: none, +2, DNF, DNS. The event's selected ruleset defines how they affect format results.
- Round completion and advancement are separate explicit actions. Do not automatically advance while unresolved attempts or correction requests remain.
- Ranking direction is lower-is-better for timed events. Ties follow the event's disclosed policy; default: average then best single, then shared rank if still tied. Do not invent a head-to-head solve unless configured and disclosed.

### Scrambles and roles

- Generate a batch for a round with more than enough predesignated extra scrambles; assign sequence IDs without exposing future sequences to competitors or public display.
- Record generator name/version, puzzle definition, generation time, assignment and reveal events. Never claim unpredictability or WCA equivalence until technically validated.
- The scramble sequence, 3D guide and print view are visible only to authorized scrambler/judge roles and only for assigned current attempts. The competitor-facing device timer must never reveal the sequence in judge-controlled mode.
- A scramble is consumed when the attempt is started or declared spoiled, according to event rules; replacement uses a new extra sequence and is audited.

## Non-functional requirements

- Mobile-first responsive web; supported current evergreen browsers. No install required for join, timer or display.
- Event operations should tolerate a brief network interruption. The UI clearly distinguishes saved, queued and unsynced results; no silent data loss.
- Proposed initial service objective: 99.5% monthly availability, excluding announced maintenance, with monitored errors and backups. Revisit after pilot usage.
- Score computation is deterministic and covered by independent rule examples; API commands are idempotent and protect against duplicate submission.
- Baseline security: MFA for staff, role checks server-side, tenant isolation, rate limits, secure sessions, audit logs, encrypted transport and encrypted backups.
- Accessibility target: WCAG 2.2 AA for registration, judge entry, organizer control and public display.
- Localization-ready strings, time zones, locale-aware dates, names in Unicode, and no locale-dependent score storage.

## Measures of success

Pilot hypotheses, not promises:

- At least 4 of 5 first-time organizers can create a basic event and explain its rules without help in a moderated test.
- Median time from opening event setup to registration-ready is under 5 minutes for a default event.
- A judge can enter a result in under 10 seconds in the standard path; correction requires no more than two screens.
- No unreconciled attempt/result mismatch in a pilot; every correction has an attributable audit entry.
- At least 3 independent clubs run a second event within 60 days; qualitative feedback identifies a reason to return.
- Competitor registration completion exceeds 90% on mobile during a supervised pilot.

## Product risks and assumptions

- Existing products may already meet the target event’s needs. Compare them hands-on before building a broad platform.
- Phone screen timing is not a reliable substitute for Stackmat or judge timing. Browser scheduling, touch latency, device differences and self-reporting constrain fairness.
- Official-quality scramble generation and secrecy have nontrivial implementation and operational requirements.
- Public event history raises consent, child-safety and data-retention questions. Default public results must be minimal and configurable.
- Event-day connectivity is not guaranteed. Offline behavior and printed fallback are launch requirements for in-person pilots.

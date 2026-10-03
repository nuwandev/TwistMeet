# Experience and workflows

## Experience goals

A first-time organizer should feel guided rather than forced to understand every regulation. Competitors should know the next action without reading a manual. Judges should spend attention on people and cubes, not navigate a dense admin panel. Spectators should see clear standings without seeing private contact details or future scrambles.

## Roles

| Role | Allowed actions |
|---|---|
| Workspace owner | Billing later; workspace settings; invite/remove staff; delete workspace |
| Organizer | Create/configure event, manage registration, assign roles, open/close rounds, approve corrections, publish/archive |
| Judge/scorekeeper | View assigned current scramble as allowed, update attempt status and result, request escalation |
| Scrambler | See assigned current scramble and guide, mark preparation/check complete |
| Competitor | Join, view own schedule/status/results, run self-timer only when enabled, request correction |
| Spectator | Read public event information and published results only |

One person may hold multiple roles, but the app should display which role is being used for a sensitive action. Require a second staff confirmation for selected high-impact actions when the organizer enables it.

## Organizer journey

1. **Create event:** name, date, time zone, venue/online, visibility, 3×3 event.
2. **Choose a preset:** Single round, Quick, or Custom. Show attempts, expected event duration and who advances in plain language.
3. **Choose result method:** Physical timer with judge entry (recommended); phone self-timing (casual); remote mode (later). Warn if selected method changes trust level.
4. **Review event rules:** format, time precision, penalties, tie policy, advancement, correction policy, privacy/public display. Preview as competitor sees it.
5. **Invite staff:** optional judges/scramblers; least privilege by default.
6. **Open registration:** QR, short code, direct URL. Live count and roster; add, rename, remove duplicate, lock/unlock.
7. **Prepare event:** generate scramble batch; print judge/scrambler sheets; test display; download offline score sheets; run a readiness checklist.
8. **Run event:** control room shows round, live roster states, attempts complete, pending help/corrections, unsynced devices and service status.
9. **Close round:** resolve exceptions, preview standings and qualifiers, confirm advancement.
10. **Publish and archive:** show podium and results; choose public/private history; export CSV; copy settings for next event.

## Competitor join and event-day flow

- Open QR/link or enter short join code.
- Read brief rules and visibility notice; choose display name; join as guest.
- See event status, check-in, group/station if assigned, and a concise “what happens next” instruction.
- In judge-controlled mode, do not show scramble notation or 3D scramble state. Show “Your puzzle is being prepared” and then “Call your judge / begin inspection” according to event procedure.
- In phone-timed casual mode, show the assigned scramble, accessible notation help, optional move-by-move 3D guide, timer and attempt status. The event is explicitly labeled self-timed.
- After an attempt, show submitted time and penalty; allow a correction request, not direct edit. Show accepted result and next action.

## Scramble station and 3D guide

- Present notation with readable spacing, move index, slow/normal speed, previous/next/replay, pause, reset and orientation controls.
- Explain notation in text: `R` right face clockwise; `R'` counterclockwise; `R2` half turn. State orientation convention at all times.
- Provide a keyboard-accessible list of moves, captions/labels and reduced-motion mode. Avoid requiring color alone; use face labels and patterns where practical.
- Provide current scramble ID, attempt, puzzle type and a “prepared and checked” action. The status is a human attestation, not a machine verification.
- Hide future scrambles; prevent accidental screen sharing by an obvious official-only page banner. Provide print layout with scramble ID and no competitor name by default.

## Judge entry

Fast path layout: competitor/station → round/attempt → time → penalty → confirm. Use large tap targets, numeric keypad, clear units, keyboard shortcuts only as optional accelerators, and an immediate undo window that creates an audit entry. Confirmation displays both raw time and adjusted result. Prevent duplicate attempt submission with an idempotency key and visible saved state.

Correction review shows original data, reason, requester, elapsed time and available actions. Accept/reject requires a short note. Replacement attempt shows the newly assigned scramble ID and prevents reuse.

## Organizer control room

Default view is action-oriented, not a data table full of configuration. It includes:

- Event and round status; event mode and rules summary.
- Competitor progress: waiting, preparing, ready, inspecting, solving (phone mode only), needs help, correction pending, complete, disconnected/unsynced.
- Round progress counts; filters for attention-needed; search competitor.
- Actions: pause intake, lock/unlock registration before event start, request judge, resolve correction, amend name, open/close round, preview advancement, publish.
- Service health and last synchronization time.

All status uses icon + text, not only green/yellow/red. Do not expose a competitor's device biometrics or location. Avoid public shaming; correction details stay staff-only.

## Public display

A shareable read-only page for TV/projector shows event/round, last-updated time, standings, personal bests where enabled, advancing competitors and podium. Motion is subtle, respects reduced-motion settings and never delays information. No join credentials, contact data, private notes, future scramble sequences or correction requests appear. Organizer can pause or hide publication.

## Offline and recovery flow

- At event readiness, organizer can download printable score sheets and a roster with attempt slots.
- Judge entry page shows queued vs confirmed writes. If disconnected, append signed local entries with unique IDs and timestamps; on reconnect, sync idempotently and flag conflicts for staff review.
- If browser storage is unavailable or lost, immediately instruct staff to use paper sheets. Never represent offline queue as server-saved.
- On conflict, preserve both submissions and route to authorized review; never pick one silently.

## Accessibility and localization

Aim for WCAG 2.2 AA. Support zoom, screen readers, focus order, keyboard operation, contrast, captions/labels, reduced motion and non-color status. Allow long names and right-to-left/localized text in layout. First language is English; design localization from day one. Time zone, date and decimal presentation are localized, but canonical values remain locale independent.

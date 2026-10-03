# Role guides for running an event

These guides are the V1 help-center baseline. In-product text should be shorter, with links to the full guide. Translate critical safety and rules text before entering a new market.

## Organizer quickstart

### Before registration

1. Create your organization account and verify your email. Create an event and choose its date, time zone, venue and visibility.
2. Pick a preset. For a small first event, use one round of Average of 5. For a final, choose Quick and confirm exactly who advances.
3. Choose `Physical Timer + Judge Entry` if you have a physical timer and a person judging each attempt. Choose `Competitor Phone Timer` only for a casual event; it is self-timed and varies by device.
4. Keep `Staff-prepared scrambles` selected for judge-controlled events. Add a scrambler and judge. Competitor self-scrambling is only for casual phone mode and gives competitors the scramble before they solve.
5. Review the rule summary: attempts, scoring, +2/DNF/DNS, tie policy, advancement, correction cap, public visibility and result source.
6. Open registration. Put the QR on screen or share the short code/link. Add people who cannot scan. Check for duplicate names; do not delete an entrant after attempts start—withdraw them with a reason.

### On event day

1. Use the readiness checklist: check staff roles, physical timer or phone mode, network, roster, scramble sheets, display screen, charged devices, and printed score sheets.
2. Lock registration before the first attempt. Confirm the roster and groups/stations.
3. Generate the scramble batch and print official-only sheets if needed. Keep future scramble pages away from competitors and spectators.
4. Start the round in Tournament Control. Judges record each result with its penalty. Watch `Needs attention`, corrections and unsynced states.
5. At round end, resolve every pending result and correction. Review the ranking and tie math. Preview the advancing list; commit only after checking it.
6. Publish results when ready. Check the public display for names and privacy. Export a CSV and archive after the event.

### If something goes wrong

- Wrong result: create a correction, enter why and preserve the original. Do not edit the database or delete the attempt.
- Scramble mistake: mark the assignment spoiled; do not reuse it. Assign an unused extra and have an official recheck.
- Internet outage: continue using printed score sheets. Record competitor, round, attempt, raw time, penalty, judge and time. When service returns, enter from paper and compare each row before publishing.
- Accidental phone start: competitor requests correction; judge/organizer decides under the published event policy. A timer reset is never invisible.
- Wrong advancement: stop before next round starts; use the logged reopen/recalculate process and tell affected competitors.

## Competitor quick guide

1. Scan the QR or enter the event code. Choose a display name. You do not need an account for guest participation.
2. Read the event mode and short rules. Your name and results may be public only if the organizer publishes them.
3. Check the waiting room for your group/station and next instruction.
4. In physical-timer mode, wait for the official with your prepared cube; the site will not show your scramble. Follow the judge’s instructions.
5. In phone-timer casual mode, the site gives your scramble and optional 3D guide. Read notation help if needed. Your result is labeled self-timed.
6. After a result is submitted, review it. If something went wrong, request a correction with a clear reason; do not make another attempt unless the organizer approves it.
7. Ask the organizer/judge for assistance if the app shows unsynced or unclear status.

## Judge and scorekeeper guide

1. Sign in and open only events assigned to you. Confirm round, mode, competitor and attempt before entering.
2. In physical mode, observe the timer and competitor. Enter the exact displayed time, then choose None, +2, DNF or DNS. Do not infer a penalty; follow the event’s disclosed rules and organizer instructions.
3. Review raw time and adjusted result preview. Save once. Wait for “Saved” confirmation. If offline, note the queued status or switch to paper; never assume a queued result is on the server.
4. If an error is found, use correction/escalation. Do not erase or overwrite the original. An organizer makes the final correction decision.
5. Keep future scramble sheets and the officials-only 3D guide out of public view. Mark applied and checked only after doing those actions physically.

## Scrambler guide

1. Open the assigned scramble for the correct round, group and attempt. Verify puzzle type and scramble ID.
2. Set the physical cube orientation shown. Follow one move at a time; use replay/next/previous if needed.
3. After the sequence, independently inspect the result against your procedure. The 3D picture helps explain moves but does not read the real cube.
4. Mark `Applied` and `Checked`; if another checker is required, ask them to confirm independently.
5. If the sequence was applied incorrectly or revealed to a competitor early, spoil it and tell the organizer. Never silently restart the sequence or reuse a scramble.
6. Keep sheets/screens protected until the round policy permits release.

## Spectator guide

The public page shows results the organizer has chosen to publish. Partial results may be provisional. Timing method is shown beside results. It does not expose future scrambles, correction notes, staff-only information or contact data. Ask the organizer if a result appears incorrect; spectators cannot edit it.

## Platform operator guide

Before public beta: review monitoring alerts, database backup completion, restore test, domain/TLS, outgoing email, rate limits, account recovery, incident contact, support mailbox, privacy/terms links, error redaction, scramble library version/license and production migration. On suspected future-scramble exposure, restrict scramble endpoints, rotate affected secrets/batches, preserve logs, alert the event owner and investigate. On a personal-data incident, follow the approved incident response and legal notification process for affected jurisdictions.

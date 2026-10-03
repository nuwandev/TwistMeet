# Competition rules and result integrity

## Purpose and status

This policy defines a straightforward rules engine for unofficial events. It borrows familiar speedcubing concepts but does not confer WCA sanction or record eligibility. WCA regulations are a living source; before each release, recheck the current rulebook and scramble guidance. Organizers remain responsible for the event rules they publish.

## Supported first-release formats

| Format | Attempts | Result rule | Use |
|---|---:|---|---|
| Best of 1 | 1 | Single adjusted attempt | Very short demonstration round |
| Best of 2 / Best of 3 | 2 / 3 | Best valid adjusted attempt | Short casual qualifier |
| Mean of 3 | 3 | Arithmetic mean of all three; any DNF/DNS makes mean DNF under WCA-style policy | Consistency round |
| Average of 5 | 5 | Remove one fastest and one slowest; mean the remaining three; one DNF/DNS may be worst, two make average DNF | Default for 3×3 rounds |

Do not advertise a format as WCA-compliant until the entire rule implementation and operational procedure have been checked against the current regulations. Format rules must be unit-tested on boundary cases and versioned.

## Attempt lifecycle

`Assigned → Cube preparation → Ready → Inspection (optional by event) → Running → Stopped → Judge confirmation → Accepted`

Alternative terminal paths are `DNS`, `DNF`, `voided by authorized correction`, or `replacement attempt assigned`. Every transition records actor, time, device/session and reason where applicable. Competitors cannot edit submitted results.

## Timer modes

### Judge-controlled physical timer (recommended in-person)

- The judge observes the attempt and operates or records the physical timer according to event rules.
- A scorekeeper enters the displayed result and penalty; a second-person confirmation can be enabled for finals or high-stakes club events.
- The site is the system of record for the submitted score, not the time-measuring instrument.
- Paper scorecards and a printed attempt sheet are available as outage fallback; results are reconciled by a named operator after service returns.

### Competitor phone timer (casual only)

- Label each result `self-timed` and state that touch/browser timing is not physically equivalent across devices and cannot establish independent official timing.
- Use a monotonic browser clock where available, minimize UI work during an attempt, and record client/build metadata for troubleshooting; do not use this to claim latency correction or anti-cheat certainty.
- Timer controls use a deliberate ready action; short accidental starts can be flagged, never silently erased. Any correction is a request to the organizer/judge.
- An organizer can choose to disallow these results from advancement or label the whole round as casual.

### Remote self-timed (later)

This needs its own published evidence model, replay/video policy, dispute process and abuse controls. Do not mix remote self-reported results into an in-person event without explicit disclosure.

## Scramble generation and handling

- A scramble is an algorithmically generated legal target state and a notation sequence, not an arbitrary list of random face turns. Validate the generator and legal move parser independently.
- Use a maintained, compatible, reviewed generator. Record exact version and parameters; test output distribution/state validity and notation parsing. Keep dependency/license review in the release checklist.
- Do not roll a homegrown generator for production without specialist review. If WCA-level equivalence is required, use only a method accepted for that purpose and obtain relevant guidance/permission.
- Generate round batches in advance with extras; encrypt at rest, restrict access by role, prevent sequence leakage in public APIs, logs, analytics, crash reports, browser caches and page source, and rotate join/event secrets separately.
- In judge-controlled in-person mode, competitors receive prepared, covered puzzles. Officials get the notation and optional guide. The sequence should not be published on competitor screens before the attempt.
- Scrambler follows the move guide, then independently checks the physical state. A visual 3D cube is instruction, not physical verification. Two-person verification is a stronger casual control; camera and smart-cube checks are future options, not guarantees.
- Expose completed-round scrambles only if event rules allow and only after all attempts using them have concluded. WCA rules may impose stricter limits; follow them for any explicitly WCA-aligned operation.

## Scramble assignment

A round uses attempt slots (1..N) and a shared scramble set according to the configured policy. In judged in-person mode, each competitor receives the appropriate attempt's prepared puzzle, and officials track assignment. The UI must clearly model groups and attempt number so a scramble is not accidentally reused or shown early. For finals, organizers may select one group and shared attempt scrambles if operationally practical. Do not imply every competitor should receive a unique scramble for every solve without checking the intended format and fairness policy.

## Corrections and extra attempts

- No self-service retry button. Competitor can flag a concern with a short reason: timer/device issue, scramble concern, interruption, or other.
- Judge/organizer can accept, reject, or hold the request. Require a reason for any invalidation or replacement; show pending requests in the control room.
- Accepted corrections preserve the original result in the audit log, mark it void, and assign the next unused extra scramble if the event policy grants a replacement. Never overwrite the old value or reuse a scramble by default.
- The published event rules define who can grant an extra attempt and whether it affects ranking. Provide a “no extra attempt” option for casual event policy.
- If an organizer edits a result after publication, mark the leaderboard corrected and surface an audit note.

## DNF, DNS, penalties and ties

- **DNF**: attempt started but not completed under event policy, or ruled invalid.
- **DNS**: eligible competitor did not start; do not infer this from a blank field until round close.
- **+2**: a judge-applied penalty added as integer centiseconds/milliseconds according to the chosen precision. Store raw time and penalty separately.
- A missing attempt is `pending`, not DNF/DNS, until a judge records a status.
- The ruleset computes result status and average. DNF/DNS handling must exactly match the selected version; display which attempt was discarded in Ao5.
- Default tie break: lower average, then lower best single; if still tied, shared rank. Event setup must disclose whether advancement uses shared rank, a tie-break attempt, or organizer decision. Avoid hidden tie policy.

## Advancement and round close

- Supported rules: everyone advances; top N; top percentage (rounded using explicit policy); optional time cutoff only after pilot validation.
- Freeze the advancing list only after all assigned attempts are resolved and correction requests are resolved or explicitly carried.
- Organizer previews the advancing roster before committing; record the action and ruleset version.
- Reopening a closed round requires reason, privilege and audit entry. Recalculate downstream rounds only through an explicit guided operation.

## Fairness and limits of claims

A software timer alone cannot prevent a competitor from viewing or manipulating their own device, rehearsing a scramble, receiving assistance, or using different hardware. Treat timing confidence as a property of the event procedure, not a marketing feature. Publish event mode, judge presence, timer source, scramble policy, result review and correction policy alongside standings.

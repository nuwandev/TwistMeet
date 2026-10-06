# Pilot event checklist

Practical, role-by-role checklist for running a real pilot event on TwistMeet, per
`product-docs/12-release-acceptance.md`'s "Event-day checklist" and this task's M6 scope. Each
item names the exact screen/action it maps to, so staff aren't guessing where to look.

## Before the event (organizer)

- [ ] Event created with correct timezone, date, venue label, and timer mode (`events/[eventId]`
      "Overview"/settings — `00` §6 lifecycle starts at `DRAFT`).
- [ ] Scramble policy decided: staff-prepared (default, recommended for judged in-person) or
      self-scramble (casual phone mode only) — set at event creation, cannot be silently assumed.
- [ ] Round(s) configured: format (BO1/BO2/BO3/MO3/AO5), advancement rule
      (everyone/top-N/top-percent), and tie policy. **If the organizer wants the tie-break-attempt
      option (09), it must be selected now, before registration opens — the API rejects it
      afterward** (`RoundService.validateTiePolicy`); the default (shared-rank advancement on a
      tie) is always available later if missed.
- [ ] Registration opened; join code/QR generated and tested on an actual phone browser, not just
      desktop (`events/[eventId]` → "Open registration").
- [ ] Judges and scramblers assigned their event roles (`events/[eventId]` staff assignments) —
      **confirm each assigned person can actually reach what their role needs**: a judge/scrambler
      with no organization membership and no assignment gets a 404, not a confusing error, so
      verify the assignment was made, not just requested.
- [ ] Test one sample entrant end-to-end before real competitors arrive: join via the code, judge
      records a result for them, result shows on the standings page. This is the single highest-
      value check — it exercises the join → judge → standings path all at once.
- [ ] Scramble sheets/extras prepared in private (`rounds/[roundId]/scramble`), away from
      competitor view; the 3D move guide tested on the actual physical cube that will be used at
      the venue, not just on-screen.
- [ ] **Avoid screen-sharing or projecting the scramble station screen** — it shows real,
      unrevealed notation; only the public display page (`public/[slug]`) is safe to project.
- [ ] Download and print the offline score sheet for every round that will run
      (`rounds/[roundId]/print-sheet`, linked from Tournament Control) — decide now who
      reconciles paper-recorded results back into the system if the venue network drops.
- [ ] Confirm venue network, device charging, and display equipment; have the support contact
      from OPERATIONS.md's "Launch blockers" (once assigned) written down somewhere staff can
      reach without the app.

## Judge role

- [ ] Knows their assigned round(s) and can reach judge entry
      (`rounds/[roundId]/judge`) without needing the organizer's help each time.
- [ ] Understands the 10-second undo window after saving a result (re-submits as a new revision,
      never erases the original — `07` S09) and that penalties (+2/DNF/DNS) and a note are all
      entered on the same screen before saving.
- [ ] Knows **not** to approve their own high-impact correction if dual approval is enabled for
      this event (`07` S09) — escalate to the organizer instead.
- [ ] Knows where to find the printed offline score sheet and how results recorded on it get
      re-entered afterward (judge entry per attempt, same as a live entry).

## Scrambler role

- [ ] Understands scramble reveal is restricted to the scrambler/judge **explicitly assigned to
      this event** — organization membership alone does not grant reveal access (this session's
      authorization fix). If an organizer needs to scramble themselves, they must self-assign the
      role first (`rounds/[roundId]/scramble` offers a one-click "Assign myself as Scrambler"
      button exactly for this case).
- [ ] Knows the applied/checked workflow order (apply, then check; an independent second checker
      is optional but must be a different person for the "independent" toggle to mean anything).
- [ ] Knows what to do if a scramble is spoiled (public exposure, misapplication): use the
      "spoil" action, which burns the sequence and assigns an unused extra automatically — never
      manually reuse or hand-write a replacement sequence.

## Competitor role

- [ ] Can join via QR/URL/short code without creating an account, and sees their event-scoped
      status, current attempt, and personal result view (`e/[eventId]`).
- [ ] Knows how to request help/a judge **before** a result is recorded (the "Request judge /
      help" button, shown while an attempt is still pending) versus requesting a **correction**
      **after** a result is recorded (a separate flow, from the result view) — these are
      deliberately different actions for different situations, and staff should be able to
      explain the difference if asked.
- [ ] In self-timed/casual mode: understands the current scramble unlocks only when it's their
      turn, and no future scramble is ever visible ahead of time.

## Event-day procedures

- [ ] Progress and exceptions are visible on Tournament Control (`events/[eventId]/control`) —
      confirm the connection-state badge shows "Live" (not stuck on "Connecting…"/
      "Reconnecting…") before relying on it during a live round; if it's stuck, the page still
      works by manually reloading, it just won't auto-refresh.
- [ ] Round close is blocked while any attempt or correction is unresolved — this is enforced by
      the API, not just a UI suggestion; don't look for a way around it, resolve the item instead.
- [ ] Advancement: preview before committing (`rounds/[roundId]/advancement`), confirm the
      entrant count and any pending tie-break attempt is resolved — commit is blocked while a
      tie-break is pending, by design.

## Closeout (organizer)

- [ ] Every pending result/correction/help request resolved (`events/[eventId]/corrections` —
      now shows both correction requests and help requests in one place).
- [ ] Advancement committed for every round that needed it; final placements confirmed on
      standings.
- [ ] Decide what to publish: toggle "Public name masking" first if competitor names should be
      anonymized on the public scoreboard, then Publish (`events/[eventId]` "Publishing" section).
      Display mode/timing source is visible on the public page automatically.
- [ ] Export CSV and mark the event Completed once every round is closed
      (`EventService.complete()` requires this); Archive when the event is fully wrapped up —
      archiving is read-only afterward except reads/export/audit, so don't archive before you're
      certain no further corrections are needed (reopen is available from `COMPLETED`, not from
      `ARCHIVED`).
- [ ] Tell participants where results are visible (the public link) and how to report a
      correction or privacy concern after the fact — this needs the support-contact decision from
      OPERATIONS.md's launch blockers to have a real answer.
- [ ] Record any operational issues from the day and review within 48 hours, per `12`'s closeout
      checklist — see OPERATIONS.md's "Incident response" for the scramble-exposure and
      event-outage procedures specifically, if either came up.

## Sign-off evidence to keep

For each pilot event, keep (even informally — a shared doc is enough at this scale):
- The ruleset/scoring version in effect (from the round's `rulesetVersion` field, visible via the
  API/standings).
- The scramble generator and version used (TNoodle `0.20.0`, per DECISIONS.md — note this stays
  an open licensing-review item, not yet legally cleared for this use).
- Who ran the event-day roles (organizer/judges/scramblers) and whether every item in this
  checklist's "Before the event" section was actually completed, not just available.
- Any deviation from this checklist and why — if something was skipped under time pressure, write
  down what and why, so the review within 48 hours actually has something concrete to review.

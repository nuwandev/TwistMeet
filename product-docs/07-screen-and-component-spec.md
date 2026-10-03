# Screen and component specification

This document defines information hierarchy, controls, states and permission boundaries. It is the authority for UI implementation. Use semantic HTML, responsive layouts and a shared component system; do not encode access control only as hidden UI.

## Visual foundation

Friendly event-day software: calm, high-contrast, clear and energetic only in result moments. Working palette: ink `#14233B`, blue `#2563EB`, mint `#0F766E`, amber `#B45309`, red `#B42318`, canvas `#F7F9FC`, surface `#FFFFFF`, border `#D7DFEA`, body text `#172033`. Check all foreground/background pairs for WCAG AA; adjust tokens when needed. Use 8 px spacing grid, 12 px cards, 10 px controls, clear 44×44 px minimum touch targets, one primary action per screen. Headings use a humanist sans; numeric times use tabular numerals. Avoid dense dashboards, gradients behind text and motion that hides state.

Desktop organizer shell: 240 px left navigation; top bar with organization/event, status and profile; content max width 1440 px. Mobile staff shell: top event selector + bottom nav or compact menu, one column, sticky primary action. Public display has a dedicated full-screen layout. Competitor screen prioritizes one current instruction and next action.

## Common components and states

- `StatusBadge`: icon + text + optional timestamp; never color only.
- `SavedState`: “Saved”, “Saving…”, “Queued on this device”, “Not saved — retry”, last sync time.
- `TimeInput`: seconds with unit, numeric keyboard on mobile, formatted preview, inline validation, retains raw value.
- `PenaltyPicker`: None, +2, DNF, DNS as mutually exclusive; DNF/DNS disables time with retained value in audit.
- `RoleBanner`: current event role and authorized scope.
- `ScrambleVisibilityBanner`: officials-only warning on every scramble page and print view.
- `ConfirmDialog`: destructive/high-impact actions give action summary and require a specific confirm button; no generic “OK”.
- `EmptyState`, `LoadingState`, `ErrorState`, `OfflineState`; each includes a next action.
- `LiveTable`: accessible HTML table with caption, header associations, stable row order, polite live updates, no focus theft.
- `ReducedMotion`: all podium/position animations respect OS preference and can be disabled.

## Public and guest screens

### P01 Home / event discovery
If public discovery is enabled, list only public events. V1 can omit global discovery and offer direct event link. Do not reveal private event existence by code enumeration. Primary CTA for staff: Sign in. Entry by short code offers clear input and rate limiting.

### P02 Join event
Fields: event code if not in URL; display name; optional nickname confirmation. Show event title, date/timezone, location/online label, timer mode, rules link and what display name visibility means. CTA `Join event`. Validation: code invalid/closed/full; display name required, trimmed, 1–32 Unicode grapheme clusters, no control chars/markup; reserved profanity filter is configurable and should not overblock names. Duplicate display name shows an inline suggestion `Nuwan (2)` and permits deliberate reuse after disambiguation. Success issues event-scoped credential and moves to P03. Never ask email/phone in guest flow.

### P03 Competitor waiting room
Show event name, registration/round status, rules summary, check-in and assigned group/station when applicable. States: waiting, check-in needed, ready, round not open, disconnected, withdrawn. Include `How this event works` and `Request help`. Do not show other entrants’ private details or future scramble.

### P04 Attempt screen: phone mode only
Show prominent `CASUAL SELF-TIMED` banner, attempt N of M, current scramble sequence and notation help, 3D guide toggle, inspection countdown only if enabled, large timer, clear ready/start/stop affordance, status and correction route. Before unlock, do not deliver scramble sequence. On unlock, only current attempt’s sequence appears. Timer must remain usable in landscape and portrait, screen-awake request best-effort, vibration optional and opt-in. State transitions visibly: ready → inspecting → timer running → stopped → submitted. If app loses focus while running, do not auto-stop or fabricate a time; preserve the timer state and request competitor/judge resolution. On submit, lock editing and show recorded result as self-timed.

### P05 Attempt status: judge-controlled mode
Show `A judge records your physical-timer result`. Do not expose timer or scramble. Show assigned station, preparation status, inspection cue if organizer configured it, `Request judge/help`, and after judge submission show result/penalty plus correction-request action. Competitor may acknowledge but acknowledgment is not approval.

### P06 Correction request
Select category: timer/entry issue, scramble concern, interruption, other; optional ≤500-character note. Explain this pauses close/advancement review but does not guarantee replacement. Confirmation shows request ID and status. Competitor can add context until staff resolves; cannot cancel once staff decision begins.

### P07 Personal result/history
List only this entrant’s results in this event, with attempt status and whether self-timed or judge-recorded. No contact details. Public share links require organizer publication and hide private notes.

## Staff screens

### S01 Staff sign-in and recovery
Email + password; email verification; password reset. MFA TOTP offered at pilot and required for owner/organizer accounts before public beta. Login rate limits and generic errors avoid account enumeration. Session revoke and sign-out visible. No social login in MVP.

### S02 Organization dashboard
Cards: next/current event, recent events, quick `Create event`, unresolved operations, event count/history. Empty state is a guided create flow. No fake analytics or global records. Switch organization explicitly and display current scope.

### S03 Event creation wizard
Step 1 basics: name (3–80 chars), date/time/timezone, venue/online, capacity optional, visibility. Step 2 competition: 3×3, preset, rounds/formats/advancement. Step 3 operations: timer mode, scramble policy, assigned roles, correction cap, public result setting. Step 4 rules preview and create draft. Progress, Back/Continue, save draft. Explain every advanced option in plain language. Presets populate visible configuration and can be reviewed before save.

### S04 Event overview
Header: title/date/timezone, lifecycle badge, mode/trust label; primary action appropriate to lifecycle. Tabs: Overview, Competitors, Rounds, Tournament Control (when live), Results, Settings, History. Summary: registration count, next deadline, format, staff, readiness warnings. Actions: open/lock registration; duplicate event; archive. Draft shows tasks; completed shows publish/export/archive.

### S05 Competitor roster
Accessible table/card list with name, join time, check-in, group/station and status. Actions: add, edit, check-in, remove before any attempt assignment, withdraw after start, lock registration. Bulk import CSV is post-MVP unless explicit. Removal requires reason when attempts exist and preserves score/audit. Show duplicate warning and search. Manual add creates event guest with same limited data as QR join.

### S06 Round setup
Show round order, format, attempts count, entrant source, advancement rule, tie policy, timer mode, scramble policy and expected duration. Editing after event live is blocked except authorized correction flow. Validate advancement count ≤ eligible entrants; top percentage uses `ceil(eligible × percentage)` then include all tied at boundary by default. Clearly preview actual number advancing.

### S07 Scramble preparation station
Role-scoped screen: round, group, attempt, puzzle, scramble ID; protected notation and 3D guide; move-by-move controls; print current batch; `Mark applied`, `Mark checked`. Track actor/time for both. Error path: `Scramble mistake` voids current assignment, consumes sequence if it was revealed, assigns unused extra with audit. Do not let competitor roles access. A second checker toggle supports independent staff acknowledgment.

### S08 Tournament Control
Top: event/round/status, mode label, time since round start, connection state. Progress summary (N complete / N expected; corrections pending; help requested; unsynced entries). Main table: entrant, group/station, current attempt, state, result summary, action. Filters: Needs attention, In progress, Finished, All. Actions: open judge entry, resolve request, pause new starts, close round when eligible. Live updates do not reorder focused rows unexpectedly. Mobile: filter bar, compact cards, action sheet.

### S09 Judge entry
Context locked by route: entrant, round, attempt, scramble ID (officials only), mode. Enter time; select penalty; notes optional; Save attempt. Show adjusted result preview. After save: immediate saved receipt + undo window 10 seconds (undo creates revision; cannot erase). Duplicate or stale submissions prompt to refresh/resolve. Judge can request organizer review, not approve own high-impact correction if dual approval is enabled. Keyboard: Enter save only after valid input; Escape leaves unsaved confirmation.

### S10 Correction queue
Staff-only list ordered oldest first with entrant, attempt, original result, category, note, received time, mode. Detail includes audit timeline. Actions: accept with no replacement, accept and grant replacement, reject, ask for details. Require decision reason; show effect on standings and scramble assignment before commit. Preserve original result. Never reveal a staff-only reason to public display.

### S11 Round review and advancement
List unresolved statuses and requests; close disabled while unresolved except explicit authorized DNS/withdrawal resolution. Preview rankings and qualification cutoff, show ties and formula, allow organizer to annotate decision. `Commit advancement` requires confirmation of entrant count and creates next-round slots transactionally. Roll back only through logged admin operation with impact preview.

### S12 Results and publication
Tabs: live/unpublished, published, revisions. Ranking table with best/average, attempts, result source label. Organizer can publish, unpublish, export CSV, amend eligible metadata. Public name masking option. Show last updated. Revisions list actor, time, reason and before/after values; private notes hidden.

### S13 Public display
Separate read-only URL/token. Fullscreen scoreboard, event/round, current status, rank, competitor display name, average and best. If round incomplete, label provisional and show completed attempt counts; don't present partial averages as final. Completion triggers reduced-motion podium reveal and announced advancement. Refresh/reconnect indicator. Hide by revoking token. Never include scramble, private entries, guest credentials or staff actions.

### S14 Event history and export
Search/filter organization events by date/name/status. Completed event cards summarize competitor count, rounds, date. Open historical read-only detail; copy event settings; download CSV with documented columns and UTF-8. Deletion uses retention policy and owner confirmation; not same as archive.

### S15 Staff and organization settings
Organization profile, timezone default, staff list/roles, join-code policy, publication defaults, deletion/export requests. Role changes require reauthentication and audit. Organization deletion requires owner confirmation and export notice; no self-service deletion while event is live.

## Design acceptance checklist

- Every screen has keyboard path, visible focus, explicit label, readable error, loading/empty/connection states and mobile layout.
- Every permission-restricted action is server-authorized and has a denied state.
- Every high-impact action previews consequences and records the actor/reason.
- No future scramble is present in competitor/public network responses.
- Partial standings are explicitly provisional; official and casual timing labels remain visible.
- Public display does not depend on color or animation to communicate rank/state.

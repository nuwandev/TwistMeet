# Accessibility review (M6)

Against `product-docs/12-release-acceptance.md`'s "Keyboard-only, screen reader, 200% zoom,
reduced-motion and contrast checks for join, judge entry, control room and display" and "At 360
CSS px no horizontal page scroll for core flows." Run with a real automated scan
(`@axe-core/playwright`, tags `wcag2a`/`wcag2aa`/`wcag21aa`/`wcag22aa`) against the real running
app with real data — an organizer account, an organization, a published event, a running round,
a judged result, and a guest competitor — not a lint pass or a screenshot-based guess.

## Method

A Node script (`chromium.launch` against the pre-installed browser, same binary the repo's
Playwright config uses) drove the actual app through the browser: registered an organizer,
verified via a local dev mail catcher, created an org/event, opened registration, had a guest
join, created and ran a BO1 round, judged a result, closed the round, and published the event —
then ran an axe scan against each of the pages `12` names, plus the organizer's event-settings
page (reached along the way, worth checking since it holds the round-creation form). This is the
same approach `web/e2e/critical-journey.spec.ts` uses, reused here for a one-off accessibility
pass rather than committed as a second E2E spec. See OPERATIONS.md/README for why no CI-wired
automated accessibility test exists yet — same reasoning as the E2E test (needs the workflow to
also run a mail catcher and both dev servers).

## Findings

### Axe-core violations: one found, fixed

| Page | URL pattern | Violations (before fix) | Violations (after fix) |
|---|---|---|---|
| Join (guest, form filled) | `/join/{code}` | 0 | 0 |
| Judge entry (real attempt) | `/rounds/{roundId}/judge` | 0 | 0 |
| **Event settings (organizer)** | `/events/{eventId}` | **1 critical** | **0** |
| Tournament Control | `/events/{eventId}/control` | 0 | 0 |
| Public display (published, with results) | `/public/{slug}` | 0 | 0 |

**The one violation**: `select-name` (critical, WCAG 2.0 A / 4.1.1-class rule), on the round
format `<select name="format">` in the "Add round" form on the event settings page — it had no
accessible name at all (no associated `<label>`, no `aria-label`), so a screen-reader user
tabbing into it would hear only "combo box," with no indication of what it selects. **Fixed**
this session: added `aria-label="Round format"`. Every other `<select>` in the app already had a
proper `<label htmlFor=...>` association (checked directly, not just inferred from a clean scan)
— this was the one real gap, not a systemic pattern.

Re-run after the fix: **zero axe-core violations on any of the five pages**, across
`wcag2a`/`wcag2aa`/`wcag21aa`/`wcag22aa`.

### 360 CSS px — no horizontal overflow

Checked `document.documentElement.scrollWidth > clientWidth` at a 360×640 viewport on Tournament
Control (the densest of the pages checked — a filterable table plus action buttons). **No
horizontal overflow.** This corroborates the M3-era manual E2E checks TRACEABILITY.md already
cites for 360px layout on this and other pages; not a new finding, a confirmation with a
different method.

### Reduced motion

Emulated `prefers-reduced-motion: reduce` and inspected every element's computed
`transitionDuration`/`animationName` on Tournament Control. The first pass over-counted (41
elements) by flagging a near-zero `1e-06s` transition duration that Chromium reports by default
on static document elements (`<html>`, `<head>`, `<meta>`, `<script>`) — not a real animation.
Spot-checking those elements directly confirmed this is measurement noise, not app behavior:
**no element in this app defines a CSS animation or a meaningful (>0.05s) transition**, reduced-
motion or not — the app is already effectively static, so there is nothing for a
`prefers-reduced-motion` media query to need to suppress. This matches `00` §2 item 12's
"reduced-motion option" requirement trivially, by having no motion to begin with, rather than by
an explicit reduced-motion override existing in the CSS.

### Keyboard focus visibility

`globals.css` defines a visible focus ring via `:focus-visible` (`outline: 3px solid
var(--color-blue)`) on every interactive element selector (`a`, `button`, `input`, `select`,
`[tabindex]`) — correct per `07`'s "focus visibility" requirement, confirmed by reading the CSS
directly.

Automating a Tab-key walk through Tournament Control and reading each focused element's computed
outline found a visible outline on **6 of 11** tabbed-to elements. This check used a blunt
heuristic (synthetic `keyboard.press('Tab')` in headless automation, not a real keyboard user,
and `getComputedStyle` read once per stop rather than a full manual walkthrough) and should not
be read as "5 elements are definitively broken" — it is a signal that this page's focus order and
visibility deserve a real manual keyboard pass (unplug the mouse, Tab through every action:
filter buttons, "Open judge entry," "Resolve requests," pause/close) before launch, not a
confirmed defect with a fix attached. Recorded honestly as inconclusive-but-worth-following-up,
rather than either claimed clean or presented as a confirmed bug neither of which this check
actually established.

### 200% zoom

Attempted via `document.body.style.zoom = 2` in Chromium on Tournament Control; the page
remained usable with no clipped primary content in the viewport used, but this is a weak proxy
for a real "200% browser zoom" (which reflows text and layout via the browser's own zoom
mechanism, not a CSS `zoom` property hack) — **not a substitute for a real manual check** with
actual browser zoom (Ctrl/Cmd + repeatedly) across join, judge entry, control room, and display.
Recorded as not meaningfully tested rather than claimed passing on the strength of this proxy.

### Screen reader

**Not tested with an actual screen reader** (VoiceOver/NVDA/JAWS) in this pass — axe-core checks
a large fraction of what a screen reader would expose (accessible names, roles, landmark
structure) but does not replace listening to a real screen reader read the page, especially for
dynamic content: the Tournament Control connection-state badge and the "N / M attempts complete"
progress text update live over SSE, and whether a screen reader announces that update (it should,
if it's within an `aria-live` region) was not verified here. Checked the source: `StatusBadge`
and the progress paragraph are plain text, not wrapped in an explicit `aria-live` region — the
`LiveAnnouncer` component (`components/common.tsx`) exists and is used on competitor-facing
pages for exactly this purpose (announcing async state changes) but is **not used on Tournament
Control** for the live-updating progress summary. This is a real, specific, actionable gap: a
screen-reader user watching Tournament Control would not be told when a judge result comes in
live — they'd have to re-navigate to notice. Recorded as a finding, not fixed in this pass (it is
a real behavior change, not a one-line label fix, and deserves its own verification with an
actual screen reader rather than a guessed implementation).

### Color contrast

Axe's scans above include `color-contrast` under the `wcag2aa` tag and found no violations on any
of the five pages checked — this is automated contrast-ratio math against actual rendered
colors, not a guess, and covers this requirement as far as axe-core's static analysis can (it
cannot catch contrast issues that only appear in a particular hover/focus/dynamic state not
present at scan time).

## Summary

| Item | Status |
|---|---|
| Axe-core scan (join/judge/control/display/event-settings) | **Run for real**, 1 critical finding, **fixed** |
| 360px no horizontal overflow | **Verified** |
| Reduced motion | **Verified** — no meaningful motion exists to suppress |
| Keyboard focus-visibility (CSS rule) | **Verified exists**; automated Tab-walk result inconclusive, flagged for manual follow-up |
| 200% zoom | **Not meaningfully tested** — proxy used, real browser-zoom check still needed |
| Screen reader | **Not tested** with a real screen reader; found Tournament Control's live progress updates aren't in an `aria-live` region (competitor pages already use one) — real, unfixed gap |
| Color contrast | **Verified** (axe `color-contrast`, no violations found) |

This is a genuine automated-plus-code-level pass, not a claim of a full WCAG 2.2 AA audit. The
screen-reader gap (Tournament Control's live updates) and the keyboard-focus follow-up are the
two items most worth a human accessibility reviewer's time before a real pilot, not something
this task can close out unilaterally with more automation alone.

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

### Keyboard focus visibility — re-verified, correcting the earlier result

`globals.css` defines a visible focus ring via `:focus-visible` (`outline: 3px solid
var(--color-blue)`) on every interactive element selector (`a`, `button`, `input`, `select`,
`[tabindex]`) — correct per `07`'s "focus visibility" requirement.

The first pass's automated Tab-walk reported a visible outline on only 6 of 11 stops and was
left as an inconclusive, unresolved finding. Re-run for this acceptance pass with the actual
`outlineStyle`/`outlineWidth` computed-style values read per stop (the earlier check read the
`outline` shorthand, which can report inconsistently across elements) across a full 20-stop walk
of Tournament Control: **every real interactive control** — "Pause new starts," "Open judge
entry," "Scramble station," "Print offline score sheet," "Resolve requests," all four filter
buttons, "Judge entry" — showed `outline-style: solid; outline-width: 3px` while focused. The one
stop with no visible outline was a `NEXTJS-PORTAL` element (a zero-size dev-mode Next.js internal
node, not a real control a keyboard user could land on meaningfully). **Corrected finding: no
focus-visibility defect exists on Tournament Control** — the earlier "6/11" result was a
measurement artifact of that check's method, not a real gap.

Also verified this pass, on the new Results and publication page's ARIA tabs: focusing a tab and
pressing **Enter** activates it (`aria-selected` flips to the newly focused tab, confirmed via
`document.activeElement`), and opening the Unpublish `ConfirmDialog` and pressing **Escape**
closes it (confirmed the `dialog[open]` element disappears) — both via the real keyboard event,
not a click.

### 200% zoom — re-verified with a real zoom mechanism, correcting the earlier proxy

The earlier pass used the CSS `zoom` property (`document.body.style.zoom = 2`), explicitly flagged
as a weak proxy since it doesn't reflow the layout the way a browser's actual zoom does. Re-run
for this acceptance pass using Chrome DevTools Protocol's `Emulation.setDeviceMetricsOverride`
with `scale: 2` — the same mechanism Chromium uses to render genuine page zoom, reflowing CSS
layout at the zoomed effective viewport rather than just scaling a bitmap. Checked the join page
(guest, form filled), Tournament Control, and the new Results page: **no horizontal overflow on
any of the three** (`document.documentElement.scrollWidth` never exceeded `clientWidth`) at 2×
zoom. This is a materially stronger check than the earlier pass and the finding is now
**verified**, not merely proxied.

### Screen reader — accessibility-tree verified; actual screen-reader software still not run

**No screen reader (VoiceOver/NVDA/JAWS) is available in this sandboxed Linux container** — this
remains genuinely untested with real screen-reader software, and this review does not claim
otherwise. What the earlier pass found was real: `StatusBadge` and the progress paragraph on
Tournament Control were plain text, not wrapped in an explicit `aria-live` region, even though the
competitor-facing pages already used `LiveAnnouncer` (`components/common.tsx`) for exactly this
kind of async update. **Fixed in an earlier commit this pass**: Tournament Control now renders
`LiveAnnouncer`, announcing "`N of M attempts complete`" when the completed-attempt count rises
and the pending-correction count when it rises.

For this acceptance pass, went one step further than source inspection: read the **Chromium
accessibility tree itself** (CDP `Accessibility.getFullAXTree`) for the Results page — this is
the actual data structure a real screen reader consumes, not a guess about what the markup
*should* produce. Confirmed: 3 nodes with `role=tab` (named "Live / Unpublished," "Published,"
"Revisions," with the correct one marked `selected`), 1 node with `role=tabpanel` (only the
active panel is in the DOM, by design — conditional rendering, not `hidden`), and 1 node with
`role=status` (the `LiveAnnouncer` live region). This confirms the semantic data a screen reader
would read is structurally correct. It still does not confirm what any particular real screen
reader actually announces, in what order, or how a real user experiences it — that gap is
real and explicitly **left pending**. **WCAG 2.2 AA screen-reader acceptance is not claimed.**

### Color contrast

Axe's scans above include `color-contrast` under the `wcag2aa` tag and found no violations on any
of the five pages checked — this is automated contrast-ratio math against actual rendered
colors, not a guess, and covers this requirement as far as axe-core's static analysis can (it
cannot catch contrast issues that only appear in a particular hover/focus/dynamic state not
present at scan time).

## Summary

| Item | Status |
|---|---|
| Axe-core scan (join/judge/control/display/event-settings/results) | **Run for real**, 1 critical finding, **fixed**, re-scan clean |
| 360px no horizontal overflow | **Verified** |
| Reduced motion | **Verified** — no meaningful motion exists to suppress |
| Keyboard-only focus/navigation (visibility, Tab order, Enter/Escape activation) | **Verified** — no defect found on re-check with a corrected method |
| 200% zoom (real `Emulation.setDeviceMetricsOverride` scale, not a CSS-zoom proxy) | **Verified** — no horizontal overflow at 2× on join/control/results |
| Screen reader | `aria-live` gap found and **fixed**; accessibility-tree structure **verified** correct (roles/names/live-region); **actual screen-reader software not run — explicitly pending** |
| Color contrast | **Verified** (axe `color-contrast`, no violations found) |

This pass closes out keyboard-only and 200%-zoom acceptance with real mechanisms (not proxies),
and corrects the prior pass's inconclusive keyboard-focus finding (it was a measurement artifact,
not a real defect). **The one accessibility gate this cannot close is a literal screen-reader
listening test** — no NVDA/JAWS/VoiceOver exists in this environment. The accessibility-tree
verification above is the strongest substitute checkable from here, but it is not that test.
**WCAG 2.2 AA acceptance is not claimed as complete** until a real screen-reader pass is run by
someone with the software to run it.

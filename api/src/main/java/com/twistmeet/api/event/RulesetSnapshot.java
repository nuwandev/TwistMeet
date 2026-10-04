package com.twistmeet.api.event;

import com.twistmeet.api.scoring.RulesetVersion;
import java.time.Instant;

/**
 * The immutable snapshot taken when registration opens (00 §4). Serialized to JSON and stored on
 * {@link Event#getRulesetSnapshot()}; never mutated afterward — a later rule change would need a
 * new ruleset version, not an edit to an event's existing snapshot (00 §4: "Editing a rule after
 * opening requires close registration, show affected entrants a notice and record the change" —
 * that re-snapshot flow is not built this milestone, see DECISIONS.md).
 */
public record RulesetSnapshot(
    RulesetVersion rulesetVersion,
    String puzzleType,
    TimerMode timerMode,
    ScramblePolicy scramblePolicy,
    Instant snapshotAt) {}

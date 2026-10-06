package com.twistmeet.api.competition;

/**
 * 09 "Advancement". Stored as the round's configured rule; the actual preview/commit operation is
 * out of scope for this milestone (see DECISIONS.md) — only the configuration and the pure
 * calculation ({@code com.twistmeet.api.scoring.AdvancementCalculator}) exist so far.
 */
public enum AdvancementRule {
  EVERYONE,
  TOP_N,
  TOP_PERCENT
}

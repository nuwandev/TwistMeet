package com.twistmeet.api.competition;

/**
 * 00 §4 default tie-break is average then best single, shared rank if still tied — that part is
 * always applied by the scoring engine regardless of this setting. This field only configures what
 * happens at the advancement boundary: either accept the default shared-rank outcome, or (per 00
 * §4) have the organizer choose a tie-break attempt before registration opens. Only {@code
 * SHARED_RANK} is actually actionable this milestone, since the tie-break-attempt mechanism belongs
 * to the advancement feature deferred to a later milestone (see DECISIONS.md); {@code
 * TIE_BREAK_ATTEMPT} can be configured but has no runtime effect yet.
 */
public enum TiePolicy {
  SHARED_RANK,
  TIE_BREAK_ATTEMPT
}

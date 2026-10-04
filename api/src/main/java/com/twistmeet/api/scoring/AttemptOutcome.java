package com.twistmeet.api.scoring;

/**
 * The scoring-relevant outcome of a single resolved attempt. This is deliberately a smaller,
 * engine-local vocabulary than the persistence {@code ResultStatus} (which also has {@code
 * PENDING}/{@code VOID}) — 09 requires the scoring module to be "independent of UI and
 * persistence," so it only ever sees attempts that have already been resolved to one of these three
 * outcomes; the caller filters out pending/voided attempts before invoking the engine.
 */
public enum AttemptOutcome {
  OK,
  DNF,
  DNS
}

package com.twistmeet.api.competition;

/**
 * 09 "An attempt is PENDING until an authorized status is entered; blank is never silently
 * converted to DNF/DNS before round close." {@code VOID} marks an attempt excluded from scoring
 * entirely (an accepted correction that voided the original result) — distinct from DNF (a
 * judged/ruled-invalid attempt that still counts as a bad result) and DNS (never started).
 */
public enum ResultStatus {
  PENDING,
  OK,
  DNF,
  DNS,
  VOID
}

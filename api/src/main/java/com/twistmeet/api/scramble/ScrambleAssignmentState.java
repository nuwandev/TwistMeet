package com.twistmeet.api.scramble;

/** 00 §7 / 07 S07 lifecycle: assigned, then revealed to an official, then applied, then checked. */
public enum ScrambleAssignmentState {
  ASSIGNED,
  REVEALED,
  APPLIED,
  CHECKED,
  VOIDED
}

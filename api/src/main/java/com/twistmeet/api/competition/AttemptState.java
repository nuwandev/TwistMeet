package com.twistmeet.api.competition;

/**
 * Workflow state of one attempt. Judge mode never exposes a timer to the app (00 §1: "not a
 * physical timer instrument in judge-controlled mode"), so a judge-recorded attempt moves directly
 * {@code PENDING -> ACCEPTED} via {@code PUT /judge-result} rather than passing through {@code
 * RUNNING}/{@code STOPPED}/{@code SUBMITTED}, which exist only for the phone/self-timed path.
 * {@code VOIDED} is a terminal alternate path from any state, used by an accepted correction (02:
 * "mark it void").
 */
public enum AttemptState {
  PENDING,
  RUNNING,
  STOPPED,
  SUBMITTED,
  ACCEPTED,
  VOIDED
}

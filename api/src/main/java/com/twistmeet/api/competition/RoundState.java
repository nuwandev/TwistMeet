package com.twistmeet.api.competition;

/** 00 §6: {@code DRAFT -> PREPARING -> READY -> LIVE -> REVIEW -> CLOSED}. */
public enum RoundState {
  DRAFT,
  PREPARING,
  READY,
  LIVE,
  REVIEW,
  CLOSED
}

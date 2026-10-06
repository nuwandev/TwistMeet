package com.twistmeet.api.competition;

/** 08 {@code POST /corrections/{id}/decision} payload action values. */
public enum CorrectionDecision {
  ACCEPT_NO_RETRY,
  ACCEPT_RETRY,
  REJECT,
  NEED_INFO
}

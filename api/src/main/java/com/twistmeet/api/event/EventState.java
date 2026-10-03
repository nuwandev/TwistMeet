package com.twistmeet.api.event;

/**
 * Full event lifecycle from 00 §6. M1 only drives transitions through {@code REGISTRATION_LOCKED};
 * {@code READY}/{@code LIVE}/{@code COMPLETED} depend on rounds and attempts (M2+) and are not
 * reachable yet, but the enum carries every state up front so later milestones do not need a
 * migration to add them.
 */
public enum EventState {
  DRAFT,
  REGISTRATION_OPEN,
  REGISTRATION_LOCKED,
  READY,
  LIVE,
  COMPLETED,
  ARCHIVED
}

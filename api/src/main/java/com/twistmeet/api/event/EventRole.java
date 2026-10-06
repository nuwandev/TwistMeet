package com.twistmeet.api.event;

/**
 * Event-scoped staff role (00 §5), distinct from the organization-wide {@code OrgRole}. A judge
 * need not be a member of the organization that owns the event at all — see {@link
 * EventStaffAssignment}.
 */
public enum EventRole {
  JUDGE,
  // 00 §5: "Scrambler: assigned scramble and 3D guide; mark physical preparation checked; no
  // results or roster access unless also assigned another role." Added at M4 — nothing before
  // this milestone needed a distinct scrambler role.
  SCRAMBLER
}

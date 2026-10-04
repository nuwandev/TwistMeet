package com.twistmeet.api.event;

/**
 * Event-scoped staff role (00 §5), distinct from the organization-wide {@code OrgRole}. A judge
 * need not be a member of the organization that owns the event at all — see {@link
 * EventStaffAssignment}.
 */
public enum EventRole {
  JUDGE
}

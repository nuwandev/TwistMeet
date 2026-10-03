package com.twistmeet.api.org;

/**
 * Organization-level staff roles (00 §5). Event-scoped roles (Judge, Scrambler) are assigned per
 * event via {@code EventStaffAssignment} and belong to M2/M3 once rounds and stations exist; this
 * enum covers only the two organization-wide roles M1 needs.
 */
public enum OrgRole {
  OWNER,
  ORGANIZER
}

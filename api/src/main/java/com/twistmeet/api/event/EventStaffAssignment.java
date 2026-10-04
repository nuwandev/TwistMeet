package com.twistmeet.api.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Grants one user a narrower, event-scoped role (currently only {@link EventRole#JUDGE}) for one
 * event, independent of whether they hold any organization-wide membership (00 §5: "event staff
 * assignment can narrow access further" — M1's organization roles are only Owner/Organizer, both
 * full-access, so a genuinely narrower judge role has to live here rather than as a third
 * organization role). This is a minimal addition beyond {@code 08}'s enumerated endpoint list,
 * documented in DECISIONS.md the same way {@code RosterController}'s roster-list GET was for M1.
 */
@Entity
@Table(name = "event_staff_assignments")
public class EventStaffAssignment {

  @Id @GeneratedValue private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EventRole role;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt = Instant.now();

  protected EventStaffAssignment() {}

  public EventStaffAssignment(UUID eventId, UUID userId, EventRole role) {
    this.eventId = eventId;
    this.userId = userId;
    this.role = role;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getUserId() {
    return userId;
  }

  public EventRole getRole() {
    return role;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }
}

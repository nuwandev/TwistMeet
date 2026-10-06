package com.twistmeet.api.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Event-scoped guest identity (00 §2.3/§2.4, 04 "no global identity by default"). M1 only needs
 * display name, status, and join time — check-in/group/station fields belong to M2/M3 once rounds
 * and stations exist.
 */
@Entity
@Table(name = "event_entrants")
public class EventEntrant {

  @Id @GeneratedValue private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EntrantStatus status = EntrantStatus.ACTIVE;

  @Enumerated(EnumType.STRING)
  @Column(name = "check_in_state", nullable = false)
  private CheckInState checkInState = CheckInState.NOT_CHECKED_IN;

  @Column(name = "joined_at", nullable = false)
  private Instant joinedAt = Instant.now();

  @Version private long version;

  protected EventEntrant() {}

  public EventEntrant(UUID eventId, String displayName) {
    this.eventId = eventId;
    this.displayName = displayName;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public EntrantStatus getStatus() {
    return status;
  }

  public CheckInState getCheckInState() {
    return checkInState;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public long getVersion() {
    return version;
  }

  public void rename(String displayName) {
    this.displayName = displayName;
  }

  public void checkIn() {
    this.checkInState = CheckInState.CHECKED_IN;
  }

  public void withdraw() {
    this.status = EntrantStatus.WITHDRAWN;
  }
}

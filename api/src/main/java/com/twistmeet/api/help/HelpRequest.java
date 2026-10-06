package com.twistmeet.api.help;

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
 * 07 P05: "Request judge/help" — a general, always-available escalation during an attempt, before a
 * result exists, distinct from a {@code Correction} (which contests an already-recorded result and
 * carries a category/decision workflow). 00 §5: "Judge: assigned event attempt entry/ status and
 * help escalation." No category or decision is modeled here deliberately — this is "a judge is
 * wanted at this station," not a dispute.
 */
@Entity
@Table(name = "help_requests")
public class HelpRequest {

  @Id @GeneratedValue private UUID id;

  @Column(name = "attempt_id", nullable = false)
  private UUID attemptId;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "entrant_id", nullable = false)
  private UUID entrantId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private HelpRequestState state = HelpRequestState.PENDING;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "resolved_by")
  private UUID resolvedBy;

  protected HelpRequest() {}

  public HelpRequest(UUID attemptId, UUID eventId, UUID entrantId) {
    this.attemptId = attemptId;
    this.eventId = eventId;
    this.entrantId = entrantId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAttemptId() {
    return attemptId;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getEntrantId() {
    return entrantId;
  }

  public HelpRequestState getState() {
    return state;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public UUID getResolvedBy() {
    return resolvedBy;
  }

  public void resolve(UUID staffUserId) {
    if (state == HelpRequestState.PENDING) {
      state = HelpRequestState.RESOLVED;
      resolvedAt = Instant.now();
      resolvedBy = staffUserId;
    }
  }
}

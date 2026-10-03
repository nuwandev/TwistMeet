package com.twistmeet.api.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail (00 §4/§10: "Audit privileged changes"). Rows are never updated or
 * deleted by application code; see migration V1 for the database-level append-only comment.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id")
  private UUID organizationId;

  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "actor_type", nullable = false)
  private String actorType;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(nullable = false)
  private String action;

  @Column(name = "target_type")
  private String targetType;

  @Column(name = "target_id")
  private String targetId;

  private String reason;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt = Instant.now();

  protected AuditEvent() {}

  public AuditEvent(
      UUID organizationId,
      UUID eventId,
      String actorType,
      UUID actorId,
      String action,
      String targetType,
      String targetId,
      String reason) {
    this.organizationId = organizationId;
    this.eventId = eventId;
    this.actorType = actorType;
    this.actorId = actorId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.reason = reason;
  }

  public UUID getId() {
    return id;
  }
}

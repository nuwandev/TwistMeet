package com.twistmeet.api.competition;

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

/** 02 "Corrections and extra attempts"; 08 {@code Correction} resource. */
@Entity
@Table(name = "corrections")
public class Correction {

  @Id @GeneratedValue private UUID id;

  @Column(name = "attempt_id", nullable = false)
  private UUID attemptId;

  @Column(name = "requested_by", nullable = false)
  private UUID requestedBy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CorrectionCategory category;

  private String note;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CorrectionState state = CorrectionState.PENDING;

  @Enumerated(EnumType.STRING)
  private CorrectionDecision decision;

  @Column(name = "decided_by")
  private UUID decidedBy;

  @Column(name = "decision_reason")
  private String decisionReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Version private long version;

  protected Correction() {}

  public Correction(UUID attemptId, UUID requestedBy, CorrectionCategory category, String note) {
    this.attemptId = attemptId;
    this.requestedBy = requestedBy;
    this.category = category;
    this.note = note;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAttemptId() {
    return attemptId;
  }

  public UUID getRequestedBy() {
    return requestedBy;
  }

  public CorrectionCategory getCategory() {
    return category;
  }

  public String getNote() {
    return note;
  }

  public CorrectionState getState() {
    return state;
  }

  public CorrectionDecision getDecision() {
    return decision;
  }

  public UUID getDecidedBy() {
    return decidedBy;
  }

  public String getDecisionReason() {
    return decisionReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public long getVersion() {
    return version;
  }

  public void decide(CorrectionDecision decision, UUID decidedBy, String reason) {
    this.decision = decision;
    this.decidedBy = decidedBy;
    this.decisionReason = reason;
    this.state = CorrectionState.DECIDED;
    this.decidedAt = Instant.now();
  }
}

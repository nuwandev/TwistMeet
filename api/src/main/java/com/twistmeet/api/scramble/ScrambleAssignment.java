package com.twistmeet.api.scramble;

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
 * Links one attempt to one {@link ScrambleSecret}. A spoiled/voided assignment is never reused or
 * mutated back to live — {@code ScrambleService} always creates a fresh row with a fresh secret, so
 * the full history for an attempt is reconstructible (R40: "no hard-delete after attempt
 * assignment," applied the same way to scramble assignments).
 */
@Entity
@Table(name = "scramble_assignments")
public class ScrambleAssignment {

  @Id @GeneratedValue private UUID id;

  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  @Column(name = "secret_id", nullable = false)
  private UUID secretId;

  @Column(name = "round_id", nullable = false)
  private UUID roundId;

  @Column(name = "attempt_id", nullable = false)
  private UUID attemptId;

  @Column(name = "entrant_id", nullable = false)
  private UUID entrantId;

  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ScrambleAssignmentState state = ScrambleAssignmentState.ASSIGNED;

  @Column(name = "revealed_at")
  private Instant revealedAt;

  @Column(name = "revealed_by")
  private UUID revealedBy;

  @Column(name = "applied_at")
  private Instant appliedAt;

  @Column(name = "applied_by")
  private UUID appliedBy;

  @Column(name = "checked_at")
  private Instant checkedAt;

  @Column(name = "checked_by")
  private UUID checkedBy;

  @Column(name = "second_checked_at")
  private Instant secondCheckedAt;

  @Column(name = "second_checked_by")
  private UUID secondCheckedBy;

  @Column(name = "voided_at")
  private Instant voidedAt;

  @Column(name = "voided_by")
  private UUID voidedBy;

  @Column(name = "void_reason")
  private String voidReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Version private long version;

  protected ScrambleAssignment() {}

  public ScrambleAssignment(
      UUID batchId,
      UUID secretId,
      UUID roundId,
      UUID attemptId,
      UUID entrantId,
      int attemptNumber) {
    this.batchId = batchId;
    this.secretId = secretId;
    this.roundId = roundId;
    this.attemptId = attemptId;
    this.entrantId = entrantId;
    this.attemptNumber = attemptNumber;
  }

  public UUID getId() {
    return id;
  }

  public UUID getBatchId() {
    return batchId;
  }

  public UUID getSecretId() {
    return secretId;
  }

  public UUID getRoundId() {
    return roundId;
  }

  public UUID getAttemptId() {
    return attemptId;
  }

  public UUID getEntrantId() {
    return entrantId;
  }

  public int getAttemptNumber() {
    return attemptNumber;
  }

  public ScrambleAssignmentState getState() {
    return state;
  }

  public Instant getRevealedAt() {
    return revealedAt;
  }

  public UUID getRevealedBy() {
    return revealedBy;
  }

  public Instant getAppliedAt() {
    return appliedAt;
  }

  public UUID getAppliedBy() {
    return appliedBy;
  }

  public Instant getCheckedAt() {
    return checkedAt;
  }

  public UUID getCheckedBy() {
    return checkedBy;
  }

  public Instant getSecondCheckedAt() {
    return secondCheckedAt;
  }

  public UUID getSecondCheckedBy() {
    return secondCheckedBy;
  }

  public Instant getVoidedAt() {
    return voidedAt;
  }

  public UUID getVoidedBy() {
    return voidedBy;
  }

  public String getVoidReason() {
    return voidReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getVersion() {
    return version;
  }

  /** Idempotent: calling this again after reveal is a no-op (same timestamp/actor kept). */
  public void reveal(UUID actorUserId) {
    if (revealedAt == null) {
      revealedAt = Instant.now();
      revealedBy = actorUserId;
      state = ScrambleAssignmentState.REVEALED;
    }
  }

  public boolean isRevealed() {
    return revealedAt != null;
  }

  /** Idempotent: a second call from any actor is a no-op. */
  public void markApplied(UUID actorUserId) {
    if (appliedAt == null) {
      appliedAt = Instant.now();
      appliedBy = actorUserId;
      state = ScrambleAssignmentState.APPLIED;
    }
  }

  /**
   * First call records the primary check; a second call from a *different* actor with {@code
   * independent=true} records an independent second check (07 S07 "a second checker toggle supports
   * independent staff acknowledgment"). Any other repeat call is a no-op.
   */
  public void markChecked(UUID actorUserId, boolean independent) {
    if (checkedAt == null) {
      checkedAt = Instant.now();
      checkedBy = actorUserId;
      state = ScrambleAssignmentState.CHECKED;
    } else if (independent && secondCheckedAt == null && !actorUserId.equals(checkedBy)) {
      secondCheckedAt = Instant.now();
      secondCheckedBy = actorUserId;
    }
  }

  public void voidAssignment(UUID actorUserId, String reason) {
    voidedAt = Instant.now();
    voidedBy = actorUserId;
    voidReason = reason;
    state = ScrambleAssignmentState.VOIDED;
  }
}

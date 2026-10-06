package com.twistmeet.api.competition;

import com.twistmeet.api.scoring.Penalty;
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
 * Append-only history of every change to an attempt's recorded result (00 §9: "immutable revision
 * history"; 08: "records a revision" on every {@code judge-result} call, including the first). Rows
 * are never updated or deleted by application code, the same convention as {@code
 * common.AuditEvent} — see that class's comment for the same known application-layer-only gap.
 */
@Entity
@Table(name = "result_revisions")
public class ResultRevision {

  @Id @GeneratedValue private UUID id;

  @Column(name = "attempt_id", nullable = false)
  private UUID attemptId;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "previous_raw_time_ms")
  private Long previousRawTimeMs;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_penalty")
  private Penalty previousPenalty;

  @Enumerated(EnumType.STRING)
  @Column(name = "previous_result_status")
  private ResultStatus previousResultStatus;

  @Column(name = "new_raw_time_ms")
  private Long newRawTimeMs;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_penalty", nullable = false)
  private Penalty newPenalty;

  @Enumerated(EnumType.STRING)
  @Column(name = "new_result_status", nullable = false)
  private ResultStatus newResultStatus;

  private String note;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected ResultRevision() {}

  public ResultRevision(
      UUID attemptId,
      UUID actorUserId,
      Long previousRawTimeMs,
      Penalty previousPenalty,
      ResultStatus previousResultStatus,
      Long newRawTimeMs,
      Penalty newPenalty,
      ResultStatus newResultStatus,
      String note) {
    this.attemptId = attemptId;
    this.actorUserId = actorUserId;
    this.previousRawTimeMs = previousRawTimeMs;
    this.previousPenalty = previousPenalty;
    this.previousResultStatus = previousResultStatus;
    this.newRawTimeMs = newRawTimeMs;
    this.newPenalty = newPenalty;
    this.newResultStatus = newResultStatus;
    this.note = note;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAttemptId() {
    return attemptId;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public Long getPreviousRawTimeMs() {
    return previousRawTimeMs;
  }

  public Penalty getPreviousPenalty() {
    return previousPenalty;
  }

  public ResultStatus getPreviousResultStatus() {
    return previousResultStatus;
  }

  public Long getNewRawTimeMs() {
    return newRawTimeMs;
  }

  public Penalty getNewPenalty() {
    return newPenalty;
  }

  public ResultStatus getNewResultStatus() {
    return newResultStatus;
  }

  public String getNote() {
    return note;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

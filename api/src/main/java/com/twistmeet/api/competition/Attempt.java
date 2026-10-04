package com.twistmeet.api.competition;

import com.twistmeet.api.scoring.Penalty;
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
 * One attempt slot (00 §6 attempt lifecycle). {@code scrambleAssignmentId} is set by {@code
 * ScrambleService} once a scramble batch has been generated for the round (M4); it stays null for
 * rounds/events created before a batch exists, or if scrambles are never generated for that round —
 * nothing in this class depends on it being set.
 */
@Entity
@Table(name = "attempts")
public class Attempt {

  @Id @GeneratedValue private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "round_id", nullable = false)
  private UUID roundId;

  @Column(name = "entrant_id", nullable = false)
  private UUID entrantId;

  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber;

  @Column(name = "scramble_assignment_id")
  private UUID scrambleAssignmentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "result_source", nullable = false)
  private ResultSource resultSource;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AttemptState state = AttemptState.PENDING;

  @Column(name = "raw_time_ms")
  private Long rawTimeMs;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Penalty penalty = Penalty.NONE;

  @Column(name = "adjusted_time_ms")
  private Long adjustedTimeMs;

  @Enumerated(EnumType.STRING)
  @Column(name = "result_status", nullable = false)
  private ResultStatus resultStatus = ResultStatus.PENDING;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "stopped_at")
  private Instant stoppedAt;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "client_build")
  private String clientBuild;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Version private long version;

  protected Attempt() {}

  public Attempt(
      UUID eventId, UUID roundId, UUID entrantId, int attemptNumber, ResultSource resultSource) {
    this.eventId = eventId;
    this.roundId = roundId;
    this.entrantId = entrantId;
    this.attemptNumber = attemptNumber;
    this.resultSource = resultSource;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getRoundId() {
    return roundId;
  }

  public UUID getEntrantId() {
    return entrantId;
  }

  public int getAttemptNumber() {
    return attemptNumber;
  }

  public UUID getScrambleAssignmentId() {
    return scrambleAssignmentId;
  }

  public void setScrambleAssignmentId(UUID scrambleAssignmentId) {
    this.scrambleAssignmentId = scrambleAssignmentId;
  }

  public ResultSource getResultSource() {
    return resultSource;
  }

  public AttemptState getState() {
    return state;
  }

  public Long getRawTimeMs() {
    return rawTimeMs;
  }

  public Penalty getPenalty() {
    return penalty;
  }

  public Long getAdjustedTimeMs() {
    return adjustedTimeMs;
  }

  public ResultStatus getResultStatus() {
    return resultStatus;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getStoppedAt() {
    return stoppedAt;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public String getClientBuild() {
    return clientBuild;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getVersion() {
    return version;
  }

  public void start() {
    this.state = AttemptState.RUNNING;
    this.startedAt = Instant.now();
  }

  public void stop() {
    this.state = AttemptState.STOPPED;
    this.stoppedAt = Instant.now();
  }

  /** Self-timed submit: records the result and immediately accepts it (see DECISIONS.md). */
  public void submitSelfTimed(
      Long rawTimeMs, Penalty penalty, ResultStatus resultStatus, String clientBuild) {
    applyResult(rawTimeMs, penalty, resultStatus);
    this.clientBuild = clientBuild;
    this.submittedAt = Instant.now();
    this.state = AttemptState.ACCEPTED;
  }

  /** Judge entry: records (or re-records, creating a revision at the service layer) the result. */
  public void recordJudgeResult(Long rawTimeMs, Penalty penalty, ResultStatus resultStatus) {
    applyResult(rawTimeMs, penalty, resultStatus);
    this.state = AttemptState.ACCEPTED;
  }

  private void applyResult(Long rawTimeMs, Penalty penalty, ResultStatus resultStatus) {
    this.rawTimeMs = rawTimeMs;
    this.penalty = penalty;
    this.resultStatus = resultStatus;
    this.adjustedTimeMs =
        (resultStatus == ResultStatus.OK && rawTimeMs != null) ? penalty.apply(rawTimeMs) : null;
  }

  /** Accepted correction with no replacement: void, preserving the original values in history. */
  public void voidResult() {
    this.state = AttemptState.VOIDED;
    this.resultStatus = ResultStatus.VOID;
  }
}

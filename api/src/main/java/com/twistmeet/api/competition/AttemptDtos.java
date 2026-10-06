package com.twistmeet.api.competition;

import com.twistmeet.api.scoring.Penalty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AttemptDtos {

  private AttemptDtos() {}

  /** 08: "DNF/DNS rules validated" — penalty carries DNF/DNS as pseudo-penalty-free statuses. */
  public enum SubmittedStatus {
    OK,
    DNF,
    DNS
  }

  public record SelfTimedSubmitRequest(
      @NotNull SubmittedStatus status,
      Long rawTimeMs,
      Penalty penalty,
      Instant clientStartedAt,
      Instant clientStoppedAt,
      @Size(max = 200) String clientBuild) {}

  public record JudgeResultRequest(
      @NotNull SubmittedStatus status,
      Long rawTimeMs,
      Penalty penalty,
      @Size(max = 500) String note,
      @NotNull long expectedVersion) {}

  public record AttemptView(
      UUID id,
      UUID eventId,
      UUID roundId,
      UUID entrantId,
      int attemptNumber,
      // 08: "Attempt DTO includes scrambleAssignmentId?" — an opaque pointer only, never the
      // notation itself. Safe to expose to the owning competitor too: the current-scramble
      // endpoint (M4) independently re-checks ownership and "unlocked" status before ever
      // returning notation, so this ID alone grants no access.
      UUID scrambleAssignmentId,
      ResultSource resultSource,
      AttemptState state,
      Long rawTimeMs,
      Penalty penalty,
      Long adjustedTimeMs,
      ResultStatus resultStatus,
      Instant startedAt,
      Instant stoppedAt,
      Instant submittedAt,
      long version) {
    public static AttemptView of(Attempt attempt) {
      return new AttemptView(
          attempt.getId(),
          attempt.getEventId(),
          attempt.getRoundId(),
          attempt.getEntrantId(),
          attempt.getAttemptNumber(),
          attempt.getScrambleAssignmentId(),
          attempt.getResultSource(),
          attempt.getState(),
          attempt.getRawTimeMs(),
          attempt.getPenalty(),
          attempt.getAdjustedTimeMs(),
          attempt.getResultStatus(),
          attempt.getStartedAt(),
          attempt.getStoppedAt(),
          attempt.getSubmittedAt(),
          attempt.getVersion());
    }
  }

  /**
   * 07 S12 "Revisions list actor, time, reason and before/after values." {@code actorUserId}/
   * {@code actorDisplayName} and the attempt/entrant identification were added for S12 (the entity
   * always had {@code actorUserId}; the view never exposed it before this).
   */
  public record ResultRevisionView(
      UUID id,
      UUID attemptId,
      int attemptNumber,
      UUID entrantId,
      String entrantDisplayName,
      UUID actorUserId,
      String actorDisplayName,
      Long previousRawTimeMs,
      Penalty previousPenalty,
      ResultStatus previousResultStatus,
      Long newRawTimeMs,
      Penalty newPenalty,
      ResultStatus newResultStatus,
      String note,
      Instant createdAt) {
    public static ResultRevisionView of(
        ResultRevision revision,
        Attempt attempt,
        String entrantDisplayName,
        String actorDisplayName) {
      return new ResultRevisionView(
          revision.getId(),
          attempt.getId(),
          attempt.getAttemptNumber(),
          attempt.getEntrantId(),
          entrantDisplayName,
          revision.getActorUserId(),
          actorDisplayName,
          revision.getPreviousRawTimeMs(),
          revision.getPreviousPenalty(),
          revision.getPreviousResultStatus(),
          revision.getNewRawTimeMs(),
          revision.getNewPenalty(),
          revision.getNewResultStatus(),
          revision.getNote(),
          revision.getCreatedAt());
    }
  }
}

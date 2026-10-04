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

  public record ResultRevisionView(
      UUID id,
      Long previousRawTimeMs,
      Penalty previousPenalty,
      ResultStatus previousResultStatus,
      Long newRawTimeMs,
      Penalty newPenalty,
      ResultStatus newResultStatus,
      String note,
      Instant createdAt) {
    public static ResultRevisionView of(ResultRevision revision) {
      return new ResultRevisionView(
          revision.getId(),
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

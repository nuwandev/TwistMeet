package com.twistmeet.api.scramble;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ScrambleDtos {

  private ScrambleDtos() {}

  /** 08: batch-generation response is "counts/IDs only" — never notation. */
  public record BatchView(
      UUID id,
      UUID roundId,
      String puzzleType,
      String generatorName,
      String generatorVersion,
      String rulesetVersion,
      int attemptCount,
      int extraCount,
      Instant createdAt) {
    static BatchView of(ScrambleBatch batch, int attemptCount) {
      return new BatchView(
          batch.getId(),
          batch.getRoundId(),
          batch.getPuzzleType(),
          batch.getGeneratorName(),
          batch.getGeneratorVersion(),
          batch.getRulesetVersion(),
          attemptCount,
          batch.getExtraCount(),
          batch.getCreatedAt());
    }
  }

  /** Metadata-only assignment view — never includes notation. Used for list/status screens. */
  public record AssignmentView(
      UUID id,
      UUID roundId,
      UUID attemptId,
      int attemptNumber,
      ScrambleAssignmentState state,
      Instant revealedAt,
      Instant appliedAt,
      UUID appliedBy,
      Instant checkedAt,
      UUID checkedBy,
      Instant secondCheckedAt,
      UUID secondCheckedBy,
      long version) {
    public static AssignmentView of(ScrambleAssignment a) {
      return new AssignmentView(
          a.getId(),
          a.getRoundId(),
          a.getAttemptId(),
          a.getAttemptNumber(),
          a.getState(),
          a.getRevealedAt(),
          a.getAppliedAt(),
          a.getAppliedBy(),
          a.getCheckedAt(),
          a.getCheckedBy(),
          a.getSecondCheckedAt(),
          a.getSecondCheckedBy(),
          a.getVersion());
    }
  }

  /**
   * The one DTO anywhere in this codebase that carries plaintext notation. Returned only by
   * reveal/official-view/current-scramble/print, each gated by its own server-side authorization
   * check — never by a list endpoint, never embedded in Attempt/Round/Event views, and never passed
   * to AuditService or a logger.
   */
  public record RevealView(
      UUID assignmentId,
      String notation,
      String puzzleType,
      UUID roundId,
      int attemptNumber,
      Instant revealedAt) {}

  public record SpoilRequest(@NotBlank @Size(max = 500) String reason) {}

  public record PrintEntry(int attemptNumber, String notation) {}
}

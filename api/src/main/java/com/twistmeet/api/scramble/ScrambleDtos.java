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
   *
   * <p>{@code toString()} is overridden to redact {@code notation}: Spring MVC's DEBUG-level
   * request logging (e.g. {@code AbstractMessageConverterMethodProcessor} "Writing [...]") calls a
   * response body's {@code toString()} verbatim, so the default record-generated {@code toString()}
   * would otherwise put plaintext notation into logs the moment anyone ever raises that log level —
   * found by {@code ScrambleSecurityGapTest}, fixed here rather than relying on every environment
   * to keep DEBUG logging off forever.
   */
  public record RevealView(
      UUID assignmentId,
      String notation,
      String puzzleType,
      UUID roundId,
      int attemptNumber,
      Instant revealedAt) {
    @Override
    public String toString() {
      return "RevealView[assignmentId="
          + assignmentId
          + ", notation=<redacted>, puzzleType="
          + puzzleType
          + ", roundId="
          + roundId
          + ", attemptNumber="
          + attemptNumber
          + ", revealedAt="
          + revealedAt
          + "]";
    }
  }

  public record SpoilRequest(@NotBlank @Size(max = 500) String reason) {}

  /** See {@link RevealView}'s javadoc on the same {@code toString()} redaction rationale. */
  public record PrintEntry(int attemptNumber, String notation) {
    @Override
    public String toString() {
      return "PrintEntry[attemptNumber=" + attemptNumber + ", notation=<redacted>]";
    }
  }
}

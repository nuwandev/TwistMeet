package com.twistmeet.api.competition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class CorrectionDtos {

  private CorrectionDtos() {}

  public record CreateCorrectionRequest(
      @NotNull CorrectionCategory category, @Size(max = 500) String note) {}

  /** 07 S10 "Require decision reason" — enforced server-side, not only by hiding the UI control. */
  public record DecisionRequest(
      @NotNull CorrectionDecision action,
      @NotBlank @Size(max = 500) String reason,
      @NotNull long expectedVersion) {}

  public record CorrectionView(
      UUID id,
      UUID attemptId,
      UUID requestedBy,
      CorrectionCategory category,
      String note,
      CorrectionState state,
      CorrectionDecision decision,
      UUID decidedBy,
      String decisionReason,
      Instant createdAt,
      Instant decidedAt,
      long version) {
    public static CorrectionView of(Correction correction) {
      return new CorrectionView(
          correction.getId(),
          correction.getAttemptId(),
          correction.getRequestedBy(),
          correction.getCategory(),
          correction.getNote(),
          correction.getState(),
          correction.getDecision(),
          correction.getDecidedBy(),
          correction.getDecisionReason(),
          correction.getCreatedAt(),
          correction.getDecidedAt(),
          correction.getVersion());
    }
  }
}

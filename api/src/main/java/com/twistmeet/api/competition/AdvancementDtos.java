package com.twistmeet.api.competition;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public final class AdvancementDtos {

  private AdvancementDtos() {}

  public record AdvancedEntrant(UUID entrantId, String displayName, int rank) {}

  /**
   * Non-mutating; safe to call repeatedly while a round is in REVIEW/CLOSED. {@code
   * alreadyCommitted} tells the UI whether "Commit advancement" would be a no-op replay.
   */
  public record AdvancementPreviewView(
      UUID roundId,
      UUID nextRoundId,
      String advancementRule,
      Integer advancementValue,
      int eligibleCount,
      int targetCount,
      List<AdvancedEntrant> advancing,
      String tieNote,
      boolean alreadyCommitted) {}

  public record CommitRequest(@NotNull Long expectedVersion) {}

  public record AdvancementCommitView(
      UUID roundId,
      UUID nextRoundId,
      int eligibleCount,
      int advancedCount,
      List<AdvancedEntrant> advancing,
      String tieNote,
      boolean idempotentReplay) {
    public static AdvancementCommitView of(
        AdvancementPreviewView preview, boolean idempotentReplay) {
      return new AdvancementCommitView(
          preview.roundId(),
          preview.nextRoundId(),
          preview.eligibleCount(),
          preview.advancing().size(),
          preview.advancing(),
          preview.tieNote(),
          idempotentReplay);
    }
  }
}

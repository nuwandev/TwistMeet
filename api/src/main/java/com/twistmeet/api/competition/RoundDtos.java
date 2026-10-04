package com.twistmeet.api.competition;

import com.twistmeet.api.scoring.RoundFormat;
import com.twistmeet.api.scoring.RulesetVersion;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class RoundDtos {

  private RoundDtos() {}

  public record CreateRoundRequest(
      @Min(1) int order,
      @NotBlank @Size(min = 1, max = 80) String name,
      @NotNull RoundFormat format,
      AdvancementRule advancementRule,
      Integer advancementValue,
      TiePolicy tiePolicy) {}

  public record UpdateRoundRequest(
      @NotBlank @Size(min = 1, max = 80) String name,
      @NotNull RoundFormat format,
      AdvancementRule advancementRule,
      Integer advancementValue,
      TiePolicy tiePolicy) {}

  public record RoundView(
      UUID id,
      UUID eventId,
      int order,
      String name,
      RoundFormat format,
      int attemptCount,
      AdvancementRule advancementRule,
      Integer advancementValue,
      TiePolicy tiePolicy,
      RoundState state,
      boolean paused,
      RulesetVersion rulesetVersion,
      Instant createdAt,
      Instant startedAt,
      long version) {
    public static RoundView of(Round round) {
      return new RoundView(
          round.getId(),
          round.getEventId(),
          round.getOrder(),
          round.getName(),
          round.getFormat(),
          round.getFormat().attemptCount(),
          round.getAdvancementRule(),
          round.getAdvancementValue(),
          round.getTiePolicy(),
          round.getState(),
          round.isPaused(),
          round.getRulesetVersion(),
          round.getCreatedAt(),
          round.getStartedAt(),
          round.getVersion());
    }
  }
}

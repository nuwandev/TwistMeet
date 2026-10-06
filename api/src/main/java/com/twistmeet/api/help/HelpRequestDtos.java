package com.twistmeet.api.help;

import java.time.Instant;
import java.util.UUID;

public final class HelpRequestDtos {

  private HelpRequestDtos() {}

  public record HelpRequestView(
      UUID id,
      UUID attemptId,
      UUID entrantId,
      HelpRequestState state,
      Instant createdAt,
      Instant resolvedAt,
      UUID resolvedBy) {
    public static HelpRequestView of(HelpRequest r) {
      return new HelpRequestView(
          r.getId(),
          r.getAttemptId(),
          r.getEntrantId(),
          r.getState(),
          r.getCreatedAt(),
          r.getResolvedAt(),
          r.getResolvedBy());
    }
  }
}

package com.twistmeet.api.history;

import java.time.Instant;
import java.util.UUID;

public final class HistoryDtos {

  private HistoryDtos() {}

  /** S14 "Completed event cards summarize competitor count, rounds, date." */
  public record EventHistorySummary(
      UUID eventId,
      String name,
      String state,
      Instant startsAt,
      int entrantCount,
      int roundCount) {}
}

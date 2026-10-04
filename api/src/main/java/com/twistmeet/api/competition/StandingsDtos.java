package com.twistmeet.api.competition;

import com.twistmeet.api.scoring.RoundOutcome;
import java.util.List;
import java.util.UUID;

public final class StandingsDtos {

  private StandingsDtos() {}

  public record EntrantStanding(
      UUID entrantId,
      String displayName,
      int rank,
      RoundOutcome outcome,
      Long displayMs,
      Long bestValidSingleMs,
      List<Integer> discardedAttemptNumbers) {}

  public record StandingsView(
      UUID roundId, boolean provisional, String rulesetVersion, List<EntrantStanding> standings) {}
}

package com.twistmeet.api.pub;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public, unauthenticated DTOs (08 {@code GET /public/events/{publicSlug}}, {@code .../standings};
 * 07 S13). Each record is a hand-picked allowlist, not a view over the staff DTOs, so there is no
 * field to accidentally forget to strip: scramble notation, guest credentials/tokens, email, staff
 * actions and private notes/corrections have no field here at all (08 API security test: "public
 * DTO has no email/token/notes/scramble fields").
 */
public final class PublicDtos {

  private PublicDtos() {}

  public record PublicRoundSummary(UUID roundId, int order, String name, String state) {}

  public record PublicEventView(
      UUID eventId,
      String name,
      String venueLabel,
      Instant startsAt,
      String timezone,
      List<PublicRoundSummary> rounds) {}

  public record PublicEntrantStanding(
      String displayName,
      int rank,
      String outcome,
      Long displayMs,
      Long bestValidSingleMs,
      int completedAttempts,
      int totalAttempts) {}

  public record PublicStandingsView(
      UUID roundId, boolean provisional, List<PublicEntrantStanding> standings) {}
}

package com.twistmeet.api.registration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class RegistrationDtos {

  private RegistrationDtos() {}

  public record JoinRequest(@NotBlank @Size(min = 1, max = 32) String displayName) {}

  public record EntrantView(UUID id, String displayName, EntrantStatus status, Instant joinedAt) {
    public static EntrantView of(EventEntrant entrant) {
      return new EntrantView(
          entrant.getId(), entrant.getDisplayName(), entrant.getStatus(), entrant.getJoinedAt());
    }
  }

  /**
   * 08: joining "returns event summary plus guest credential in secure cookie; never returns
   * roster."
   */
  public record JoinResponse(UUID eventId, String eventName, EntrantView entrant) {}
}

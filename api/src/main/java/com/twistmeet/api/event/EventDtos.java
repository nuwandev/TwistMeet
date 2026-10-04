package com.twistmeet.api.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class EventDtos {

  private EventDtos() {}

  public record CreateEventRequest(
      @NotBlank @Size(min = 3, max = 80) String name,
      @Size(max = 2000) String description,
      @NotNull Instant startsAt,
      @NotBlank String timezone,
      @Size(max = 200) String venueLabel,
      EventVisibility visibility,
      TimerMode timerMode,
      ScramblePolicy scramblePolicy) {}

  /** Backs {@code RoleBanner} (07): the caller's resolved staff role for one event. */
  public record MyRoleView(String role) {}

  public record UpdateEventRequest(
      @NotBlank @Size(min = 3, max = 80) String name,
      @Size(max = 2000) String description,
      @Size(max = 200) String venueLabel) {}

  /** Staff-facing view. Guests/public never see {@code joinCode}; see {@code 08} DTO rules. */
  public record EventView(
      UUID id,
      UUID organizationId,
      String name,
      String description,
      Instant startsAt,
      String timezone,
      String venueLabel,
      EventVisibility visibility,
      EventState state,
      String puzzleType,
      TimerMode timerMode,
      ScramblePolicy scramblePolicy,
      String joinCode,
      String rulesetSnapshot,
      Instant createdAt,
      long version) {
    public static EventView of(Event event, boolean includeJoinCode) {
      return new EventView(
          event.getId(),
          event.getOrganizationId(),
          event.getName(),
          event.getDescription(),
          event.getStartsAt(),
          event.getTimezone(),
          event.getVenueLabel(),
          event.getVisibility(),
          event.getState(),
          event.getPuzzleType(),
          event.getTimerMode(),
          event.getScramblePolicy(),
          includeJoinCode ? event.getJoinCode() : null,
          event.getRulesetSnapshot(),
          event.getCreatedAt(),
          event.getVersion());
    }
  }
}

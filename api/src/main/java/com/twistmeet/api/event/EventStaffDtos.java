package com.twistmeet.api.event;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class EventStaffDtos {

  private EventStaffDtos() {}

  public record AssignStaffRequest(@NotNull UUID userId, @NotNull EventRole role) {}

  public record EventStaffAssignmentView(UUID id, UUID userId, EventRole role, Instant assignedAt) {
    public static EventStaffAssignmentView of(EventStaffAssignment assignment) {
      return new EventStaffAssignmentView(
          assignment.getId(),
          assignment.getUserId(),
          assignment.getRole(),
          assignment.getAssignedAt());
    }
  }
}

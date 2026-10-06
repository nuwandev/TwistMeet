package com.twistmeet.api.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventStaffAssignmentRepository extends JpaRepository<EventStaffAssignment, UUID> {
  List<EventStaffAssignment> findByEventId(UUID eventId);

  List<EventStaffAssignment> findByUserId(UUID userId);

  Optional<EventStaffAssignment> findByIdAndEventId(UUID id, UUID eventId);

  boolean existsByEventIdAndUserIdAndRole(UUID eventId, UUID userId, EventRole role);

  boolean existsByEventIdAndUserId(UUID eventId, UUID userId);
}

package com.twistmeet.api.registration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventEntrantRepository extends JpaRepository<EventEntrant, UUID> {
  List<EventEntrant> findByEventId(UUID eventId);

  List<EventEntrant> findByEventIdAndDisplayNameStartingWith(
      UUID eventId, String displayNamePrefix);

  Optional<EventEntrant> findByIdAndEventId(UUID id, UUID eventId);
}

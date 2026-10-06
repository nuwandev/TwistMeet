package com.twistmeet.api.competition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundRepository extends JpaRepository<Round, UUID> {
  List<Round> findByEventIdOrderByOrder(UUID eventId);

  Optional<Round> findByIdAndEventId(UUID id, UUID eventId);

  boolean existsByEventIdAndOrder(UUID eventId, int order);
}

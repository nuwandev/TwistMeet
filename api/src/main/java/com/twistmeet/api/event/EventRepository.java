package com.twistmeet.api.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, UUID> {
  List<Event> findByOrganizationId(UUID organizationId);

  Optional<Event> findByJoinCodeHash(String joinCodeHash);
}

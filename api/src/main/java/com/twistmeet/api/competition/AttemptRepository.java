package com.twistmeet.api.competition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {
  List<Attempt> findByRoundId(UUID roundId);

  List<Attempt> findByEventId(UUID eventId);

  List<Attempt> findByRoundIdAndEntrantId(UUID roundId, UUID entrantId);

  List<Attempt> findByEntrantId(UUID entrantId);

  Optional<Attempt> findByIdAndEventId(UUID id, UUID eventId);

  boolean existsByEntrantId(UUID entrantId);

  int countByRoundIdAndEntrantId(UUID roundId, UUID entrantId);
}

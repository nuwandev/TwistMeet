package com.twistmeet.api.scramble;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrambleBatchRepository extends JpaRepository<ScrambleBatch, UUID> {
  Optional<ScrambleBatch> findByRoundId(UUID roundId);
}

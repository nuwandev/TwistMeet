package com.twistmeet.api.scramble;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrambleSecretRepository extends JpaRepository<ScrambleSecret, UUID> {
  Optional<ScrambleSecret> findFirstByBatchIdAndExtraTrueAndConsumedFalse(UUID batchId);
}

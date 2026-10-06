package com.twistmeet.api.competition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorrectionRepository extends JpaRepository<Correction, UUID> {
  List<Correction> findByAttemptId(UUID attemptId);

  List<Correction> findByAttemptIdIn(List<UUID> attemptIds);

  List<Correction> findByAttemptIdInAndState(List<UUID> attemptIds, CorrectionState state);

  Optional<Correction> findByIdAndAttemptIdIn(UUID id, List<UUID> attemptIds);
}

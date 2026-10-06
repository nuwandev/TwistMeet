package com.twistmeet.api.scramble;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrambleAssignmentRepository extends JpaRepository<ScrambleAssignment, UUID> {
  List<ScrambleAssignment> findByRoundId(UUID roundId);

  Optional<ScrambleAssignment> findByIdAndRoundId(UUID id, UUID roundId);

  /** The one live (non-voided) assignment for an attempt, if any. */
  Optional<ScrambleAssignment> findByAttemptIdAndStateNot(
      UUID attemptId, ScrambleAssignmentState excludedState);
}

package com.twistmeet.api.competition;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultRevisionRepository extends JpaRepository<ResultRevision, UUID> {
  List<ResultRevision> findByAttemptIdOrderByCreatedAtAsc(UUID attemptId);

  List<ResultRevision> findByAttemptIdInOrderByCreatedAtDesc(List<UUID> attemptIds);
}

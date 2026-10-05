package com.twistmeet.api.help;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, UUID> {
  List<HelpRequest> findByEventIdAndState(UUID eventId, HelpRequestState state);

  List<HelpRequest> findByEventId(UUID eventId);

  boolean existsByAttemptIdAndState(UUID attemptId, HelpRequestState state);
}

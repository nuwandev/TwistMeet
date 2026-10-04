package com.twistmeet.api.competition;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoundQualifiedEntrantRepository
    extends JpaRepository<RoundQualifiedEntrant, RoundQualifiedEntrant.Key> {

  boolean existsByIdRoundId(UUID roundId);

  List<RoundQualifiedEntrant> findByIdRoundId(UUID roundId);
}

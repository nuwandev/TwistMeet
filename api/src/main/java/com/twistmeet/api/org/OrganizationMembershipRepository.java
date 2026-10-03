package com.twistmeet.api.org;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMembershipRepository
    extends JpaRepository<OrganizationMembership, UUID> {
  List<OrganizationMembership> findByUserId(UUID userId);

  Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}

package com.twistmeet.api.org;

import com.twistmeet.api.common.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Central tenant-authorization check (00 §5 "Use server-side authorization on every request"; 04
 * "Tenant ID must be enforced on every query and write"). Every org-scoped controller/service must
 * go through here rather than re-implementing the membership check, so there is exactly one place
 * cross-tenant access is decided and exactly one place to test it (08 API security tests:
 * "organization A cannot access organization B").
 */
@Service
public class TenantAccessService {

  private final OrganizationMembershipRepository membershipRepository;

  public TenantAccessService(OrganizationMembershipRepository membershipRepository) {
    this.membershipRepository = membershipRepository;
  }

  /**
   * Returns the caller's role in the organization, or throws 404 (not 403) if they are not a member
   * — per 08: "Return 404 for inaccessible objects to reduce enumeration," a non-member must not be
   * able to distinguish "org exists, you're not in it" from "org does not exist."
   */
  public OrgRole requireMembership(UUID organizationId, UUID userId) {
    return membershipRepository
        .findByOrganizationIdAndUserId(organizationId, userId)
        .map(OrganizationMembership::getRole)
        .orElseThrow(() -> ApiException.notFound("Organization not found"));
  }

  public void requireAnyStaffRole(UUID organizationId, UUID userId) {
    requireMembership(organizationId, userId);
  }
}

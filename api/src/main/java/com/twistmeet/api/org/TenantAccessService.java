package com.twistmeet.api.org;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRole;
import com.twistmeet.api.event.EventStaffAssignmentRepository;
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
  private final EventStaffAssignmentRepository eventStaffAssignmentRepository;

  public TenantAccessService(
      OrganizationMembershipRepository membershipRepository,
      EventStaffAssignmentRepository eventStaffAssignmentRepository) {
    this.membershipRepository = membershipRepository;
    this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
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

  /**
   * Organizer-level authorization for an event-scoped operation (round configuration, roster
   * removal after start, etc. — 00 §5 lists these as Organizer-only, explicitly excluded from
   * Judge). A caller who holds a narrower {@link EventRole#JUDGE} assignment for this exact event
   * but no organization membership is a recognized actor for the event, just not authorized for
   * this action, so gets 403 rather than the anti-enumeration 404 a total stranger gets.
   */
  public void requireOrganizer(Event event, UUID userId) {
    boolean isMember =
        membershipRepository
            .findByOrganizationIdAndUserId(event.getOrganizationId(), userId)
            .isPresent();
    if (isMember) {
      return;
    }
    if (eventStaffAssignmentRepository.existsByEventIdAndUserId(event.getId(), userId)) {
      throw ApiException.forbidden("Organizer role required");
    }
    throw ApiException.notFound("Event not found");
  }

  /**
   * Judge-or-organizer authorization for judge-level actions (judge result entry, etc. — 00 §5
   * assigns these to Judge and Organizer alike). Any organization member already qualifies (M1's
   * simplified two-role org model has no lesser "organization staff" tier yet — see DECISIONS.md);
   * otherwise the caller must hold an explicit {@link EventRole#JUDGE} assignment for this exact
   * event.
   */
  public void requireJudgeOrOrganizer(Event event, UUID userId) {
    boolean isMember =
        membershipRepository
            .findByOrganizationIdAndUserId(event.getOrganizationId(), userId)
            .isPresent();
    boolean isJudge =
        eventStaffAssignmentRepository.existsByEventIdAndUserIdAndRole(
            event.getId(), userId, EventRole.JUDGE);
    if (!isMember && !isJudge) {
      throw ApiException.notFound("Event not found");
    }
  }

  /**
   * Resolves which staff role the caller holds for this event (00 §5/§9: "Display role and event
   * scope visibly on staff pages" — the UI's {@code RoleBanner} needs this, and a judge who is not
   * an organization member cannot otherwise discover their own role, since every other
   * organizer-scoped read 404s for them). 404 for a caller with no relationship to the event at
   * all, matching every other anti-enumeration check in this class.
   */
  public ResolvedStaffRole resolveStaffRole(Event event, UUID userId) {
    var membership =
        membershipRepository.findByOrganizationIdAndUserId(event.getOrganizationId(), userId);
    if (membership.isPresent()) {
      return membership.get().getRole() == OrgRole.OWNER
          ? ResolvedStaffRole.OWNER
          : ResolvedStaffRole.ORGANIZER;
    }
    if (eventStaffAssignmentRepository.existsByEventIdAndUserIdAndRole(
        event.getId(), userId, EventRole.JUDGE)) {
      return ResolvedStaffRole.JUDGE;
    }
    throw ApiException.notFound("Event not found");
  }

  public enum ResolvedStaffRole {
    OWNER,
    ORGANIZER,
    JUDGE
  }
}

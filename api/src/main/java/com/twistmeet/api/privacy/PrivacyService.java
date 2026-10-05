package com.twistmeet.api.privacy;

import com.twistmeet.api.auth.AuthDtos.UserView;
import com.twistmeet.api.auth.User;
import com.twistmeet.api.auth.UserRepository;
import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.AuditService;
import com.twistmeet.api.competition.AttemptDtos.AttemptView;
import com.twistmeet.api.competition.AttemptRepository;
import com.twistmeet.api.competition.CorrectionDtos.CorrectionView;
import com.twistmeet.api.competition.CorrectionRepository;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.event.EventStaffAssignment;
import com.twistmeet.api.event.EventStaffAssignmentRepository;
import com.twistmeet.api.help.HelpRequestDtos.HelpRequestView;
import com.twistmeet.api.help.HelpRequestRepository;
import com.twistmeet.api.org.Organization;
import com.twistmeet.api.org.OrganizationMembership;
import com.twistmeet.api.org.OrganizationMembershipRepository;
import com.twistmeet.api.org.OrganizationRepository;
import com.twistmeet.api.privacy.PrivacyDtos.EventStaffAssignmentExport;
import com.twistmeet.api.privacy.PrivacyDtos.GuestDataExport;
import com.twistmeet.api.privacy.PrivacyDtos.MyDataExport;
import com.twistmeet.api.privacy.PrivacyDtos.OrganizationMembershipExport;
import com.twistmeet.api.registration.EventEntrant;
import com.twistmeet.api.registration.RegistrationDtos.EntrantView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 04 "Give organizers export and deletion processes while preserving legitimate dispute/audit needs
 * with a stated schedule." This is the policy-independent mechanism only: a self-service export of
 * everything a person can already see about themselves, and a deletion *request* flag that an
 * operator acts on once a retention policy exists (see DATA_RETENTION_DECISIONS.md at the repo
 * root). Nothing here performs an automated delete.
 */
@Service
public class PrivacyService {

  private final UserRepository userRepository;
  private final OrganizationMembershipRepository organizationMembershipRepository;
  private final EventStaffAssignmentRepository eventStaffAssignmentRepository;
  private final OrganizationRepository organizationRepository;
  private final EventRepository eventRepository;
  private final AttemptRepository attemptRepository;
  private final CorrectionRepository correctionRepository;
  private final HelpRequestRepository helpRequestRepository;
  private final AuditService auditService;

  public PrivacyService(
      UserRepository userRepository,
      OrganizationMembershipRepository organizationMembershipRepository,
      EventStaffAssignmentRepository eventStaffAssignmentRepository,
      OrganizationRepository organizationRepository,
      EventRepository eventRepository,
      AttemptRepository attemptRepository,
      CorrectionRepository correctionRepository,
      HelpRequestRepository helpRequestRepository,
      AuditService auditService) {
    this.userRepository = userRepository;
    this.organizationMembershipRepository = organizationMembershipRepository;
    this.eventStaffAssignmentRepository = eventStaffAssignmentRepository;
    this.organizationRepository = organizationRepository;
    this.eventRepository = eventRepository;
    this.attemptRepository = attemptRepository;
    this.correctionRepository = correctionRepository;
    this.helpRequestRepository = helpRequestRepository;
    this.auditService = auditService;
  }

  public MyDataExport exportMyData(UUID userId) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));

    List<OrganizationMembershipExport> memberships =
        organizationMembershipRepository.findByUserId(userId).stream()
            .map(
                (OrganizationMembership m) -> {
                  String orgName =
                      organizationRepository
                          .findById(m.getOrganizationId())
                          .map(Organization::getName)
                          .orElse("(deleted organization)");
                  return new OrganizationMembershipExport(
                      m.getOrganizationId(), orgName, m.getRole().name(), m.getCreatedAt());
                })
            .toList();

    List<EventStaffAssignmentExport> assignments =
        eventStaffAssignmentRepository.findByUserId(userId).stream()
            .map(
                (EventStaffAssignment a) -> {
                  String eventName =
                      eventRepository
                          .findById(a.getEventId())
                          .map(Event::getName)
                          .orElse("(deleted event)");
                  return new EventStaffAssignmentExport(
                      a.getEventId(), eventName, a.getRole().name(), a.getAssignedAt());
                })
            .toList();

    auditService.recordSelfServiceAction(userId, "SELF_DATA_EXPORTED", "User", userId.toString());
    return new MyDataExport(UserView.of(user), memberships, assignments, Instant.now());
  }

  @Transactional
  public UserView requestMyDeletion(UUID userId) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
    user.requestDeletion();
    user = userRepository.save(user);
    auditService.recordSelfServiceAction(
        userId, "SELF_DELETION_REQUESTED", "User", userId.toString());
    return UserView.of(user);
  }

  public GuestDataExport exportGuestData(EventEntrant entrant) {
    List<AttemptView> attempts =
        attemptRepository.findByEntrantId(entrant.getId()).stream().map(AttemptView::of).toList();
    List<UUID> attemptIds = attempts.stream().map(AttemptView::id).toList();
    List<CorrectionView> corrections =
        correctionRepository.findByAttemptIdIn(attemptIds).stream()
            .filter(c -> c.getRequestedBy().equals(entrant.getId()))
            .map(CorrectionView::of)
            .toList();
    List<HelpRequestView> helpRequests =
        helpRequestRepository.findByEntrantId(entrant.getId()).stream()
            .map(HelpRequestView::of)
            .toList();
    auditService.recordGuestAction(
        entrant.getEventId(),
        entrant.getId(),
        "SELF_DATA_EXPORTED",
        "EventEntrant",
        entrant.getId().toString());
    return new GuestDataExport(
        EntrantView.of(entrant), attempts, corrections, helpRequests, Instant.now());
  }
}

package com.twistmeet.api.help;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.AuditService;
import com.twistmeet.api.competition.Attempt;
import com.twistmeet.api.competition.AttemptRepository;
import com.twistmeet.api.competition.ResultStatus;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.org.TenantAccessService;
import com.twistmeet.api.registration.EventEntrant;
import com.twistmeet.api.registration.GuestAuthResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 07 P05 "Request judge/help" — available any time during an attempt, before a result exists.
 * Distinct from a correction request (contests an already-recorded result); see {@code
 * com.twistmeet.api.competition.CorrectionService}.
 */
@Service
public class HelpRequestService {

  private final HelpRequestRepository helpRequestRepository;
  private final AttemptRepository attemptRepository;
  private final EventRepository eventRepository;
  private final TenantAccessService tenantAccessService;
  private final GuestAuthResolver guestAuthResolver;
  private final AuditService auditService;

  public HelpRequestService(
      HelpRequestRepository helpRequestRepository,
      AttemptRepository attemptRepository,
      EventRepository eventRepository,
      TenantAccessService tenantAccessService,
      GuestAuthResolver guestAuthResolver,
      AuditService auditService) {
    this.helpRequestRepository = helpRequestRepository;
    this.attemptRepository = attemptRepository;
    this.eventRepository = eventRepository;
    this.tenantAccessService = tenantAccessService;
    this.guestAuthResolver = guestAuthResolver;
    this.auditService = auditService;
  }

  @Transactional
  public HelpRequest create(UUID attemptId, HttpServletRequest request) {
    Attempt attempt =
        attemptRepository
            .findById(attemptId)
            .orElseThrow(() -> ApiException.notFound("Attempt not found"));
    Event event =
        eventRepository
            .findById(attempt.getEventId())
            .orElseThrow(() -> ApiException.notFound("Event not found"));
    EventEntrant entrant = guestAuthResolver.requireEntrantForEvent(request, event.getId());
    if (!entrant.getId().equals(attempt.getEntrantId())) {
      throw ApiException.notFound("Attempt not found");
    }
    if (attempt.getResultStatus() != ResultStatus.PENDING) {
      throw ApiException.invalidTransition(
          "Help requests are only for an attempt still in progress; use a correction request"
              + " once a result is recorded");
    }
    if (helpRequestRepository.existsByAttemptIdAndState(attemptId, HelpRequestState.PENDING)) {
      // Idempotent: repeatedly tapping "Request help" doesn't flood the staff queue.
      return helpRequestRepository.findByEventId(event.getId()).stream()
          .filter(
              h -> h.getAttemptId().equals(attemptId) && h.getState() == HelpRequestState.PENDING)
          .findFirst()
          .orElseThrow();
    }
    HelpRequest helpRequest =
        helpRequestRepository.save(new HelpRequest(attemptId, event.getId(), entrant.getId()));
    auditService.recordGuestAction(
        event.getId(),
        entrant.getId(),
        "HELP_REQUESTED",
        "HelpRequest",
        helpRequest.getId().toString());
    return helpRequest;
  }

  public List<HelpRequest> listForEvent(
      UUID eventId, UUID staffUserId, HelpRequestState stateFilter) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> ApiException.notFound("Event not found"));
    tenantAccessService.requireJudgeOrOrganizer(event, staffUserId);
    return stateFilter == null
        ? helpRequestRepository.findByEventId(eventId)
        : helpRequestRepository.findByEventIdAndState(eventId, stateFilter);
  }

  @Transactional
  public HelpRequest resolve(UUID helpRequestId, UUID staffUserId) {
    HelpRequest helpRequest =
        helpRequestRepository
            .findById(helpRequestId)
            .orElseThrow(() -> ApiException.notFound("Help request not found"));
    Event event =
        eventRepository
            .findById(helpRequest.getEventId())
            .orElseThrow(() -> ApiException.notFound("Event not found"));
    tenantAccessService.requireJudgeOrOrganizer(event, staffUserId);
    helpRequest.resolve(staffUserId);
    helpRequest = helpRequestRepository.save(helpRequest);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        event.getId(),
        staffUserId,
        "HELP_REQUEST_RESOLVED",
        "HelpRequest",
        helpRequestId.toString(),
        null);
    return helpRequest;
  }
}

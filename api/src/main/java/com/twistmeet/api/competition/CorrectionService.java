package com.twistmeet.api.competition;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.AuditService;
import com.twistmeet.api.competition.CorrectionDtos.CreateCorrectionRequest;
import com.twistmeet.api.competition.CorrectionDtos.DecisionRequest;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.org.TenantAccessService;
import com.twistmeet.api.registration.EventEntrant;
import com.twistmeet.api.registration.GuestAuthResolver;
import com.twistmeet.api.scramble.ScrambleService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 02 "Corrections and extra attempts". Decisions are Organizer-only, per 00 §5's role table
 * ("approve correction" listed under Organizer, not Judge) and 08's endpoint contract ("POST
 * /corrections/{id}/decision organizer"); 02's narrative text separately says "Judge/organizer can
 * accept, reject, or hold the request," which conflicts with both. Per 00 §12's stated precedence
 * (00 itself, then the more specific 07/08/09 contracts, over narrative supporting docs), this
 * implementation follows 00 §5 + 08 and restricts decisions to Organizer/Owner — recorded as a
 * resolved document conflict in DECISIONS.md rather than silently picked.
 */
@Service
public class CorrectionService {

  private final CorrectionRepository correctionRepository;
  private final AttemptRepository attemptRepository;
  private final AttemptService attemptService;
  private final EventRepository eventRepository;
  private final TenantAccessService tenantAccessService;
  private final GuestAuthResolver guestAuthResolver;
  private final AuditService auditService;
  private final ScrambleService scrambleService;

  public CorrectionService(
      CorrectionRepository correctionRepository,
      AttemptRepository attemptRepository,
      AttemptService attemptService,
      EventRepository eventRepository,
      TenantAccessService tenantAccessService,
      GuestAuthResolver guestAuthResolver,
      AuditService auditService,
      ScrambleService scrambleService) {
    this.correctionRepository = correctionRepository;
    this.attemptRepository = attemptRepository;
    this.attemptService = attemptService;
    this.eventRepository = eventRepository;
    this.tenantAccessService = tenantAccessService;
    this.guestAuthResolver = guestAuthResolver;
    this.auditService = auditService;
    this.scrambleService = scrambleService;
  }

  @Transactional
  public Correction create(
      UUID attemptId, HttpServletRequest request, CreateCorrectionRequest body) {
    Attempt attempt = findAttemptOrNotFound(attemptId);
    Event event = findEventOrNotFound(attempt.getEventId());
    EventEntrant entrant = guestAuthResolver.requireEntrantForEvent(request, event.getId());
    if (!entrant.getId().equals(attempt.getEntrantId())) {
      throw ApiException.notFound("Attempt not found");
    }
    if (attempt.getResultStatus() == ResultStatus.PENDING) {
      throw ApiException.invalidTransition("Attempt has no recorded result to contest yet");
    }
    Correction correction =
        correctionRepository.save(
            new Correction(attemptId, entrant.getId(), body.category(), body.note()));
    auditService.recordGuestAction(
        event.getId(),
        entrant.getId(),
        "CORRECTION_REQUESTED",
        "Correction",
        correction.getId().toString());
    return correction;
  }

  public List<Correction> listForEvent(
      UUID eventId, UUID staffUserId, CorrectionState stateFilter) {
    Event event = findEventOrNotFound(eventId);
    tenantAccessService.requireJudgeOrOrganizer(event, staffUserId);
    List<UUID> attemptIds =
        attemptRepository.findByEventId(eventId).stream().map(Attempt::getId).toList();
    if (stateFilter != null) {
      return correctionRepository.findByAttemptIdInAndState(attemptIds, stateFilter);
    }
    return correctionRepository.findByAttemptIdIn(attemptIds);
  }

  @Transactional
  public Correction decide(UUID correctionId, UUID actorUserId, DecisionRequest body) {
    Correction correction =
        correctionRepository
            .findById(correctionId)
            .orElseThrow(() -> ApiException.notFound("Correction not found"));
    Attempt attempt = findAttemptOrNotFound(correction.getAttemptId());
    Event event = findEventOrNotFound(attempt.getEventId());
    tenantAccessService.requireOrganizer(event, actorUserId);

    if (correction.getState() != CorrectionState.PENDING) {
      throw ApiException.invalidTransition("Correction has already been decided");
    }
    if (correction.getVersion() != body.expectedVersion()) {
      throw ApiException.conflict(
          "STALE_VERSION", "Correction has been modified since you loaded it");
    }

    switch (body.action()) {
      case ACCEPT_NO_RETRY -> attemptService.voidAttempt(attempt, actorUserId, body.reason());
      case ACCEPT_RETRY -> {
        attemptService.voidAttempt(attempt, actorUserId, body.reason());
        Attempt replacement = attemptService.createReplacementAttempt(attempt);
        // 02: "assign the next unused extra scramble if the event policy grants a replacement."
        // A no-op if this round has no scramble batch (M4 feature not in use for this event).
        scrambleService.assignScrambleIfBatchExists(replacement);
      }
      case REJECT, NEED_INFO -> {
        // Preserve the original result; no change to the attempt (02: "preserve the original
        // result" — a reject or a request for more detail makes no change at all).
      }
    }

    correction.decide(body.action(), actorUserId, body.reason());
    correction = correctionRepository.save(correction);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        event.getId(),
        actorUserId,
        "CORRECTION_DECIDED_" + body.action(),
        "Correction",
        correctionId.toString(),
        body.reason());
    return correction;
  }

  private Attempt findAttemptOrNotFound(UUID attemptId) {
    return attemptRepository
        .findById(attemptId)
        .orElseThrow(() -> ApiException.notFound("Attempt not found"));
  }

  private Event findEventOrNotFound(UUID eventId) {
    return eventRepository
        .findById(eventId)
        .orElseThrow(() -> ApiException.notFound("Event not found"));
  }
}

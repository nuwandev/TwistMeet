package com.twistmeet.api.registration;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.AuditService;
import com.twistmeet.api.competition.AttemptRepository;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.org.TenantAccessService;
import com.twistmeet.api.registration.RegistrationDtos.AddEntrantRequest;
import com.twistmeet.api.registration.RegistrationDtos.UpdateEntrantRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff roster management (00 §2.4, 07 S05): add, edit, check-in, remove-before-assignment,
 * withdraw-after-start. Removing and withdrawing are deliberately different operations — a removal
 * before any attempt exists is a hard delete (08: "only before attempt assignment... returns
 * tombstone"), because there is no score/audit history yet to preserve; a withdrawal after attempts
 * exist never deletes the row, it only marks the entrant withdrawn, because 00 §6 requires "no
 * hard-delete after attempt assignment" and the entrant's existing results must stay in the record.
 */
@Service
public class RosterService {

  private final EventRepository eventRepository;
  private final EventEntrantRepository entrantRepository;
  private final AttemptRepository attemptRepository;
  private final TenantAccessService tenantAccessService;
  private final AuditService auditService;

  public RosterService(
      EventRepository eventRepository,
      EventEntrantRepository entrantRepository,
      AttemptRepository attemptRepository,
      TenantAccessService tenantAccessService,
      AuditService auditService) {
    this.eventRepository = eventRepository;
    this.entrantRepository = entrantRepository;
    this.attemptRepository = attemptRepository;
    this.tenantAccessService = tenantAccessService;
    this.auditService = auditService;
  }

  @Transactional
  public EventEntrant add(UUID eventId, UUID actorUserId, AddEntrantRequest request) {
    Event event = findEventOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    String displayName = DisplayNamePolicy.normalize(request.displayName());
    String disambiguated =
        DisplayNamePolicy.disambiguate(entrantRepository.findByEventId(eventId), displayName);
    EventEntrant entrant = entrantRepository.save(new EventEntrant(eventId, disambiguated));
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "ENTRANT_ADDED",
        "EventEntrant",
        entrant.getId().toString(),
        null);
    return entrant;
  }

  @Transactional
  public EventEntrant update(
      UUID eventId, UUID entrantId, UUID actorUserId, UpdateEntrantRequest request) {
    Event event = findEventOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    EventEntrant entrant = findEntrantOrNotFound(eventId, entrantId);
    String displayName = DisplayNamePolicy.normalize(request.displayName());
    List<EventEntrant> others =
        entrantRepository.findByEventId(eventId).stream()
            .filter(e -> !e.getId().equals(entrantId))
            .toList();
    entrant.rename(DisplayNamePolicy.disambiguate(others, displayName));
    entrant = entrantRepository.save(entrant);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "ENTRANT_RENAMED",
        "EventEntrant",
        entrantId.toString(),
        null);
    return entrant;
  }

  @Transactional
  public EventEntrant checkIn(UUID eventId, UUID entrantId, UUID actorUserId) {
    Event event = findEventOrNotFound(eventId);
    // 00 §5: check-in is listed under Organizer/Judge capability in S05's action list, not just
    // Organizer — a judge at the door can check entrants in.
    tenantAccessService.requireJudgeOrOrganizer(event, actorUserId);
    EventEntrant entrant = findEntrantOrNotFound(eventId, entrantId);
    entrant.checkIn();
    entrant = entrantRepository.save(entrant);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "ENTRANT_CHECKED_IN",
        "EventEntrant",
        entrantId.toString(),
        null);
    return entrant;
  }

  @Transactional
  public EventEntrant withdraw(UUID eventId, UUID entrantId, UUID actorUserId, String reason) {
    Event event = findEventOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    EventEntrant entrant = findEntrantOrNotFound(eventId, entrantId);
    if (entrant.getStatus() == EntrantStatus.WITHDRAWN) {
      return entrant; // idempotent
    }
    entrant.withdraw();
    entrant = entrantRepository.save(entrant);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "ENTRANT_WITHDRAWN",
        "EventEntrant",
        entrantId.toString(),
        reason);
    return entrant;
  }

  /** Hard delete, only before any attempt exists for this entrant; returns a tombstone. */
  @Transactional
  public RegistrationDtos.EntrantTombstone remove(UUID eventId, UUID entrantId, UUID actorUserId) {
    Event event = findEventOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    EventEntrant entrant = findEntrantOrNotFound(eventId, entrantId);
    if (attemptRepository.existsByEntrantId(entrantId)) {
      throw ApiException.invalidTransition(
          "Entrant already has attempts assigned; withdraw instead of removing");
    }
    String displayName = entrant.getDisplayName();
    entrantRepository.delete(entrant);
    Instant deletedAt = Instant.now();
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "ENTRANT_REMOVED",
        "EventEntrant",
        entrantId.toString(),
        null);
    return new RegistrationDtos.EntrantTombstone(entrantId, displayName, deletedAt);
  }

  private EventEntrant findEntrantOrNotFound(UUID eventId, UUID entrantId) {
    return entrantRepository
        .findByIdAndEventId(entrantId, eventId)
        .orElseThrow(() -> ApiException.notFound("Entrant not found"));
  }

  private Event findEventOrNotFound(UUID eventId) {
    return eventRepository
        .findById(eventId)
        .orElseThrow(() -> ApiException.notFound("Event not found"));
  }
}

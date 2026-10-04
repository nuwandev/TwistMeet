package com.twistmeet.api.event;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.AuditService;
import com.twistmeet.api.common.SecretTokens;
import com.twistmeet.api.event.EventDtos.CreateEventRequest;
import com.twistmeet.api.event.EventDtos.UpdateEventRequest;
import com.twistmeet.api.org.TenantAccessService;
import com.twistmeet.api.scoring.RulesetVersion;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Event lifecycle per 00 §6: {@code DRAFT -> REGISTRATION_OPEN -> REGISTRATION_LOCKED -> READY ->
 * LIVE -> COMPLETED -> ARCHIVED}, organizer-only transitions. M1 only drives the first three
 * states; {@code READY}/{@code LIVE}/{@code COMPLETED}/{@code ARCHIVED} require rounds and attempts
 * (M2+) and are rejected here as not-yet-implemented rather than silently allowed.
 */
@Service
public class EventService {

  private final EventRepository eventRepository;
  private final TenantAccessService tenantAccessService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public EventService(
      EventRepository eventRepository,
      TenantAccessService tenantAccessService,
      AuditService auditService,
      ObjectMapper objectMapper) {
    this.eventRepository = eventRepository;
    this.tenantAccessService = tenantAccessService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Event create(UUID organizationId, UUID actorUserId, CreateEventRequest request) {
    tenantAccessService.requireAnyStaffRole(organizationId, actorUserId);
    validateTimezone(request.timezone());

    EventVisibility visibility =
        request.visibility() == null ? EventVisibility.PRIVATE : request.visibility();
    if (request.scramblePolicy() == ScramblePolicy.SELF_SCRAMBLE
        && request.timerMode() != TimerMode.PHONE_CASUAL) {
      // 00 §7: "Competitor self-scramble (only enabled in casual phone mode)."
      throw ApiException.badRequest(
          "SCRAMBLE_POLICY_INVALID", "Self-scramble is only allowed in casual phone-timer mode");
    }
    String code = SecretTokens.newJoinCode();
    Event event =
        new Event(
            organizationId,
            request.name(),
            request.description(),
            request.startsAt(),
            request.timezone(),
            request.venueLabel(),
            visibility,
            code,
            SecretTokens.sha256Hex(code));
    if (request.timerMode() != null) {
      event.setTimerMode(request.timerMode());
    }
    if (request.scramblePolicy() != null) {
      event.setScramblePolicy(request.scramblePolicy());
    }
    event = eventRepository.save(event);
    auditService.recordStaffAction(
        organizationId,
        event.getId(),
        actorUserId,
        "EVENT_CREATED",
        "Event",
        event.getId().toString(),
        null);
    return event;
  }

  public List<Event> listForOrganization(UUID organizationId, UUID actorUserId) {
    tenantAccessService.requireAnyStaffRole(organizationId, actorUserId);
    return eventRepository.findByOrganizationId(organizationId);
  }

  public Event getForStaff(UUID eventId, UUID actorUserId) {
    Event event = findOrNotFound(eventId);
    tenantAccessService.requireAnyStaffRole(event.getOrganizationId(), actorUserId);
    return event;
  }

  @Transactional
  public Event update(UUID eventId, UUID actorUserId, UpdateEventRequest request) {
    Event event = findOrNotFound(eventId);
    tenantAccessService.requireAnyStaffRole(event.getOrganizationId(), actorUserId);
    if (event.getState() != EventState.DRAFT) {
      // Simplification recorded in DECISIONS.md: 08 allows editing until entrants are
      // registered; M1 takes the safer, simpler rule of locking edits as soon as registration
      // opens rather than tracking entrant existence separately.
      throw ApiException.invalidTransition("Event can only be edited while in DRAFT");
    }
    event.applyDraftEdits(request.name(), request.description(), request.venueLabel());
    return eventRepository.save(event);
  }

  @Transactional
  public Event openRegistration(UUID eventId, UUID actorUserId) {
    Event event =
        transition(
            eventId,
            actorUserId,
            EventState.DRAFT,
            EventState.REGISTRATION_OPEN,
            "REGISTRATION_OPENED");
    if (event.getRulesetSnapshot() == null) {
      RulesetSnapshot snapshot =
          new RulesetSnapshot(
              RulesetVersion.V1,
              event.getPuzzleType(),
              event.getTimerMode(),
              event.getScramblePolicy(),
              Instant.now());
      event.setRulesetSnapshot(objectMapper.writeValueAsString(snapshot));
      event = eventRepository.save(event);
    }
    return event;
  }

  @Transactional
  public Event lockRegistration(UUID eventId, UUID actorUserId) {
    return transition(
        eventId,
        actorUserId,
        EventState.REGISTRATION_OPEN,
        EventState.REGISTRATION_LOCKED,
        "REGISTRATION_LOCKED");
  }

  @Transactional
  public Event reopenRegistration(UUID eventId, UUID actorUserId) {
    return transition(
        eventId,
        actorUserId,
        EventState.REGISTRATION_LOCKED,
        EventState.REGISTRATION_OPEN,
        "REGISTRATION_REOPENED");
  }

  @Transactional
  public Event rotateJoinCode(UUID eventId, UUID actorUserId) {
    Event event = findOrNotFound(eventId);
    tenantAccessService.requireAnyStaffRole(event.getOrganizationId(), actorUserId);
    String code = SecretTokens.newJoinCode();
    event.rotateJoinCode(code, SecretTokens.sha256Hex(code));
    event = eventRepository.save(event);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        event.getId(),
        actorUserId,
        "JOIN_CODE_ROTATED",
        "Event",
        event.getId().toString(),
        null);
    return event;
  }

  private Event transition(
      UUID eventId, UUID actorUserId, EventState from, EventState to, String auditAction) {
    Event event = findOrNotFound(eventId);
    tenantAccessService.requireAnyStaffRole(event.getOrganizationId(), actorUserId);
    if (event.getState() != from) {
      throw ApiException.invalidTransition(
          "Cannot move event from " + event.getState() + " to " + to);
    }
    event.setState(to);
    event = eventRepository.save(event);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        event.getId(),
        actorUserId,
        auditAction,
        "Event",
        event.getId().toString(),
        null);
    return event;
  }

  /**
   * Resolves the caller's staff role for this event, for {@code RoleBanner} (00 §5/§9) — unlike
   * {@link #getForStaff}, this succeeds for a judge who holds no organization membership at all,
   * since {@link TenantAccessService#resolveStaffRole} checks judge assignment as a fallback.
   */
  public TenantAccessService.ResolvedStaffRole getMyRole(UUID eventId, UUID actorUserId) {
    Event event = findOrNotFound(eventId);
    return tenantAccessService.resolveStaffRole(event, actorUserId);
  }

  private Event findOrNotFound(UUID eventId) {
    return eventRepository
        .findById(eventId)
        .orElseThrow(() -> ApiException.notFound("Event not found"));
  }

  private void validateTimezone(String timezone) {
    try {
      ZoneId.of(timezone);
    } catch (DateTimeException e) {
      throw ApiException.badRequest("INVALID_TIMEZONE", "timezone must be a valid IANA zone id");
    }
  }
}

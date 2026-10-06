package com.twistmeet.api.event;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.AuditService;
import com.twistmeet.api.common.SecretTokens;
import com.twistmeet.api.event.EventDtos.CreateEventRequest;
import com.twistmeet.api.event.EventDtos.UpdateEventRequest;
import com.twistmeet.api.org.TenantAccessService;
import com.twistmeet.api.scoring.RulesetVersion;
import com.twistmeet.api.stream.EventStreamService;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Event lifecycle per 00 §6: {@code DRAFT -> REGISTRATION_OPEN -> REGISTRATION_LOCKED -> READY ->
 * LIVE -> COMPLETED -> ARCHIVED}, organizer-only transitions. {@code READY}/{@code LIVE} are
 * tracked at the round level ({@link com.twistmeet.api.competition.RoundState}) rather than
 * mirrored onto {@code Event.state} — round-level state is what every other screen/endpoint
 * actually reads (documented in DECISIONS.md); this service drives {@code COMPLETED} and {@code
 * ARCHIVED} directly off round completion instead of an unused intermediate event-level READY/LIVE
 * pair.
 */
@Service
public class EventService {

  private static final Set<EventState> ARCHIVABLE_FROM =
      EnumSet.of(
          EventState.DRAFT,
          EventState.REGISTRATION_OPEN,
          EventState.REGISTRATION_LOCKED,
          EventState.COMPLETED);

  private final EventRepository eventRepository;
  private final com.twistmeet.api.competition.RoundRepository roundRepository;
  private final TenantAccessService tenantAccessService;
  private final AuditService auditService;
  private final ObjectMapper objectMapper;
  private final EventStreamService eventStreamService;

  public EventService(
      EventRepository eventRepository,
      com.twistmeet.api.competition.RoundRepository roundRepository,
      TenantAccessService tenantAccessService,
      AuditService auditService,
      ObjectMapper objectMapper,
      EventStreamService eventStreamService) {
    this.eventRepository = eventRepository;
    this.roundRepository = roundRepository;
    this.tenantAccessService = tenantAccessService;
    this.auditService = auditService;
    this.objectMapper = objectMapper;
    this.eventStreamService = eventStreamService;
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
    requireNotArchived(event);
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

  /**
   * Publish/unpublish are intentionally not gated on {@link EventState}: that state machine barely
   * advances past {@code REGISTRATION_LOCKED} in practice (rounds/attempts drive the
   * actually-useful progress — see {@link RoundState}), so gating on it would make publish
   * unreachable for a normal event. Any organizer, at any event state, may publish or unpublish;
   * what the public page actually shows is separately gated per-round (see PublicStandingsService).
   */
  @Transactional
  public Event publish(UUID eventId, UUID actorUserId) {
    Event event = findOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    requireNotArchived(event);
    event.publish(event.getPublicSlug() == null ? newUniqueSlug() : null);
    event = eventRepository.save(event);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        event.getId(),
        actorUserId,
        "EVENT_PUBLISHED",
        "Event",
        event.getId().toString(),
        null);
    eventStreamService.publish(
        event.getId(), "EVENT_PUBLISHED", event.getId(), Set.of("publishedAt"));
    return event;
  }

  @Transactional
  public Event unpublish(UUID eventId, UUID actorUserId) {
    Event event = findOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    event.unpublish(newUniqueSlug());
    event = eventRepository.save(event);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        event.getId(),
        actorUserId,
        "EVENT_UNPUBLISHED",
        "Event",
        event.getId().toString(),
        null);
    eventStreamService.publish(
        event.getId(), "EVENT_UNPUBLISHED", event.getId(), Set.of("publishedAt"));
    return event;
  }

  /** 07 S12 "Public name masking option." */
  @Transactional
  public Event setPublicNameMask(UUID eventId, UUID actorUserId, boolean masked) {
    Event event = findOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    requireNotArchived(event);
    event.setPublicNameMask(masked);
    event = eventRepository.save(event);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        masked ? "PUBLIC_NAME_MASK_ENABLED" : "PUBLIC_NAME_MASK_DISABLED",
        "Event",
        eventId.toString(),
        null);
    return event;
  }

  private String newUniqueSlug() {
    String slug;
    do {
      slug = SecretTokens.newOpaqueToken().replaceAll("[^A-Za-z0-9]", "").substring(0, 16);
    } while (eventRepository.existsByPublicSlug(slug));
    return slug;
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
    requireNotArchived(event);
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

  /**
   * COMPLETED once every configured round is CLOSED (00 §6). Driven off round state rather than a
   * separate manual trigger, since "every round closed" is the only meaningful definition of "the
   * competition is over" this data model has.
   */
  @Transactional
  public Event complete(UUID eventId, UUID actorUserId) {
    Event event = findOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    requireNotArchived(event);
    if (event.getState() == EventState.COMPLETED) {
      return event; // idempotent
    }
    var rounds = roundRepository.findByEventIdOrderByOrder(eventId);
    if (rounds.isEmpty()) {
      throw ApiException.invalidTransition("Event has no rounds to complete");
    }
    boolean anyNotClosed =
        rounds.stream()
            .anyMatch(r -> r.getState() != com.twistmeet.api.competition.RoundState.CLOSED);
    if (anyNotClosed) {
      throw ApiException.invalidTransition("Every round must be CLOSED before the event completes");
    }
    event.setState(EventState.COMPLETED);
    event = eventRepository.save(event);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "EVENT_COMPLETED",
        "Event",
        eventId.toString(),
        null);
    return event;
  }

  /**
   * "A completed event can be reopened for corrections; record actor/reason and recompute derived
   * standings transparently" (00 §6). Standings are always computed live (never cached), so
   * "recompute transparently" already happens automatically; this method's job is just the
   * event-level state move plus the required actor/reason audit record. Corrections themselves
   * (CorrectionService) already accept a decision regardless of round state — reopening the event
   * doesn't additionally unlock anything at the round level, it only reflects that the event is no
   * longer considered finally closed out.
   */
  @Transactional
  public Event reopen(UUID eventId, UUID actorUserId, String reason) {
    Event event = findOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    requireNotArchived(event);
    if (event.getState() != EventState.COMPLETED) {
      throw ApiException.invalidTransition("Only a COMPLETED event can be reopened");
    }
    event.setState(EventState.REGISTRATION_LOCKED);
    event = eventRepository.save(event);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "EVENT_REOPENED",
        "Event",
        eventId.toString(),
        reason);
    return event;
  }

  /**
   * "Require an explicit confirmation for... event archive" (00 §5) — enforced client-side via
   * {@code ConfirmDialog}, same pattern as round close/void/publish. "Archived is read-only except
   * export/delete request processes" (00 §6): {@link #requireNotArchived} enforces that on every
   * other mutating method in this service and in {@code RoundService}.
   */
  @Transactional
  public Event archive(UUID eventId, UUID actorUserId) {
    Event event = findOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    if (!ARCHIVABLE_FROM.contains(event.getState())) {
      throw ApiException.invalidTransition("Cannot archive an event in state " + event.getState());
    }
    event.setState(EventState.ARCHIVED);
    event = eventRepository.save(event);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "EVENT_ARCHIVED",
        "Event",
        eventId.toString(),
        null);
    return event;
  }

  /**
   * 00 §2: "Event create/edit... rules preview, clone, archive and event history." Clones
   * configuration only (name, description, timezone, venue, visibility, timer mode, scramble
   * policy) into a brand-new DRAFT event with its own join code — never entrants, rounds, attempts,
   * or results, which "clone" in the S14 "copy event settings" sense never implies.
   */
  @Transactional
  public Event clone(UUID eventId, UUID actorUserId) {
    Event source = findOrNotFound(eventId);
    tenantAccessService.requireAnyStaffRole(source.getOrganizationId(), actorUserId);
    String code = SecretTokens.newJoinCode();
    Event copy =
        new Event(
            source.getOrganizationId(),
            source.getName() + " (copy)",
            source.getDescription(),
            source.getStartsAt(),
            source.getTimezone(),
            source.getVenueLabel(),
            source.getVisibility(),
            code,
            SecretTokens.sha256Hex(code));
    copy.setTimerMode(source.getTimerMode());
    copy.setScramblePolicy(source.getScramblePolicy());
    copy = eventRepository.save(copy);
    auditService.recordStaffAction(
        source.getOrganizationId(),
        copy.getId(),
        actorUserId,
        "EVENT_CLONED",
        "Event",
        eventId.toString(),
        null);
    return copy;
  }

  private void requireNotArchived(Event event) {
    if (event.getState() == EventState.ARCHIVED) {
      throw ApiException.invalidTransition("Archived events are read-only");
    }
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

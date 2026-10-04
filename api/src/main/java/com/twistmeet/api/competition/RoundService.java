package com.twistmeet.api.competition;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.AuditService;
import com.twistmeet.api.competition.RoundDtos.CreateRoundRequest;
import com.twistmeet.api.competition.RoundDtos.UpdateRoundRequest;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.event.EventState;
import com.twistmeet.api.event.TimerMode;
import com.twistmeet.api.org.TenantAccessService;
import com.twistmeet.api.registration.EntrantStatus;
import com.twistmeet.api.registration.EventEntrant;
import com.twistmeet.api.registration.EventEntrantRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Round lifecycle (00 §6: {@code DRAFT -> PREPARING -> READY -> LIVE -> REVIEW -> CLOSED}).
 *
 * <p>08 says round creation/edit is "draft event only"/"before live" without defining what "draft
 * event" means given that rounds are normally configured while registration is open, not only while
 * the event itself sits in {@code DRAFT}. Taken literally, that would make it impossible to ever
 * set up rounds for an event that has already opened registration — not a plausible intended
 * behavior. This implementation allows round creation/edit any time before the event reaches {@code
 * READY}/{@code LIVE} (i.e. while {@code DRAFT}, {@code REGISTRATION_OPEN}, or {@code
 * REGISTRATION_LOCKED}), and records this as a resolved ambiguity in DECISIONS.md per 00 §12.
 */
@Service
public class RoundService {

  private static final Set<EventState> ROUND_CONFIGURABLE_EVENT_STATES =
      EnumSet.of(EventState.DRAFT, EventState.REGISTRATION_OPEN, EventState.REGISTRATION_LOCKED);

  private final RoundRepository roundRepository;
  private final EventRepository eventRepository;
  private final EventEntrantRepository entrantRepository;
  private final AttemptRepository attemptRepository;
  private final CorrectionRepository correctionRepository;
  private final TenantAccessService tenantAccessService;
  private final AuditService auditService;

  public RoundService(
      RoundRepository roundRepository,
      EventRepository eventRepository,
      EventEntrantRepository entrantRepository,
      AttemptRepository attemptRepository,
      CorrectionRepository correctionRepository,
      TenantAccessService tenantAccessService,
      AuditService auditService) {
    this.roundRepository = roundRepository;
    this.eventRepository = eventRepository;
    this.entrantRepository = entrantRepository;
    this.attemptRepository = attemptRepository;
    this.correctionRepository = correctionRepository;
    this.tenantAccessService = tenantAccessService;
    this.auditService = auditService;
  }

  @Transactional
  public Round create(UUID eventId, UUID actorUserId, CreateRoundRequest request) {
    Event event = findEventOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    if (!ROUND_CONFIGURABLE_EVENT_STATES.contains(event.getState())) {
      throw ApiException.invalidTransition(
          "Rounds can only be configured before the event is READY/LIVE");
    }
    validateAdvancement(request.advancementRule(), request.advancementValue(), null);
    if (roundRepository.existsByEventIdAndOrder(eventId, request.order())) {
      throw ApiException.badRequest("ROUND_ORDER_TAKEN", "A round with that order already exists");
    }
    Round round =
        roundRepository.save(
            new Round(
                eventId,
                request.order(),
                request.name(),
                request.format(),
                request.advancementRule(),
                request.advancementValue(),
                request.tiePolicy()));
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "ROUND_CREATED",
        "Round",
        round.getId().toString(),
        null);
    return round;
  }

  @Transactional
  public Round update(UUID roundId, UUID actorUserId, UpdateRoundRequest request) {
    Round round = findRoundOrNotFound(roundId);
    Event event = findEventOrNotFound(round.getEventId());
    tenantAccessService.requireOrganizer(event, actorUserId);
    if (round.getState() != RoundState.DRAFT) {
      throw ApiException.invalidTransition("Round can only be edited while DRAFT");
    }
    validateAdvancement(request.advancementRule(), request.advancementValue(), null);
    round.applyDraftEdits(
        request.name(),
        request.format(),
        request.advancementRule(),
        request.advancementValue(),
        request.tiePolicy());
    return roundRepository.save(round);
  }

  public List<Round> listForEvent(UUID eventId, UUID actorUserId) {
    Event event = findEventOrNotFound(eventId);
    tenantAccessService.requireAnyStaffRole(event.getOrganizationId(), actorUserId);
    return roundRepository.findByEventIdOrderByOrder(eventId);
  }

  public Round getForStaff(UUID roundId, UUID actorUserId) {
    Round round = findRoundOrNotFound(roundId);
    Event event = findEventOrNotFound(round.getEventId());
    tenantAccessService.requireJudgeOrOrganizer(event, actorUserId);
    return round;
  }

  /** Freezes the current active entrants and the ruleset, and allocates attempt slots. */
  @Transactional
  public Round prepare(UUID roundId, UUID actorUserId) {
    Round round = findRoundOrNotFound(roundId);
    UUID eventId = round.getEventId();
    Event event = findEventOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    requireRoundState(round, RoundState.DRAFT, RoundState.PREPARING);

    List<EventEntrant> entrants =
        entrantRepository.findByEventId(eventId).stream()
            .filter(e -> e.getStatus() == EntrantStatus.ACTIVE)
            .toList();
    validateAdvancement(round.getAdvancementRule(), round.getAdvancementValue(), entrants.size());

    ResultSource resultSource =
        event.getTimerMode() == TimerMode.PHYSICAL_JUDGE
            ? ResultSource.JUDGE
            : ResultSource.SELF_TIMED;
    int attemptCount = round.getFormat().attemptCount();
    for (EventEntrant entrant : entrants) {
      if (attemptRepository.countByRoundIdAndEntrantId(roundId, entrant.getId()) > 0) {
        continue; // Re-running prepare on an already-prepared round is a no-op per entrant.
      }
      for (int n = 1; n <= attemptCount; n++) {
        attemptRepository.save(new Attempt(eventId, roundId, entrant.getId(), n, resultSource));
      }
    }

    round.setState(RoundState.PREPARING);
    round = roundRepository.save(round);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "ROUND_PREPARED",
        "Round",
        roundId.toString(),
        null);
    return round;
  }

  @Transactional
  public Round ready(UUID roundId, UUID actorUserId) {
    return transition(
        roundId, actorUserId, RoundState.PREPARING, RoundState.READY, "ROUND_READY", true);
  }

  @Transactional
  public Round start(UUID roundId, UUID actorUserId) {
    return transition(
        roundId, actorUserId, RoundState.READY, RoundState.LIVE, "ROUND_STARTED", false);
  }

  @Transactional
  public Round togglePause(UUID roundId, UUID actorUserId) {
    Round round = findRoundOrNotFound(roundId);
    Event event = findEventOrNotFound(round.getEventId());
    tenantAccessService.requireOrganizer(event, actorUserId);
    if (round.getState() != RoundState.LIVE) {
      throw ApiException.invalidTransition("Round must be LIVE to pause/resume");
    }
    boolean nowPaused = round.togglePause();
    round = roundRepository.save(round);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        round.getEventId(),
        actorUserId,
        nowPaused ? "ROUND_PAUSED" : "ROUND_RESUMED",
        "Round",
        roundId.toString(),
        null);
    return round;
  }

  /** REVIEW begins once every non-voided attempt in the round has a decided result status. */
  @Transactional
  public Round review(UUID roundId, UUID actorUserId) {
    Round round = findRoundOrNotFound(roundId);
    UUID eventId = round.getEventId();
    Event event = findEventOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    if (round.getState() != RoundState.LIVE) {
      throw ApiException.invalidTransition("Round must be LIVE to enter review");
    }
    List<Attempt> attempts = attemptRepository.findByRoundId(roundId);
    boolean anyUnresolved =
        attempts.stream()
            .anyMatch(
                a ->
                    a.getResultStatus() == ResultStatus.PENDING
                        && a.getState() != AttemptState.VOIDED);
    if (anyUnresolved) {
      throw ApiException.invalidTransition(
          "Every attempt must have a decided result (OK/DNF/DNS) before review");
    }
    round.setState(RoundState.REVIEW);
    round = roundRepository.save(round);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "ROUND_REVIEW",
        "Round",
        roundId.toString(),
        null);
    return round;
  }

  /** CLOSED is blocked while any correction request for an attempt in this round is pending. */
  @Transactional
  public Round close(UUID roundId, UUID actorUserId) {
    Round round = findRoundOrNotFound(roundId);
    UUID eventId = round.getEventId();
    Event event = findEventOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    if (round.getState() != RoundState.REVIEW) {
      throw ApiException.invalidTransition("Round must be in REVIEW to close");
    }
    List<UUID> attemptIds =
        attemptRepository.findByRoundId(roundId).stream().map(Attempt::getId).toList();
    boolean anyPendingCorrection =
        !correctionRepository
            .findByAttemptIdInAndState(attemptIds, CorrectionState.PENDING)
            .isEmpty();
    if (anyPendingCorrection) {
      throw ApiException.conflict(
          "CORRECTION_PENDING", "Cannot close round while correction requests are pending");
    }
    round.setState(RoundState.CLOSED);
    round = roundRepository.save(round);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "ROUND_CLOSED",
        "Round",
        roundId.toString(),
        null);
    return round;
  }

  private Round transition(
      UUID roundId,
      UUID actorUserId,
      RoundState from,
      RoundState to,
      String auditAction,
      boolean requireEntrantsExist) {
    Round round = findRoundOrNotFound(roundId);
    UUID eventId = round.getEventId();
    Event event = findEventOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    requireRoundState(round, from, to);
    if (requireEntrantsExist && attemptRepository.findByRoundId(roundId).isEmpty()) {
      throw ApiException.invalidTransition("Round has no prepared attempt slots");
    }
    round.setState(to);
    round = roundRepository.save(round);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        auditAction,
        "Round",
        roundId.toString(),
        null);
    return round;
  }

  private void requireRoundState(Round round, RoundState expected, RoundState target) {
    if (round.getState() != expected) {
      throw ApiException.invalidTransition(
          "Cannot move round from " + round.getState() + " to " + target);
    }
  }

  private void validateAdvancement(AdvancementRule rule, Integer value, Integer eligibleCount) {
    AdvancementRule effectiveRule = rule == null ? AdvancementRule.EVERYONE : rule;
    if (effectiveRule == AdvancementRule.TOP_N) {
      if (value == null || value < 1) {
        throw ApiException.badRequest("ADVANCEMENT_INVALID", "TOP_N requires a positive value");
      }
      if (eligibleCount != null && value > eligibleCount) {
        throw ApiException.badRequest(
            "ADVANCEMENT_INVALID", "Advancement count cannot exceed eligible entrants");
      }
    } else if (effectiveRule == AdvancementRule.TOP_PERCENT) {
      if (value == null || value < 1 || value > 100) {
        throw ApiException.badRequest(
            "ADVANCEMENT_INVALID", "TOP_PERCENT requires a value between 1 and 100");
      }
    }
  }

  private Round findRoundOrNotFound(UUID roundId) {
    return roundRepository
        .findById(roundId)
        .orElseThrow(() -> ApiException.notFound("Round not found"));
  }

  private Event findEventOrNotFound(UUID eventId) {
    return eventRepository
        .findById(eventId)
        .orElseThrow(() -> ApiException.notFound("Event not found"));
  }
}

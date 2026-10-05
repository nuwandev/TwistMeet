package com.twistmeet.api.competition;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.competition.AttemptDtos.JudgeResultRequest;
import com.twistmeet.api.competition.AttemptDtos.SelfTimedSubmitRequest;
import com.twistmeet.api.competition.AttemptDtos.SubmittedStatus;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.org.TenantAccessService;
import com.twistmeet.api.registration.EventEntrant;
import com.twistmeet.api.registration.GuestAuthResolver;
import com.twistmeet.api.scoring.Penalty;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attempt lifecycle (00 §6). Judge mode never exposes a timer, so a judge-recorded attempt goes
 * straight {@code PENDING -> ACCEPTED} via {@link #recordJudgeResult}; the phone/self-timed path
 * uses {@link #start}/{@link #stop}/{@link #submitSelfTimed}. Every method here is idempotent per
 * 00 §6 "every command is idempotent": repeating an already-applied transition with the same
 * effective outcome returns the current state rather than erroring.
 */
@Service
public class AttemptService {

  private static final long MIN_RAW_TIME_MS = 1; // 0.001s
  private static final long MAX_RAW_TIME_MS = 9_999_999; // 9999.999s

  private final AttemptRepository attemptRepository;
  private final RoundRepository roundRepository;
  private final EventRepository eventRepository;
  private final ResultRevisionRepository resultRevisionRepository;
  private final TenantAccessService tenantAccessService;
  private final GuestAuthResolver guestAuthResolver;

  public AttemptService(
      AttemptRepository attemptRepository,
      RoundRepository roundRepository,
      EventRepository eventRepository,
      ResultRevisionRepository resultRevisionRepository,
      TenantAccessService tenantAccessService,
      GuestAuthResolver guestAuthResolver) {
    this.attemptRepository = attemptRepository;
    this.roundRepository = roundRepository;
    this.eventRepository = eventRepository;
    this.resultRevisionRepository = resultRevisionRepository;
    this.tenantAccessService = tenantAccessService;
    this.guestAuthResolver = guestAuthResolver;
  }

  /** Role-filtered get: judge/organizer of the event, or the entrant's own guest session. */
  public Attempt getAuthorized(UUID attemptId, UUID staffUserIdOrNull, HttpServletRequest request) {
    Attempt attempt = findOrNotFound(attemptId);
    Event event = findEventOrNotFound(attempt.getEventId());
    if (staffUserIdOrNull != null) {
      try {
        tenantAccessService.requireJudgeOrOrganizer(event, staffUserIdOrNull);
        return attempt;
      } catch (ApiException e) {
        // Fall through to try guest ownership before giving up.
      }
    }
    EventEntrant entrant = guestAuthResolver.requireEntrantForEvent(request, event.getId());
    if (!entrant.getId().equals(attempt.getEntrantId())) {
      throw ApiException.notFound("Attempt not found");
    }
    return attempt;
  }

  @Transactional
  public Attempt start(UUID attemptId, HttpServletRequest request) {
    Attempt attempt = findOrNotFound(attemptId);
    Event event = findEventOrNotFound(attempt.getEventId());
    EventEntrant entrant = requireOwnEntrant(request, event.getId(), attempt);
    requireSelfTimed(attempt);
    requireRoundLiveAndNotPaused(attempt.getRoundId());
    if (attempt.getState() == AttemptState.RUNNING) {
      return attempt; // idempotent re-start
    }
    if (attempt.getState() != AttemptState.PENDING) {
      throw ApiException.invalidTransition("Attempt is not awaiting start");
    }
    attempt.start();
    return attemptRepository.save(attempt);
  }

  @Transactional
  public Attempt stop(UUID attemptId, HttpServletRequest request) {
    Attempt attempt = findOrNotFound(attemptId);
    Event event = findEventOrNotFound(attempt.getEventId());
    requireOwnEntrant(request, event.getId(), attempt);
    requireSelfTimed(attempt);
    if (attempt.getState() == AttemptState.STOPPED) {
      return attempt; // idempotent
    }
    if (attempt.getState() != AttemptState.RUNNING) {
      throw ApiException.invalidTransition("Attempt is not running");
    }
    attempt.stop();
    return attemptRepository.save(attempt);
  }

  @Transactional
  public Attempt submitSelfTimed(
      UUID attemptId, HttpServletRequest request, SelfTimedSubmitRequest body) {
    Attempt attempt = findOrNotFound(attemptId);
    Event event = findEventOrNotFound(attempt.getEventId());
    requireOwnEntrant(request, event.getId(), attempt);
    requireSelfTimed(attempt);

    ResultStatus newStatus = toResultStatus(body.status());
    Long rawTimeMs = validateRawTime(newStatus, body.rawTimeMs());
    Penalty penalty = validatePenalty(newStatus, body.penalty());

    if (attempt.getState() == AttemptState.ACCEPTED) {
      // Idempotent resubmission: identical payload returns the existing result unchanged;
      // a different payload for an already-accepted self-timed attempt is not a resubmission,
      // it is an edit, which self-timed mode has no authority to make (08 "no direct competitor
      // edit") — self-timed competitors reach further changes only through a correction request.
      if (sameResult(attempt, rawTimeMs, penalty, newStatus)) {
        return attempt;
      }
      throw ApiException.conflict("DUPLICATE_ATTEMPT", "Attempt was already submitted");
    }
    if (attempt.getState() != AttemptState.PENDING
        && attempt.getState() != AttemptState.RUNNING
        && attempt.getState() != AttemptState.STOPPED) {
      throw ApiException.invalidTransition("Attempt is not awaiting submission");
    }
    attempt.submitSelfTimed(rawTimeMs, penalty, newStatus, body.clientBuild());
    return attemptRepository.save(attempt);
  }

  /** Judge entry; every call (including the first) records a revision (08, 00 §9). */
  @Transactional
  public Attempt recordJudgeResult(UUID attemptId, UUID actorUserId, JudgeResultRequest body) {
    Attempt attempt = findOrNotFound(attemptId);
    Event event = findEventOrNotFound(attempt.getEventId());
    tenantAccessService.requireJudgeOrOrganizer(event, actorUserId);
    if (attempt.getResultSource() != ResultSource.JUDGE) {
      throw ApiException.invalidTransition("Attempt is not judge-recorded");
    }
    if (attempt.getState() == AttemptState.VOIDED) {
      throw ApiException.invalidTransition("Attempt was voided by an accepted correction");
    }
    // A tie-break attempt (09) is deliberately created and judged *after* the round's regular
    // attempts are done — by the time advancement preview surfaces a boundary tie, the round is
    // typically already REVIEW or CLOSED. Its attempt number is always beyond the round's own
    // format count, so it's unambiguous which attempts this exception applies to; every regular
    // attempt is still fully protected by the closed-round check.
    if (attempt.getAttemptNumber() <= roundFormatAttemptCount(attempt.getRoundId())) {
      requireRoundNotClosed(attempt.getRoundId());
    }

    if (attempt.getVersion() != body.expectedVersion()) {
      throw ApiException.conflict("STALE_VERSION", "Attempt has been modified since you loaded it");
    }

    ResultStatus newStatus = toResultStatus(body.status());
    Long rawTimeMs = validateRawTime(newStatus, body.rawTimeMs());
    Penalty penalty = validatePenalty(newStatus, body.penalty());

    Long previousRaw = attempt.getRawTimeMs();
    Penalty previousPenalty = attempt.getPenalty();
    ResultStatus previousStatus = attempt.getResultStatus();

    try {
      attempt.recordJudgeResult(rawTimeMs, penalty, newStatus);
      attempt = attemptRepository.save(attempt);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw ApiException.conflict("STALE_VERSION", "Attempt has been modified since you loaded it");
    }

    resultRevisionRepository.save(
        new ResultRevision(
            attemptId,
            actorUserId,
            previousRaw,
            previousPenalty,
            previousStatus == ResultStatus.PENDING ? null : previousStatus,
            rawTimeMs,
            penalty,
            newStatus,
            body.note()));
    return attempt;
  }

  /** P04/P05/P07: a competitor's own attempts across the whole event, own credential only. */
  public List<Attempt> listOwnAttempts(UUID eventId, HttpServletRequest request) {
    EventEntrant entrant = guestAuthResolver.requireEntrantForEvent(request, eventId);
    return attemptRepository.findByEntrantId(entrant.getId());
  }

  public List<Attempt> listForRoundAuthorized(UUID roundId, UUID staffUserId) {
    Round round =
        roundRepository
            .findById(roundId)
            .orElseThrow(() -> ApiException.notFound("Round not found"));
    Event event = findEventOrNotFound(round.getEventId());
    tenantAccessService.requireJudgeOrOrganizer(event, staffUserId);
    return attemptRepository.findByRoundId(roundId);
  }

  public List<ResultRevision> history(UUID attemptId, UUID staffUserId) {
    Attempt attempt = findOrNotFound(attemptId);
    Event event = findEventOrNotFound(attempt.getEventId());
    tenantAccessService.requireJudgeOrOrganizer(event, staffUserId);
    return resultRevisionRepository.findByAttemptIdOrderByCreatedAtAsc(attemptId);
  }

  /**
   * Used by {@code CorrectionService} when a decision voids an attempt and, for {@code
   * ACCEPT_RETRY}, creates a replacement slot. The replacement has no scramble assignment (none
   * exist this milestone) and the same result source as the original.
   */
  @Transactional
  Attempt voidAttempt(Attempt attempt, UUID actorUserId, String reason) {
    Long previousRaw = attempt.getRawTimeMs();
    Penalty previousPenalty = attempt.getPenalty();
    ResultStatus previousStatus = attempt.getResultStatus();
    attempt.voidResult();
    attempt = attemptRepository.save(attempt);
    resultRevisionRepository.save(
        new ResultRevision(
            attempt.getId(),
            actorUserId,
            previousRaw,
            previousPenalty,
            previousStatus == ResultStatus.PENDING ? null : previousStatus,
            previousRaw,
            previousPenalty,
            ResultStatus.VOID,
            reason));
    return attempt;
  }

  @Transactional
  Attempt createReplacementAttempt(Attempt original) {
    int nextNumber =
        attemptRepository
                .findByRoundIdAndEntrantId(original.getRoundId(), original.getEntrantId())
                .stream()
                .mapToInt(Attempt::getAttemptNumber)
                .max()
                .orElse(original.getAttemptNumber())
            + 1;
    Attempt replacement =
        new Attempt(
            original.getEventId(),
            original.getRoundId(),
            original.getEntrantId(),
            nextNumber,
            original.getResultSource());
    return attemptRepository.save(replacement);
  }

  private boolean sameResult(
      Attempt attempt, Long rawTimeMs, Penalty penalty, ResultStatus status) {
    return java.util.Objects.equals(attempt.getRawTimeMs(), rawTimeMs)
        && attempt.getPenalty() == penalty
        && attempt.getResultStatus() == status;
  }

  private EventEntrant requireOwnEntrant(
      HttpServletRequest request, UUID eventId, Attempt attempt) {
    EventEntrant entrant = guestAuthResolver.requireEntrantForEvent(request, eventId);
    if (!entrant.getId().equals(attempt.getEntrantId())) {
      throw ApiException.notFound("Attempt not found");
    }
    return entrant;
  }

  private void requireSelfTimed(Attempt attempt) {
    if (attempt.getResultSource() != ResultSource.SELF_TIMED) {
      throw ApiException.forbidden("Physical timer mode does not expose this control");
    }
  }

  private void requireRoundLiveAndNotPaused(UUID roundId) {
    Round round =
        roundRepository
            .findById(roundId)
            .orElseThrow(() -> ApiException.notFound("Round not found"));
    if (round.getState() != RoundState.LIVE) {
      throw ApiException.invalidTransition("Round is not live");
    }
    if (round.isPaused()) {
      throw ApiException.conflict("ROUND_PAUSED", "The organizer has paused new attempt starts");
    }
  }

  private int roundFormatAttemptCount(UUID roundId) {
    return roundRepository
        .findById(roundId)
        .map(r -> r.getFormat().attemptCount())
        .orElse(Integer.MAX_VALUE);
  }

  private void requireRoundNotClosed(UUID roundId) {
    Round round =
        roundRepository
            .findById(roundId)
            .orElseThrow(() -> ApiException.notFound("Round not found"));
    if (round.getState() == RoundState.CLOSED) {
      throw ApiException.invalidTransition("Round is closed");
    }
  }

  private ResultStatus toResultStatus(SubmittedStatus status) {
    return switch (status) {
      case OK -> ResultStatus.OK;
      case DNF -> ResultStatus.DNF;
      case DNS -> ResultStatus.DNS;
    };
  }

  private Long validateRawTime(ResultStatus status, Long rawTimeMs) {
    if (status == ResultStatus.OK) {
      if (rawTimeMs == null || rawTimeMs < MIN_RAW_TIME_MS || rawTimeMs > MAX_RAW_TIME_MS) {
        throw ApiException.badRequest(
            "VALIDATION_ERROR",
            "rawTimeMs must be between 0.001 and 9999.999 seconds for OK results");
      }
      return rawTimeMs;
    }
    if (rawTimeMs != null) {
      throw ApiException.badRequest("VALIDATION_ERROR", "DNF/DNS must not carry a raw time");
    }
    return null;
  }

  private Penalty validatePenalty(ResultStatus status, Penalty penalty) {
    if (status != ResultStatus.OK) {
      if (penalty != null && penalty != Penalty.NONE) {
        throw ApiException.badRequest("VALIDATION_ERROR", "DNF/DNS must not carry a penalty");
      }
      return Penalty.NONE;
    }
    return penalty == null ? Penalty.NONE : penalty;
  }

  private Attempt findOrNotFound(UUID attemptId) {
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

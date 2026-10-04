package com.twistmeet.api.competition;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.AuditService;
import com.twistmeet.api.competition.AdvancementDtos.AdvancedEntrant;
import com.twistmeet.api.competition.AdvancementDtos.AdvancementCommitView;
import com.twistmeet.api.competition.AdvancementDtos.AdvancementPreviewView;
import com.twistmeet.api.competition.AdvancementDtos.CommitRequest;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.org.TenantAccessService;
import com.twistmeet.api.registration.EntrantStatus;
import com.twistmeet.api.registration.EventEntrant;
import com.twistmeet.api.registration.EventEntrantRepository;
import com.twistmeet.api.scoring.AdvancementCalculator;
import com.twistmeet.api.scoring.AttemptOutcome;
import com.twistmeet.api.scoring.RankedEntry;
import com.twistmeet.api.scoring.RankingService;
import com.twistmeet.api.scoring.RankingService.Scored;
import com.twistmeet.api.scoring.RoundResult;
import com.twistmeet.api.scoring.ScoredAttempt;
import com.twistmeet.api.scoring.ScoringEngine;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Advancement preview/commit (09 "Advancement"; 08 {@code /rounds/{roundId}/advancement/preview},
 * {@code .../commit}). Reuses the pure scoring engine ({@link ScoringEngine}, {@link
 * RankingService}, {@link AdvancementCalculator}) exactly as {@link StandingsService} does —
 * nothing here re-implements ranking or tie math.
 *
 * <p>Deferred by explicit decision (DECISIONS.md): the optional organizer-selected tie-break
 * *attempt* (09: "If organizer selected a tie-break attempt...") is not implemented — ties at the
 * boundary rank always advance together, which 09 itself names as the default behavior.
 */
@Service
public class AdvancementService {

  private final RoundRepository roundRepository;
  private final EventRepository eventRepository;
  private final AttemptRepository attemptRepository;
  private final EventEntrantRepository entrantRepository;
  private final RoundQualifiedEntrantRepository qualifiedEntrantRepository;
  private final TenantAccessService tenantAccessService;
  private final AuditService auditService;

  public AdvancementService(
      RoundRepository roundRepository,
      EventRepository eventRepository,
      AttemptRepository attemptRepository,
      EventEntrantRepository entrantRepository,
      RoundQualifiedEntrantRepository qualifiedEntrantRepository,
      TenantAccessService tenantAccessService,
      AuditService auditService) {
    this.roundRepository = roundRepository;
    this.eventRepository = eventRepository;
    this.attemptRepository = attemptRepository;
    this.entrantRepository = entrantRepository;
    this.qualifiedEntrantRepository = qualifiedEntrantRepository;
    this.tenantAccessService = tenantAccessService;
    this.auditService = auditService;
  }

  public AdvancementPreviewView preview(UUID roundId, UUID actorUserId) {
    Round round = findRoundOrNotFound(roundId);
    Event event = findEventOrNotFound(round.getEventId());
    tenantAccessService.requireOrganizer(event, actorUserId);
    return computePreview(round, event);
  }

  @Transactional
  public AdvancementCommitView commit(UUID roundId, UUID actorUserId, CommitRequest body) {
    Round round = findRoundOrNotFound(roundId);
    Event event = findEventOrNotFound(round.getEventId());
    tenantAccessService.requireOrganizer(event, actorUserId);

    if (round.getAdvancementCommittedAt() != null) {
      // Idempotent replay (08: "repeated writes are idempotent"): the round is frozen (REVIEW/
      // CLOSED, never re-opened), so recomputing deterministically reproduces the exact result
      // without touching round_qualified_entrants again.
      return AdvancementCommitView.of(computePreview(round, event), true);
    }

    if (round.getVersion() != body.expectedVersion()) {
      throw ApiException.conflict("STALE_VERSION", "Round has changed; reload and retry");
    }

    AdvancementPreviewView preview = computePreview(round, event);
    Round nextRound = requireNextRoundDraft(event.getId(), round);

    // Claim the commit via the round's own optimistic lock BEFORE writing any roster rows: the
    // first of two racing commits to flush wins this update (version V -> V+1); the second's
    // flush affects zero rows and throws immediately, so it never reaches the roster insert
    // loop below and can never produce a duplicate or partially-written roster.
    round.markAdvancementCommitted(actorUserId, preview.advancing().size());
    try {
      round = roundRepository.saveAndFlush(round);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw ApiException.conflict("STALE_VERSION", "Round has changed; reload and retry");
    }

    for (AdvancedEntrant advanced : preview.advancing()) {
      var key = new RoundQualifiedEntrant.Key(nextRound.getId(), advanced.entrantId());
      if (!qualifiedEntrantRepository.existsById(key)) {
        qualifiedEntrantRepository.save(
            new RoundQualifiedEntrant(nextRound.getId(), advanced.entrantId()));
      }
    }

    auditService.recordStaffAction(
        event.getOrganizationId(),
        event.getId(),
        actorUserId,
        "ADVANCEMENT_COMMITTED",
        "Round",
        roundId.toString(),
        "advanced "
            + preview.advancing().size()
            + " of "
            + preview.eligibleCount()
            + " to "
            + nextRound.getName());
    return AdvancementCommitView.of(preview, false);
  }

  private AdvancementPreviewView computePreview(Round round, Event event) {
    if (round.getState() != RoundState.REVIEW && round.getState() != RoundState.CLOSED) {
      throw ApiException.invalidTransition(
          "Round must be in REVIEW or CLOSED before advancement can be previewed or committed");
    }

    List<Attempt> attempts = attemptRepository.findByRoundId(round.getId());
    Map<UUID, EventEntrant> entrantsById =
        entrantRepository.findByEventId(event.getId()).stream()
            .collect(Collectors.toMap(EventEntrant::getId, e -> e));
    Map<UUID, List<Attempt>> byEntrant =
        attempts.stream().collect(Collectors.groupingBy(Attempt::getEntrantId));

    // 09 eligibility: "not withdrawn and has at least one result status." Withdrawn entrants are
    // filtered here; an entrant with zero resolved attempts naturally scores RoundOutcome.
    // NO_RESULT, which RankingService.rank already excludes from the ranked list below.
    List<Scored<UUID>> scored =
        byEntrant.entrySet().stream()
            .filter(
                e -> {
                  EventEntrant entrant = entrantsById.get(e.getKey());
                  return entrant != null && entrant.getStatus() == EntrantStatus.ACTIVE;
                })
            .map(e -> new Scored<>(e.getKey(), scoreEntrant(round, e.getValue())))
            .toList();
    List<RankedEntry<UUID>> ranked = RankingService.rank(scored);
    int eligibleCount = ranked.size();

    Set<UUID> advancingIds;
    int targetCount;
    AdvancementRule rule = round.getAdvancementRule();
    if (rule == AdvancementRule.EVERYONE) {
      advancingIds = ranked.stream().map(RankedEntry::id).collect(Collectors.toSet());
      targetCount = eligibleCount;
    } else if (rule == AdvancementRule.TOP_N) {
      int n = round.getAdvancementValue() == null ? 0 : round.getAdvancementValue();
      targetCount = n;
      advancingIds = eligibleCount == 0 || n < 1 ? Set.of() : AdvancementCalculator.topN(ranked, n);
    } else {
      double percentage = round.getAdvancementValue() == null ? 0 : round.getAdvancementValue();
      if (eligibleCount == 0) {
        advancingIds = Set.of();
        targetCount = 0;
      } else {
        var result = AdvancementCalculator.topPercent(ranked, eligibleCount, percentage);
        advancingIds = result.advancing();
        targetCount = result.targetCount();
      }
    }

    List<AdvancedEntrant> advancing =
        ranked.stream()
            .filter(r -> advancingIds.contains(r.id()))
            .map(
                r ->
                    new AdvancedEntrant(
                        r.id(), entrantsById.get(r.id()).getDisplayName(), r.rank()))
            .sorted((a, b) -> Integer.compare(a.rank(), b.rank()))
            .toList();

    String tieNote = buildTieNote(ranked, advancing.size(), targetCount);
    Round nextRound = findNextRound(event.getId(), round.getOrder()).orElse(null);

    return new AdvancementPreviewView(
        round.getId(),
        nextRound == null ? null : nextRound.getId(),
        rule.name(),
        round.getAdvancementValue(),
        eligibleCount,
        targetCount,
        advancing,
        tieNote,
        round.getAdvancementCommittedAt() != null);
  }

  private String buildTieNote(List<RankedEntry<UUID>> ranked, int advancingCount, int targetCount) {
    if (advancingCount <= targetCount || ranked.isEmpty()) {
      return "No boundary tie; " + advancingCount + " entrant(s) advance.";
    }
    int boundaryRank =
        ranked.stream()
            .filter(r -> r.rank() <= advancingCount)
            .mapToInt(RankedEntry::rank)
            .max()
            .orElse(0);
    long tiedAtBoundary = ranked.stream().filter(r -> r.rank() == boundaryRank).count();
    return "Rank "
        + boundaryRank
        + " is tied among "
        + tiedAtBoundary
        + " entrant(s); all are included, so "
        + advancingCount
        + " advance instead of "
        + targetCount
        + ".";
  }

  private Round requireNextRoundDraft(UUID eventId, Round current) {
    Round next =
        findNextRound(eventId, current.getOrder())
            .orElseThrow(
                () ->
                    ApiException.badRequest(
                        "NO_NEXT_ROUND", "Create the next round before committing advancement"));
    if (next.getState() != RoundState.DRAFT) {
      throw ApiException.invalidTransition(
          "Next round has already been prepared; cannot change its qualified roster now");
    }
    return next;
  }

  private java.util.Optional<Round> findNextRound(UUID eventId, int currentOrder) {
    return roundRepository.findByEventIdOrderByOrder(eventId).stream()
        .filter(r -> r.getOrder() == currentOrder + 1)
        .findFirst();
  }

  private RoundResult scoreEntrant(Round round, List<Attempt> attempts) {
    List<ScoredAttempt> resolved =
        attempts.stream()
            .filter(
                a ->
                    a.getResultStatus() != ResultStatus.PENDING
                        && a.getResultStatus() != ResultStatus.VOID)
            .map(
                a ->
                    switch (a.getResultStatus()) {
                      case OK ->
                          new ScoredAttempt(
                              a.getAttemptNumber(), AttemptOutcome.OK, a.getAdjustedTimeMs());
                      case DNF -> ScoredAttempt.dnf(a.getAttemptNumber());
                      case DNS -> ScoredAttempt.dns(a.getAttemptNumber());
                      case PENDING, VOID -> throw new IllegalStateException("filtered above");
                    })
            .toList();
    return ScoringEngine.score(round.getRulesetVersion(), round.getFormat(), resolved);
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

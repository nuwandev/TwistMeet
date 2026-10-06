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
import com.twistmeet.api.stream.EventStreamService;
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
 * <p>09: "If organizer selected a tie-break attempt, show the tied names and create a special
 * one-attempt attempt with an unused scramble; winner policy is the lower valid time, DNF last,
 * then shared advancement if still tied. This option must be selected before registration opens."
 * Implemented via {@link #createTieBreakAttempts}: a boundary tie under {@link
 * TiePolicy#TIE_BREAK_ATTEMPT} withholds the tied entrants from {@code advancing} (flagging {@code
 * tieBreakRequired}) until that endpoint creates one extra judged attempt per tied entrant; once
 * all of those are resolved, {@link #computePreview} picks the winner(s) by lower valid time (DNF
 * ranked last; identical times or all-DNF falls back to shared advancement, exactly as specified).
 * The default ({@link TiePolicy#SHARED_RANK}) is unchanged: boundary ties always advance together.
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
  private final com.twistmeet.api.scramble.ScrambleService scrambleService;
  private final EventStreamService eventStreamService;

  public AdvancementService(
      RoundRepository roundRepository,
      EventRepository eventRepository,
      AttemptRepository attemptRepository,
      EventEntrantRepository entrantRepository,
      RoundQualifiedEntrantRepository qualifiedEntrantRepository,
      TenantAccessService tenantAccessService,
      AuditService auditService,
      com.twistmeet.api.scramble.ScrambleService scrambleService,
      EventStreamService eventStreamService) {
    this.roundRepository = roundRepository;
    this.eventRepository = eventRepository;
    this.attemptRepository = attemptRepository;
    this.entrantRepository = entrantRepository;
    this.qualifiedEntrantRepository = qualifiedEntrantRepository;
    this.tenantAccessService = tenantAccessService;
    this.auditService = auditService;
    this.scrambleService = scrambleService;
    this.eventStreamService = eventStreamService;
  }

  /**
   * Creates one extra, judged attempt (attempt number = format count + 1) for every entrant
   * currently tied at the advancement boundary rank, assigning an unused scramble if a batch exists
   * for this round. Idempotent per entrant: re-calling before any result is recorded is a no-op for
   * entrants who already have one.
   */
  @Transactional
  public List<AdvancedEntrant> createTieBreakAttempts(UUID roundId, UUID actorUserId) {
    Round round = findRoundOrNotFound(roundId);
    Event event = findEventOrNotFound(round.getEventId());
    tenantAccessService.requireOrganizer(event, actorUserId);
    if (round.getTiePolicy() != TiePolicy.TIE_BREAK_ATTEMPT) {
      throw ApiException.invalidTransition(
          "This round was not configured with the tie-break-attempt option");
    }
    AdvancementPreviewView preview = computePreview(round, event);
    if (preview.tiedPendingResolution().isEmpty()) {
      throw ApiException.invalidTransition("No boundary tie is currently pending resolution");
    }
    int tieBreakAttemptNumber = tieBreakAttemptNumber(round);
    for (AdvancedEntrant tied : preview.tiedPendingResolution()) {
      boolean exists =
          attemptRepository.findByRoundIdAndEntrantId(roundId, tied.entrantId()).stream()
              .anyMatch(a -> a.getAttemptNumber() == tieBreakAttemptNumber);
      if (exists) {
        continue;
      }
      com.twistmeet.api.competition.ResultSource resultSource =
          event.getTimerMode() == com.twistmeet.api.event.TimerMode.PHYSICAL_JUDGE
              ? ResultSource.JUDGE
              : ResultSource.SELF_TIMED;
      Attempt tieBreakAttempt =
          attemptRepository.save(
              new Attempt(
                  event.getId(), roundId, tied.entrantId(), tieBreakAttemptNumber, resultSource));
      scrambleService.assignScrambleIfBatchExists(tieBreakAttempt);
    }
    auditService.recordStaffAction(
        event.getOrganizationId(),
        event.getId(),
        actorUserId,
        "TIE_BREAK_ATTEMPTS_CREATED",
        "Round",
        roundId.toString(),
        "tie-break among " + preview.tiedPendingResolution().size() + " entrant(s)");
    eventStreamService.publish(
        event.getId(), "TIE_BREAK_ATTEMPTS_CREATED", roundId, Set.of("tiedPendingResolution"));
    return preview.tiedPendingResolution();
  }

  private int tieBreakAttemptNumber(Round round) {
    return round.getFormat().attemptCount() + 1;
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
    if (preview.tieBreakRequired()) {
      throw ApiException.invalidTransition(
          "A tie-break attempt must be created and resolved before committing (see"
              + " /advancement/tie-break)");
    }
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
    eventStreamService.publish(
        event.getId(), "ADVANCEMENT_COMMITTED", roundId, Set.of("advancementCommittedAt"));
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
    // A tie-break attempt (attemptNumber beyond the round's own format count) must never be fed
    // into the main scoring/ranking — it would silently replace or augment an entrant's real
    // result. byEntrant (unfiltered) is kept separately below, purely for tie-break lookup.
    Map<UUID, List<Attempt>> byEntrant =
        attempts.stream().collect(Collectors.groupingBy(Attempt::getEntrantId));
    int formatAttemptCount = round.getFormat().attemptCount();
    Map<UUID, List<Attempt>> mainAttemptsByEntrant =
        attempts.stream()
            .filter(a -> a.getAttemptNumber() <= formatAttemptCount)
            .collect(Collectors.groupingBy(Attempt::getEntrantId));

    // 09 eligibility: "not withdrawn and has at least one result status." Withdrawn entrants are
    // filtered here; an entrant with zero resolved attempts naturally scores RoundOutcome.
    // NO_RESULT, which RankingService.rank already excludes from the ranked list below.
    List<Scored<UUID>> scored =
        mainAttemptsByEntrant.entrySet().stream()
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

    List<AdvancedEntrant> naiveAdvancing =
        ranked.stream()
            .filter(r -> advancingIds.contains(r.id()))
            .map(
                r ->
                    new AdvancedEntrant(
                        r.id(), entrantsById.get(r.id()).getDisplayName(), r.rank()))
            .sorted((a, b) -> Integer.compare(a.rank(), b.rank()))
            .toList();

    List<AdvancedEntrant> advancing = naiveAdvancing;
    List<AdvancedEntrant> tiedPendingResolution = List.of();
    boolean tieBreakRequired = false;

    boolean boundaryTie = rule != AdvancementRule.EVERYONE && naiveAdvancing.size() > targetCount;
    if (boundaryTie && round.getTiePolicy() == TiePolicy.TIE_BREAK_ATTEMPT) {
      int boundaryRank = naiveAdvancing.stream().mapToInt(AdvancedEntrant::rank).max().orElse(0);
      List<AdvancedEntrant> tied =
          naiveAdvancing.stream().filter(a -> a.rank() == boundaryRank).toList();
      List<AdvancedEntrant> aboveBoundary =
          naiveAdvancing.stream().filter(a -> a.rank() < boundaryRank).toList();
      int tieBreakAttemptNumber = tieBreakAttemptNumber(round);

      Map<UUID, Attempt> tieBreakAttemptsByEntrant =
          tied.stream()
              .map(
                  a ->
                      byEntrant.getOrDefault(a.entrantId(), List.of()).stream()
                          .filter(att -> att.getAttemptNumber() == tieBreakAttemptNumber)
                          .findFirst()
                          .map(att -> Map.entry(a.entrantId(), att)))
              .filter(java.util.Optional::isPresent)
              .map(java.util.Optional::get)
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

      boolean allTieBreaksResolved =
          tied.size() == tieBreakAttemptsByEntrant.size()
              && tieBreakAttemptsByEntrant.values().stream()
                  .allMatch(att -> att.getResultStatus() != ResultStatus.PENDING);

      if (!allTieBreaksResolved) {
        tieBreakRequired = true;
        tiedPendingResolution = tied;
        advancing = aboveBoundary;
      } else {
        // Winner policy: lower valid (OK) time wins; DNF/DNS rank last; identical times (or
        // every tied entrant being DNF/DNS, i.e. nobody has a comparable valid time) falls back
        // to shared advancement — exactly 09's "then shared advancement if still tied."
        Long bestTime = null;
        for (Attempt att : tieBreakAttemptsByEntrant.values()) {
          if (att.getResultStatus() == ResultStatus.OK && att.getAdjustedTimeMs() != null) {
            bestTime =
                bestTime == null
                    ? att.getAdjustedTimeMs()
                    : Math.min(bestTime, att.getAdjustedTimeMs());
          }
        }
        List<AdvancedEntrant> winners;
        if (bestTime == null) {
          winners = tied; // nobody posted a valid time -> shared advancement
        } else {
          Long finalBestTime = bestTime;
          winners =
              tied.stream()
                  .filter(
                      a -> {
                        Attempt att = tieBreakAttemptsByEntrant.get(a.entrantId());
                        return att.getResultStatus() == ResultStatus.OK
                            && finalBestTime.equals(att.getAdjustedTimeMs());
                      })
                  .toList();
        }
        advancing =
            java.util.stream.Stream.concat(aboveBoundary.stream(), winners.stream())
                .sorted((a, b) -> Integer.compare(a.rank(), b.rank()))
                .toList();
      }
    }

    String tieNote =
        tieBreakRequired
            ? "Rank "
                + tiedPendingResolution.get(0).rank()
                + " is tied among "
                + tiedPendingResolution.size()
                + " entrant(s); a tie-break attempt is required before committing."
            : buildTieNote(ranked, naiveAdvancing.size(), targetCount);
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
        round.getAdvancementCommittedAt() != null,
        tieBreakRequired,
        tiedPendingResolution);
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

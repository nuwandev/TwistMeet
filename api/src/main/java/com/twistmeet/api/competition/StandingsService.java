package com.twistmeet.api.competition;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.competition.StandingsDtos.EntrantStanding;
import com.twistmeet.api.competition.StandingsDtos.StandingsView;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.org.TenantAccessService;
import com.twistmeet.api.registration.EventEntrant;
import com.twistmeet.api.registration.EventEntrantRepository;
import com.twistmeet.api.scoring.AttemptOutcome;
import com.twistmeet.api.scoring.RankedEntry;
import com.twistmeet.api.scoring.RankingService;
import com.twistmeet.api.scoring.RankingService.Scored;
import com.twistmeet.api.scoring.RoundResult;
import com.twistmeet.api.scoring.ScoredAttempt;
import com.twistmeet.api.scoring.ScoringEngine;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Authorized (staff-only) standings for one round, computed live from {@link Attempt} rows via the
 * pure {@code scoring} module (09). Published/public standings are a later milestone (see
 * DECISIONS.md) — this is the organizer/judge-facing view only.
 */
@Service
public class StandingsService {

  private final RoundRepository roundRepository;
  private final AttemptRepository attemptRepository;
  private final EventEntrantRepository entrantRepository;
  private final EventRepository eventRepository;
  private final TenantAccessService tenantAccessService;

  public StandingsService(
      RoundRepository roundRepository,
      AttemptRepository attemptRepository,
      EventEntrantRepository entrantRepository,
      EventRepository eventRepository,
      TenantAccessService tenantAccessService) {
    this.roundRepository = roundRepository;
    this.attemptRepository = attemptRepository;
    this.entrantRepository = entrantRepository;
    this.eventRepository = eventRepository;
    this.tenantAccessService = tenantAccessService;
  }

  public StandingsView compute(UUID roundId, UUID staffUserId) {
    Round round =
        roundRepository
            .findById(roundId)
            .orElseThrow(() -> ApiException.notFound("Round not found"));
    Event event =
        eventRepository
            .findById(round.getEventId())
            .orElseThrow(() -> ApiException.notFound("Event not found"));
    tenantAccessService.requireJudgeOrOrganizer(event, staffUserId);

    List<Attempt> attempts = attemptRepository.findByRoundId(roundId);
    Map<UUID, List<Attempt>> byEntrant =
        attempts.stream().collect(Collectors.groupingBy(Attempt::getEntrantId));
    Map<UUID, EventEntrant> entrantsById =
        entrantRepository.findByEventId(event.getId()).stream()
            .collect(Collectors.toMap(EventEntrant::getId, e -> e));

    List<Scored<UUID>> scored =
        byEntrant.entrySet().stream()
            .map(
                e -> {
                  RoundResult result = scoreEntrant(round, e.getValue());
                  return new Scored<>(e.getKey(), result);
                })
            .toList();
    List<RankedEntry<UUID>> ranked = RankingService.rank(scored);

    List<EntrantStanding> standings =
        ranked.stream()
            .map(
                r -> {
                  EventEntrant entrant = entrantsById.get(r.id());
                  RoundResult result = r.result();
                  return new EntrantStanding(
                      r.id(),
                      entrant == null ? "" : entrant.getDisplayName(),
                      r.rank(),
                      result.outcome(),
                      result.displayMs(),
                      result.bestValidSingleMs(),
                      result.discardedAttemptNumbers());
                })
            .sorted(
                (a, b) -> {
                  if (a.rank() != b.rank()) {
                    return Integer.compare(a.rank(), b.rank());
                  }
                  return a.displayName().compareToIgnoreCase(b.displayName());
                })
            .toList();

    boolean provisional = round.getState() != RoundState.CLOSED;
    return new StandingsView(roundId, provisional, round.getRulesetVersion().toString(), standings);
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
}

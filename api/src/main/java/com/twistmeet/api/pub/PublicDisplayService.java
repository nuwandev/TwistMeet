package com.twistmeet.api.pub;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.competition.Attempt;
import com.twistmeet.api.competition.AttemptRepository;
import com.twistmeet.api.competition.ResultStatus;
import com.twistmeet.api.competition.Round;
import com.twistmeet.api.competition.RoundRepository;
import com.twistmeet.api.competition.RoundState;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.pub.PublicDtos.PublicEntrantStanding;
import com.twistmeet.api.pub.PublicDtos.PublicEventView;
import com.twistmeet.api.pub.PublicDtos.PublicRoundSummary;
import com.twistmeet.api.pub.PublicDtos.PublicStandingsView;
import com.twistmeet.api.registration.EntrantStatus;
import com.twistmeet.api.registration.EventEntrant;
import com.twistmeet.api.registration.EventEntrantRepository;
import com.twistmeet.api.scoring.AttemptOutcome;
import com.twistmeet.api.scoring.RankedEntry;
import com.twistmeet.api.scoring.RankingService;
import com.twistmeet.api.scoring.RankingService.Scored;
import com.twistmeet.api.scoring.RoundResult;
import com.twistmeet.api.scoring.ScoredAttempt;
import com.twistmeet.api.scoring.ScoringEngine;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Unauthenticated public event/standings display (08 {@code /public/events/{publicSlug}}; 07 S13).
 * Only reachable once an organizer has published the event ({@link Event#getPublishedAt()}
 * non-null) and only shows rounds that have actually started being run ({@link
 * #VISIBLE_ROUND_STATES}) — a round still in DRAFT/PREPARING has nothing public to show and must
 * never leak its existence or roster. Withdrawn entrants are excluded entirely.
 */
@Service
public class PublicDisplayService {

  private static final Set<RoundState> VISIBLE_ROUND_STATES =
      EnumSet.of(RoundState.READY, RoundState.LIVE, RoundState.REVIEW, RoundState.CLOSED);

  private final EventRepository eventRepository;
  private final RoundRepository roundRepository;
  private final AttemptRepository attemptRepository;
  private final EventEntrantRepository entrantRepository;

  public PublicDisplayService(
      EventRepository eventRepository,
      RoundRepository roundRepository,
      AttemptRepository attemptRepository,
      EventEntrantRepository entrantRepository) {
    this.eventRepository = eventRepository;
    this.roundRepository = roundRepository;
    this.attemptRepository = attemptRepository;
    this.entrantRepository = entrantRepository;
  }

  public PublicEventView getEvent(String publicSlug) {
    Event event = requirePublishedEvent(publicSlug);
    List<PublicRoundSummary> rounds =
        roundRepository.findByEventIdOrderByOrder(event.getId()).stream()
            .filter(r -> VISIBLE_ROUND_STATES.contains(r.getState()))
            .map(
                r ->
                    new PublicRoundSummary(
                        r.getId(), r.getOrder(), r.getName(), r.getState().name()))
            .toList();
    return new PublicEventView(
        event.getId(),
        event.getName(),
        event.getVenueLabel(),
        event.getStartsAt(),
        event.getTimezone(),
        rounds);
  }

  public PublicStandingsView getStandings(String publicSlug, UUID roundId) {
    Event event = requirePublishedEvent(publicSlug);
    Round round =
        roundRepository
            .findByIdAndEventId(roundId, event.getId())
            .filter(r -> VISIBLE_ROUND_STATES.contains(r.getState()))
            .orElseThrow(() -> ApiException.notFound("Round not found"));

    List<Attempt> attempts = attemptRepository.findByRoundId(roundId);
    Map<UUID, EventEntrant> activeEntrants =
        entrantRepository.findByEventId(event.getId()).stream()
            .filter(e -> e.getStatus() == EntrantStatus.ACTIVE)
            .collect(Collectors.toMap(EventEntrant::getId, e -> e));
    Map<UUID, List<Attempt>> byEntrant =
        attempts.stream()
            .filter(a -> activeEntrants.containsKey(a.getEntrantId()))
            .collect(Collectors.groupingBy(Attempt::getEntrantId));

    List<Scored<UUID>> scored =
        byEntrant.entrySet().stream()
            .map(e -> new Scored<>(e.getKey(), scoreEntrant(round, e.getValue())))
            .toList();
    List<RankedEntry<UUID>> ranked = RankingService.rank(scored);

    List<PublicEntrantStanding> standings =
        ranked.stream()
            .map(
                r -> {
                  EventEntrant entrant = activeEntrants.get(r.id());
                  List<Attempt> entrantAttempts = byEntrant.get(r.id());
                  int completed =
                      (int)
                          entrantAttempts.stream()
                              .filter(a -> a.getResultStatus() != ResultStatus.PENDING)
                              .count();
                  RoundResult result = r.result();
                  String displayName =
                      event.isPublicNameMask()
                          ? "Competitor " + r.rank()
                          : entrant.getDisplayName();
                  return new PublicEntrantStanding(
                      displayName,
                      r.rank(),
                      result.outcome().name(),
                      result.displayMs(),
                      result.bestValidSingleMs(),
                      completed,
                      entrantAttempts.size());
                })
            .sorted((a, b) -> Integer.compare(a.rank(), b.rank()))
            .toList();

    boolean provisional = round.getState() != RoundState.CLOSED;
    return new PublicStandingsView(roundId, provisional, standings);
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

  private Event requirePublishedEvent(String publicSlug) {
    Event event =
        eventRepository
            .findByPublicSlug(publicSlug)
            .orElseThrow(() -> ApiException.notFound("Not found"));
    if (event.getPublishedAt() == null) {
      throw ApiException.notFound("Not found");
    }
    return event;
  }
}

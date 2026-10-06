package com.twistmeet.api.history;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.AuditService;
import com.twistmeet.api.competition.Attempt;
import com.twistmeet.api.competition.AttemptRepository;
import com.twistmeet.api.competition.ResultStatus;
import com.twistmeet.api.competition.Round;
import com.twistmeet.api.competition.RoundRepository;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.event.EventState;
import com.twistmeet.api.history.HistoryDtos.EventHistorySummary;
import com.twistmeet.api.org.TenantAccessService;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Organization event history (07 S14: "Search/filter organization events by date/name/status...
 * download CSV with documented columns and UTF-8") and CSV export (08 {@code
 * /organizations/{orgId}/events/{eventId}/export.csv}).
 *
 * <p>Deferred by explicit decision (DECISIONS.md, not silently invented): "copy event settings"
 * (duplicate-as-template) and retention-policy deletion from S14 are not part of this list — the M5
 * task description's own acceptance bullets name only history and CSV export.
 */
@Service
public class HistoryService {

  private final EventRepository eventRepository;
  private final RoundRepository roundRepository;
  private final AttemptRepository attemptRepository;
  private final EventEntrantRepository entrantRepository;
  private final TenantAccessService tenantAccessService;
  private final AuditService auditService;

  public HistoryService(
      EventRepository eventRepository,
      RoundRepository roundRepository,
      AttemptRepository attemptRepository,
      EventEntrantRepository entrantRepository,
      TenantAccessService tenantAccessService,
      AuditService auditService) {
    this.eventRepository = eventRepository;
    this.roundRepository = roundRepository;
    this.attemptRepository = attemptRepository;
    this.entrantRepository = entrantRepository;
    this.tenantAccessService = tenantAccessService;
    this.auditService = auditService;
  }

  public List<EventHistorySummary> list(
      UUID organizationId,
      UUID actorUserId,
      EventState state,
      Instant from,
      Instant to,
      String nameContains) {
    tenantAccessService.requireAnyStaffRole(organizationId, actorUserId);
    String needle = nameContains == null ? null : nameContains.toLowerCase();
    return eventRepository.findByOrganizationId(organizationId).stream()
        .filter(e -> state == null || e.getState() == state)
        .filter(e -> from == null || !e.getStartsAt().isBefore(from))
        .filter(e -> to == null || !e.getStartsAt().isAfter(to))
        .filter(e -> needle == null || e.getName().toLowerCase().contains(needle))
        .sorted((a, b) -> b.getStartsAt().compareTo(a.getStartsAt()))
        .map(
            e ->
                new EventHistorySummary(
                    e.getId(),
                    e.getName(),
                    e.getState().name(),
                    e.getStartsAt(),
                    entrantRepository.findByEventId(e.getId()).size(),
                    roundRepository.findByEventIdOrderByOrder(e.getId()).size()))
        .toList();
  }

  public String exportCsv(UUID organizationId, UUID eventId, UUID actorUserId) {
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> ApiException.notFound("Event not found"));
    if (!event.getOrganizationId().equals(organizationId)) {
      throw ApiException.notFound("Event not found");
    }
    tenantAccessService.requireOrganizer(event, actorUserId);

    Map<UUID, EventEntrant> entrantsById =
        entrantRepository.findByEventId(eventId).stream()
            .collect(Collectors.toMap(EventEntrant::getId, e -> e));

    List<List<String>> rows = new ArrayList<>();
    for (Round round : roundRepository.findByEventIdOrderByOrder(eventId)) {
      List<Attempt> attempts = attemptRepository.findByRoundId(round.getId());
      Map<UUID, List<Attempt>> byEntrant =
          attempts.stream().collect(Collectors.groupingBy(Attempt::getEntrantId));

      List<Scored<UUID>> scored =
          byEntrant.entrySet().stream()
              .filter(
                  en -> {
                    EventEntrant entrant = entrantsById.get(en.getKey());
                    return entrant != null && entrant.getStatus() == EntrantStatus.ACTIVE;
                  })
              .map(en -> new Scored<>(en.getKey(), scoreEntrant(round, en.getValue())))
              .toList();
      List<RankedEntry<UUID>> ranked = RankingService.rank(scored);

      for (RankedEntry<UUID> r : ranked) {
        EventEntrant entrant = entrantsById.get(r.id());
        RoundResult result = r.result();
        List<Attempt> entrantAttempts =
            byEntrant.get(r.id()).stream()
                .sorted((a, b) -> Integer.compare(a.getAttemptNumber(), b.getAttemptNumber()))
                .toList();
        String rawList =
            entrantAttempts.stream()
                .map(
                    a ->
                        a.getAdjustedTimeMs() == null
                            ? a.getResultStatus().name()
                            : a.getAdjustedTimeMs().toString())
                .collect(Collectors.joining(";"));
        String formattedList =
            entrantAttempts.stream()
                .map(
                    a ->
                        a.getAdjustedTimeMs() == null
                            ? a.getResultStatus().name()
                            : formatMs(a.getAdjustedTimeMs()))
                .collect(Collectors.joining(";"));
        rows.add(
            List.of(
                round.getName(),
                Integer.toString(r.rank()),
                entrant.getDisplayName(),
                result.outcome().name(),
                result.displayMs() == null ? "" : result.displayMs().toString(),
                result.displayMs() == null ? "" : formatMs(result.displayMs()),
                result.bestValidSingleMs() == null ? "" : result.bestValidSingleMs().toString(),
                result.bestValidSingleMs() == null ? "" : formatMs(result.bestValidSingleMs()),
                rawList,
                formattedList));
      }
    }

    auditService.recordStaffAction(
        organizationId, eventId, actorUserId, "EVENT_EXPORTED", "Event", eventId.toString(), null);

    return CsvWriter.write(
        List.of(
            "Round",
            "Rank",
            "Entrant",
            "Outcome",
            "ResultMs",
            "ResultFormatted",
            "BestSingleMs",
            "BestSingleFormatted",
            "AttemptsRawMs",
            "AttemptsFormatted"),
        rows);
  }

  /**
   * 09: "Display default two decimals with round-half-up... Do not use language-default
   * floating-point rounding."
   */
  private String formatMs(long ms) {
    return BigDecimal.valueOf(ms)
        .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP)
        .toPlainString();
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

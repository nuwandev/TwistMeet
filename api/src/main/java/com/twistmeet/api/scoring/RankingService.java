package com.twistmeet.api.scoring;

import java.util.ArrayList;
import java.util.List;

/**
 * Ranks a round's already-scored entrants (09 "Ranking"). Within a round, valid results rank before
 * DNF; primary key is the exact format result ascending; tie-break 1 is best valid single
 * ascending; if still identical, shared rank using competition ranking (1, 2, 2, 4). Entrants with
 * {@link RoundOutcome#NO_RESULT} (no resolved attempts at all) are not ranked.
 */
public final class RankingService {

  private RankingService() {}

  public record Scored<T>(T id, RoundResult result) {}

  public static <T> List<RankedEntry<T>> rank(List<Scored<T>> entries) {
    List<Scored<T>> ranked =
        entries.stream().filter(e -> e.result().outcome() != RoundOutcome.NO_RESULT).toList();
    List<Scored<T>> sorted = new ArrayList<>(ranked);
    sorted.sort(RankingService::compare);

    List<RankedEntry<T>> out = new ArrayList<>();
    int rank = 0;
    for (int i = 0; i < sorted.size(); i++) {
      if (i == 0 || compare(sorted.get(i - 1), sorted.get(i)) != 0) {
        rank = i + 1;
      }
      out.add(new RankedEntry<>(sorted.get(i).id(), rank, sorted.get(i).result()));
    }
    return out;
  }

  private static <T> int compare(Scored<T> a, Scored<T> b) {
    RoundResult ra = a.result();
    RoundResult rb = b.result();
    int outcomeCompare = Integer.compare(outcomeOrder(ra.outcome()), outcomeOrder(rb.outcome()));
    if (outcomeCompare != 0) {
      return outcomeCompare;
    }
    if (ra.outcome() == RoundOutcome.OK) {
      int keyCompare = ra.comparisonKey().compareTo(rb.comparisonKey());
      if (keyCompare != 0) {
        return keyCompare;
      }
    }
    // Both OK-and-tied-on-primary-key, or both DNF (which have no comparable primary key):
    // tie-break on best valid single.
    return compareBestSingle(ra.bestValidSingleMs(), rb.bestValidSingleMs());
  }

  private static int outcomeOrder(RoundOutcome outcome) {
    return outcome == RoundOutcome.OK ? 0 : 1;
  }

  private static int compareBestSingle(Long a, Long b) {
    if (a == null && b == null) {
      return 0;
    }
    if (a == null) {
      return 1;
    }
    if (b == null) {
      return -1;
    }
    return Long.compare(a, b);
  }
}

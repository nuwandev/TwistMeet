package com.twistmeet.api.scoring;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure advancement-boundary math (09 "Advancement"): which ranks advance under {@code TOP_N} or
 * {@code TOP_PERCENT}, always including every entrant tied at the boundary rank.
 *
 * <p>This is deliberately just the calculation, not a feature: the M2 scope for this build excludes
 * the advancement preview/commit workflow, round-to-round entrant creation and its API (deferred to
 * a later milestone — see DECISIONS.md). The calculation itself is part of the scoring engine's
 * required conformance coverage (09 vectors V11/V12) and is kept here, tested, but not wired into
 * any controller or persisted state.
 */
public final class AdvancementCalculator {

  private AdvancementCalculator() {}

  /** Returns the ids of every entrant at rank &le; n, including all entrants tied at rank n. */
  public static <T> Set<T> topN(List<RankedEntry<T>> ranked, int n) {
    if (n < 1) {
      throw new IllegalArgumentException("n must be at least 1");
    }
    return ranked.stream()
        .filter(e -> e.rank() <= n)
        .map(RankedEntry::id)
        .collect(Collectors.toSet());
  }

  public record PercentResult<T>(int targetCount, Set<T> advancing) {}

  /**
   * {@code target = ceil(eligibleCount * percentage / 100)}; then include everyone tied at the
   * target rank (09: "compute target... then include all tied at the target rank").
   */
  public static <T> PercentResult<T> topPercent(
      List<RankedEntry<T>> ranked, int eligibleCount, double percentage) {
    if (eligibleCount < 0) {
      throw new IllegalArgumentException("eligibleCount must not be negative");
    }
    int target = (int) Math.ceil(eligibleCount * percentage / 100.0);
    if (target < 1) {
      target = 1;
    }
    Set<T> advancing = topN(ranked, target);
    return new PercentResult<>(target, advancing);
  }
}

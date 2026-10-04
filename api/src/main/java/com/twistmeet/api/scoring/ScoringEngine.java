package com.twistmeet.api.scoring;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure, deterministic round scoring (09-scoring-conformance.md). No UI or persistence dependency:
 * every method here is a function of its arguments only, so the same inputs always produce
 * byte-equivalent output (09 property: "Same inputs + same ruleset version always produce
 * byte-equivalent derived values").
 */
public final class ScoringEngine {

  private ScoringEngine() {}

  /**
   * Scores one entrant's attempts in one round under the given ruleset/format. {@code attempts}
   * need not be sorted and need not number exactly {@code format.attemptCount()} entries — a round
   * in progress may have fewer resolved attempts than the format's nominal count (the caller
   * decides whether to treat the result as provisional), and a voided-without-replacement attempt
   * simply means one fewer input than nominal. There is currently only one ruleset version; the
   * parameter exists so a future rule change cannot silently alter V1's behavior.
   */
  public static RoundResult score(
      RulesetVersion ruleset, RoundFormat format, List<ScoredAttempt> attempts) {
    if (ruleset != RulesetVersion.V1) {
      throw new IllegalArgumentException("Unsupported ruleset version: " + ruleset);
    }
    if (attempts.isEmpty()) {
      return RoundResult.noResult();
    }

    Long bestValidSingleMs =
        attempts.stream()
            .filter(a -> a.outcome() == AttemptOutcome.OK)
            .map(ScoredAttempt::adjustedTimeMs)
            .min(Long::compareTo)
            .orElse(null);

    if (format.isBestOf()) {
      return scoreBestOf(attempts, bestValidSingleMs);
    }
    if (format == RoundFormat.MO3) {
      return scoreTrimmedMean(attempts, bestValidSingleMs, 0, "Mo3: arithmetic mean of all three");
    }
    // AO5: discard exactly one fastest and one slowest.
    return scoreTrimmedMean(
        attempts, bestValidSingleMs, 1, "Ao5: discard one fastest and one slowest");
  }

  private static RoundResult scoreBestOf(List<ScoredAttempt> attempts, Long bestValidSingleMs) {
    if (bestValidSingleMs == null) {
      return new RoundResult(RoundOutcome.DNF, null, null, null, List.of(), "All attempts DNF/DNS");
    }
    ExactValue key = ExactValue.ofMillis(bestValidSingleMs);
    return new RoundResult(
        RoundOutcome.OK,
        key,
        bestValidSingleMs,
        bestValidSingleMs,
        List.of(),
        "Best valid adjusted single");
  }

  /**
   * Shared implementation for Mo3 (discardEachEnd=0) and Ao5 (discardEachEnd=1): sort all attempts
   * ascending by (is-it-OK, value-or-worse-than-any-valid, attempt number) so DNF/DNS always sort
   * after every valid value and ties break deterministically by attempt number (09 "deterministic
   * tie by attempt number"); discard {@code discardEachEnd} entries off each end; if every
   * remaining (middle) entry is OK, average them exactly; otherwise the result is DNF. This single
   * sort-and-trim rule reproduces every Ao5 case in the conformance table, including "fewer than
   * four valid results is DNF" as an emergent property: with two or more non-OK attempts, at least
   * one non-OK entry is always left in the middle after one discard at each end.
   */
  private static RoundResult scoreTrimmedMean(
      List<ScoredAttempt> attempts, Long bestValidSingleMs, int discardEachEnd, String ruleName) {
    List<ScoredAttempt> sorted = new ArrayList<>(attempts);
    sorted.sort(
        Comparator.<ScoredAttempt>comparingInt(a -> a.outcome() == AttemptOutcome.OK ? 0 : 1)
            .thenComparingLong(
                a -> a.outcome() == AttemptOutcome.OK ? a.adjustedTimeMs() : Long.MAX_VALUE)
            .thenComparingInt(ScoredAttempt::attemptNumber));

    int n = sorted.size();
    int keepStart = discardEachEnd;
    int keepEndExclusive = n - discardEachEnd;
    if (keepEndExclusive <= keepStart) {
      // Not enough attempts to discard from both ends and still have anything left to average.
      return new RoundResult(RoundOutcome.DNF, null, null, bestValidSingleMs, List.of(), ruleName);
    }

    List<Integer> discarded = new ArrayList<>();
    for (int i = 0; i < keepStart; i++) {
      discarded.add(sorted.get(i).attemptNumber());
    }
    for (int i = keepEndExclusive; i < n; i++) {
      discarded.add(sorted.get(i).attemptNumber());
    }

    long sum = 0;
    int count = 0;
    boolean allOk = true;
    for (int i = keepStart; i < keepEndExclusive; i++) {
      ScoredAttempt a = sorted.get(i);
      if (a.outcome() != AttemptOutcome.OK) {
        allOk = false;
        break;
      }
      sum += a.adjustedTimeMs();
      count++;
    }

    if (!allOk || count == 0) {
      return new RoundResult(
          RoundOutcome.DNF, null, null, bestValidSingleMs, discarded, ruleName + " — DNF");
    }

    ExactValue average = ExactValue.ofSum(sum, count);
    return new RoundResult(
        RoundOutcome.OK,
        average,
        average.roundHalfUpToTenMs(),
        bestValidSingleMs,
        discarded,
        ruleName);
  }
}

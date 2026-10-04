package com.twistmeet.api.scoring;

import java.util.List;

/**
 * The full derived result for one entrant in one round: outcome, the exact comparison key used for
 * ranking (ms for Bo1-3, a rational average for Mo3/Ao5), the rounded display value, the best
 * single valid attempt (used as the rank-1 tie-break and as the Bo1-3 comparison value itself),
 * which attempt numbers were discarded (Ao5 only), and a short human-readable explanation (09:
 * "Output includes derived result, displayed average, exact comparison key, discarded attempts,
 * rank and explanation").
 */
public record RoundResult(
    RoundOutcome outcome,
    ExactValue comparisonKey,
    Long displayMs,
    Long bestValidSingleMs,
    List<Integer> discardedAttemptNumbers,
    String explanation) {

  public static RoundResult noResult() {
    return new RoundResult(
        RoundOutcome.NO_RESULT, null, null, null, List.of(), "No attempts resolved yet");
  }
}

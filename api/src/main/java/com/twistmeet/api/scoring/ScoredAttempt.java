package com.twistmeet.api.scoring;

/**
 * One resolved attempt as seen by the scoring engine: its original attempt number (used only for
 * deterministic tie-break when discarding equal-valued extremes — 09 "deterministic tie by attempt
 * number"), its outcome, and its adjusted time in milliseconds (raw + penalty; required iff {@code
 * outcome == OK}, otherwise must be {@code null}).
 */
public record ScoredAttempt(int attemptNumber, AttemptOutcome outcome, Long adjustedTimeMs) {

  public ScoredAttempt {
    if (outcome == AttemptOutcome.OK && adjustedTimeMs == null) {
      throw new IllegalArgumentException("OK attempts must carry an adjusted time");
    }
    if (outcome != AttemptOutcome.OK && adjustedTimeMs != null) {
      throw new IllegalArgumentException("Non-OK attempts must not carry an adjusted time");
    }
  }

  public static ScoredAttempt ok(int attemptNumber, long adjustedTimeMs) {
    return new ScoredAttempt(attemptNumber, AttemptOutcome.OK, adjustedTimeMs);
  }

  public static ScoredAttempt dnf(int attemptNumber) {
    return new ScoredAttempt(attemptNumber, AttemptOutcome.DNF, null);
  }

  public static ScoredAttempt dns(int attemptNumber) {
    return new ScoredAttempt(attemptNumber, AttemptOutcome.DNS, null);
  }
}

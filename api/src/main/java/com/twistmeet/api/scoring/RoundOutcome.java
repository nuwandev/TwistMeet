package com.twistmeet.api.scoring;

/**
 * The round-level computed outcome for one entrant. {@code NO_RESULT} is distinct from {@code DNF}:
 * it means the engine was given nothing to score at all (09 property: "Empty/pending attempt set
 * never generates a final result") — a DNF is itself a definite, rankable final result, whereas
 * {@code NO_RESULT} means no result exists yet.
 */
public enum RoundOutcome {
  OK,
  DNF,
  NO_RESULT
}

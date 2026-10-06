package com.twistmeet.api.scoring;

/**
 * 09 "Units and penalty rules": {@code NONE} adjusted value equals raw value; {@code PLUS_TWO}
 * adjusted value is raw + 2000 ms. Store raw time and penalty separately (08 database invariants);
 * this is the one pure function that derives the adjusted value from them.
 */
public enum Penalty {
  NONE,
  PLUS_TWO;

  private static final long PLUS_TWO_MS = 2000;

  public long apply(long rawTimeMs) {
    return this == PLUS_TWO ? rawTimeMs + PLUS_TWO_MS : rawTimeMs;
  }
}

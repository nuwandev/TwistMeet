package com.twistmeet.api.scoring;

/** Supported first-release formats (02 "Supported first-release formats"). */
public enum RoundFormat {
  BO1(1),
  BO2(2),
  BO3(3),
  MO3(3),
  AO5(5);

  private final int attemptCount;

  RoundFormat(int attemptCount) {
    this.attemptCount = attemptCount;
  }

  /** The number of attempt slots this format expects per entrant per round. */
  public int attemptCount() {
    return attemptCount;
  }

  public boolean isBestOf() {
    return this == BO1 || this == BO2 || this == BO3;
  }
}

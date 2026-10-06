package com.twistmeet.api.scoring;

/**
 * An exact rational number of milliseconds (numerator / denominator, denominator &gt; 0), used as
 * the sort/comparison key for averages so that display rounding never affects ordering (09:
 * "Numeric source remains integer milliseconds; average is a rational number (sum / count) and sort
 * key must preserve exact rational value... Do not use language-default floating-point rounding").
 * Comparison is done by cross-multiplication, never by converting to a {@code double}.
 */
public record ExactValue(long numerator, long denominator) implements Comparable<ExactValue> {

  public ExactValue {
    if (denominator <= 0) {
      throw new IllegalArgumentException("denominator must be positive");
    }
  }

  public static ExactValue ofMillis(long ms) {
    return new ExactValue(ms, 1);
  }

  public static ExactValue ofSum(long sumMs, long count) {
    return new ExactValue(sumMs, count);
  }

  @Override
  public int compareTo(ExactValue other) {
    // a/b vs c/d, both denominators positive, cross-multiply: a*d vs c*b.
    long left = this.numerator * other.denominator;
    long right = other.numerator * this.denominator;
    return Long.compare(left, right);
  }

  /**
   * Rounds this exact value to the nearest 10 ms (display hundredths of a second), half-up (09
   * "Display default two decimals with round-half-up").
   */
  public long roundHalfUpToTenMs() {
    // hundredths = numerator / (denominator * 10); round-half-up p/q = floor((2p + q) / (2q)).
    long q = denominator * 10;
    long roundedHundredths = Math.floorDiv(2 * numerator + q, 2 * q);
    return roundedHundredths * 10;
  }
}

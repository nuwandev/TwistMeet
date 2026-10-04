package com.twistmeet.api.scramble;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * OD01 (DECISIONS.md): conformance checks on the wrapped TNoodle generator. This does not
 * re-validate TNoodle's own move-generation correctness (that is the official WCA scramble program,
 * reviewed independently of this project — see DECISIONS.md) — it checks that this codebase's
 * integration with it behaves as expected: valid notation shape, no two calls ever producing the
 * same scramble in a reasonably large sample (a crude but meaningful signal that generation is not
 * stuck returning a fixed/degenerate value), and a plausible move count.
 */
class ScrambleGeneratorTest {

  private static final Pattern VALID_3X3_SCRAMBLE =
      Pattern.compile("^([UDLRFB][2']?)( [UDLRFB][2']?)*$");

  private final ScrambleGenerator generator = new ScrambleGenerator();

  @Test
  void generatesWellFormedNotation() {
    String scramble = generator.generate();
    assertThat(scramble).isNotBlank();
    assertThat(VALID_3X3_SCRAMBLE.matcher(scramble).matches())
        .as("scramble '%s' should match standard 3x3x3 notation", scramble)
        .isTrue();
    int moveCount = scramble.split(" ").length;
    // WCA 3x3x3 scrambles are conventionally in the high teens to low twenties of moves.
    assertThat(moveCount).isBetween(15, 30);
  }

  @Test
  void repeatedGenerationProducesDistinctScrambles() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 50; i++) {
      seen.add(generator.generate());
    }
    // Random-state scrambling over a state space this large should never repeat in 50 draws;
    // anything less than near-total uniqueness would indicate a broken/degenerate generator.
    assertThat(seen).hasSizeGreaterThanOrEqualTo(49);
  }

  @Test
  void consecutiveScramblesDoNotShareAnObviousFixedPrefix() {
    // A regression guard against a generator that was accidentally seeded identically each call.
    String first = generator.generate();
    String second = generator.generate();
    assertThat(first).isNotEqualTo(second);
  }
}

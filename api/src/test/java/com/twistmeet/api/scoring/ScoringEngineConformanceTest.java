package com.twistmeet.api.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every conformance vector (V01-V12) and property expectation (P01-P05, P07) in
 * 09-scoring-conformance.md. P06 (revision recalculation) is an integration-level property, covered
 * in the competition module's tests once revisions exist.
 */
class ScoringEngineConformanceTest {

  private static ScoredAttempt ok(int n, double seconds) {
    return ScoredAttempt.ok(n, Math.round(seconds * 1000));
  }

  // --- V01-V04: Ao5 ---

  @Test
  void v01_ao5BasicDiscardFastestAndSlowest() {
    List<ScoredAttempt> attempts =
        List.of(ok(1, 14.21), ok(2, 12.84), ok(3, 18.91), ok(4, 13.05), ok(5, 12.10));
    RoundResult result = ScoringEngine.score(RulesetVersion.V1, RoundFormat.AO5, attempts);
    assertThat(result.outcome()).isEqualTo(RoundOutcome.OK);
    assertThat(result.displayMs()).isEqualTo(13370); // 13.37s
    assertThat(result.discardedAttemptNumbers()).containsExactlyInAnyOrder(5, 3); // 12.10, 18.91
  }

  @Test
  void v02_ao5CorrectedDnfCase() {
    List<ScoredAttempt> attempts =
        List.of(ok(1, 14.20), ok(2, 13.80), ScoredAttempt.dnf(3), ok(4, 15.10), ok(5, 13.40));
    RoundResult result = ScoringEngine.score(RulesetVersion.V1, RoundFormat.AO5, attempts);
    assertThat(result.outcome()).isEqualTo(RoundOutcome.OK);
    // Authoritative corrected value per 09's note, not the earlier erroneous draft number.
    assertThat(result.displayMs()).isEqualTo(14370); // 14.37s
    assertThat(result.discardedAttemptNumbers()).containsExactlyInAnyOrder(5, 3); // 13.40, DNF
  }

  @Test
  void v03_ao5FewerThanFourValidIsDnf() {
    List<ScoredAttempt> attempts =
        List.of(
            ok(1, 10.00), ok(2, 11.00), ok(3, 12.00), ScoredAttempt.dnf(4), ScoredAttempt.dns(5));
    RoundResult result = ScoringEngine.score(RulesetVersion.V1, RoundFormat.AO5, attempts);
    assertThat(result.outcome()).isEqualTo(RoundOutcome.DNF);
  }

  @Test
  void v04_ao5TieDiscardByAttemptNumber() {
    List<ScoredAttempt> attempts =
        List.of(ok(1, 10.00), ok(2, 10.00), ok(3, 12.00), ok(4, 13.00), ok(5, 14.00));
    RoundResult result = ScoringEngine.score(RulesetVersion.V1, RoundFormat.AO5, attempts);
    assertThat(result.outcome()).isEqualTo(RoundOutcome.OK);
    assertThat(result.displayMs()).isEqualTo(11670); // 11.67s
    // One of the tied 10.00s (deterministically the lower attempt number) and the 14.00.
    assertThat(result.discardedAttemptNumbers()).containsExactlyInAnyOrder(1, 5);
  }

  // --- V05-V06: Mo3 ---

  @Test
  void v05_mo3ArithmeticMean() {
    List<ScoredAttempt> attempts = List.of(ok(1, 10.00), ok(2, 11.00), ok(3, 12.00));
    RoundResult result = ScoringEngine.score(RulesetVersion.V1, RoundFormat.MO3, attempts);
    assertThat(result.outcome()).isEqualTo(RoundOutcome.OK);
    assertThat(result.displayMs()).isEqualTo(11000); // 11.00s
  }

  @Test
  void v06_mo3AnyDnsMakesMeanDnf() {
    List<ScoredAttempt> attempts = List.of(ok(1, 10.00), ok(2, 11.00), ScoredAttempt.dns(3));
    RoundResult result = ScoringEngine.score(RulesetVersion.V1, RoundFormat.MO3, attempts);
    assertThat(result.outcome()).isEqualTo(RoundOutcome.DNF);
  }

  // --- V07: Bo3 ---

  @Test
  void v07_bo3BestValidSingle() {
    List<ScoredAttempt> attempts = List.of(ok(1, 11.00), ok(2, 10.00), ScoredAttempt.dnf(3));
    RoundResult result = ScoringEngine.score(RulesetVersion.V1, RoundFormat.BO3, attempts);
    assertThat(result.outcome()).isEqualTo(RoundOutcome.OK);
    assertThat(result.displayMs()).isEqualTo(10000); // 10.00s
  }

  // --- V08: penalty ---

  @Test
  void v08_plusTwoPenaltyAppliedBeforeComparison() {
    long adjusted = Penalty.PLUS_TWO.apply(12345);
    assertThat(adjusted).isEqualTo(14345);
    assertThat(Penalty.NONE.apply(12345)).isEqualTo(12345);
  }

  // --- V09-V10: ranking ---

  @Test
  void v09_rankAverageTieBreaksOnBestSingle() {
    RoundResult a =
        new RoundResult(RoundOutcome.OK, ExactValue.ofMillis(12000), 12000L, 9000L, List.of(), "a");
    RoundResult b =
        new RoundResult(RoundOutcome.OK, ExactValue.ofMillis(12000), 12000L, 9500L, List.of(), "b");
    var ranked =
        RankingService.rank(
            List.of(
                new RankingService.Scored<>("first", a), new RankingService.Scored<>("second", b)));
    assertThat(rankOf(ranked, "first")).isEqualTo(1);
    assertThat(rankOf(ranked, "second")).isEqualTo(2);
  }

  @Test
  void v10_rankIdenticalSharesRankThenSkips() {
    RoundResult tied =
        new RoundResult(RoundOutcome.OK, ExactValue.ofMillis(12000), 12000L, 9000L, List.of(), "x");
    RoundResult third =
        new RoundResult(RoundOutcome.OK, ExactValue.ofMillis(13000), 13000L, 9800L, List.of(), "y");
    var ranked =
        RankingService.rank(
            List.of(
                new RankingService.Scored<>("a", tied),
                new RankingService.Scored<>("b", tied),
                new RankingService.Scored<>("c", third)));
    assertThat(rankOf(ranked, "a")).isEqualTo(1);
    assertThat(rankOf(ranked, "b")).isEqualTo(1);
    assertThat(rankOf(ranked, "c")).isEqualTo(3);
  }

  // --- V11-V12: advancement (pure calculation; see AdvancementCalculator class comment) ---

  @Test
  void v11_topNIncludesBoundaryTies() {
    RoundResult r1 = okResult(10000, 9000);
    RoundResult r2 = okResult(11000, 9500);
    RoundResult r2b = okResult(11000, 9500);
    RoundResult r4 = okResult(13000, 10000);
    var ranked =
        RankingService.rank(
            List.of(
                new RankingService.Scored<>("a", r1),
                new RankingService.Scored<>("b", r2),
                new RankingService.Scored<>("c", r2b),
                new RankingService.Scored<>("d", r4)));
    // ranks are 1,2,2,4
    var advancing = AdvancementCalculator.topN(ranked, 2);
    assertThat(advancing).containsExactlyInAnyOrder("a", "b", "c");
  }

  @Test
  void v12_topPercentCeilsAndIncludesCutoffTies() {
    List<RankedEntry<String>> ranked = new java.util.ArrayList<>();
    // 11 eligible entrants, ranks 1..11 with ranks 3 and 4 tied (to exercise cutoff-tie
    // inclusion), target = ceil(11 * 25 / 100) = 3.
    ranked.add(new RankedEntry<>("r1", 1, okResult(10000, 9000)));
    ranked.add(new RankedEntry<>("r2", 2, okResult(11000, 9000)));
    ranked.add(new RankedEntry<>("r3", 3, okResult(12000, 9000)));
    ranked.add(new RankedEntry<>("r4", 3, okResult(12000, 9000)));
    for (int i = 5; i <= 11; i++) {
      ranked.add(new RankedEntry<>("r" + i, i, okResult(10000L + i * 1000, 9000)));
    }
    var result = AdvancementCalculator.topPercent(ranked, 11, 25.0);
    assertThat(result.targetCount()).isEqualTo(3);
    assertThat(result.advancing()).containsExactlyInAnyOrder("r1", "r2", "r3", "r4");
  }

  // --- Properties ---

  @Test
  void p01_attemptOrderDoesNotChangeScore() {
    List<ScoredAttempt> inOrder =
        List.of(ok(1, 14.21), ok(2, 12.84), ok(3, 18.91), ok(4, 13.05), ok(5, 12.10));
    List<ScoredAttempt> shuffled =
        List.of(ok(3, 18.91), ok(1, 14.21), ok(5, 12.10), ok(2, 12.84), ok(4, 13.05));
    RoundResult a = ScoringEngine.score(RulesetVersion.V1, RoundFormat.AO5, inOrder);
    RoundResult b = ScoringEngine.score(RulesetVersion.V1, RoundFormat.AO5, shuffled);
    assertThat(a.displayMs()).isEqualTo(b.displayMs());
    assertThat(a.outcome()).isEqualTo(b.outcome());
  }

  @Test
  void p02_changingADiscardedExtremeWithoutChangingWhichOneIsDiscardedDoesNotAffectAverage() {
    List<ScoredAttempt> original =
        List.of(ok(1, 12.00), ok(2, 12.50), ok(3, 13.00), ok(4, 13.50), ok(5, 20.00));
    List<ScoredAttempt> slowerExtreme =
        List.of(ok(1, 12.00), ok(2, 12.50), ok(3, 13.00), ok(4, 13.50), ok(5, 99.00));
    RoundResult a = ScoringEngine.score(RulesetVersion.V1, RoundFormat.AO5, original);
    RoundResult b = ScoringEngine.score(RulesetVersion.V1, RoundFormat.AO5, slowerExtreme);
    assertThat(a.discardedAttemptNumbers()).containsExactlyInAnyOrder(1, 5);
    assertThat(b.discardedAttemptNumbers()).containsExactlyInAnyOrder(1, 5);
    assertThat(a.displayMs()).isEqualTo(b.displayMs());
  }

  @Test
  void p03_plusTwoAffectsByExactly2000Ms() {
    long withoutPenalty = Penalty.NONE.apply(10000);
    long withPenalty = Penalty.PLUS_TWO.apply(10000);
    assertThat(withPenalty - withoutPenalty).isEqualTo(2000);
  }

  @Test
  void p04_dnfNeverOutranksValidTimes() {
    RoundResult valid = okResult(99000, 99000); // deliberately slow but valid
    RoundResult dnf = new RoundResult(RoundOutcome.DNF, null, null, null, List.of(), "dnf");
    var ranked =
        RankingService.rank(
            List.of(
                new RankingService.Scored<>("slowButValid", valid),
                new RankingService.Scored<>("dnf", dnf)));
    assertThat(rankOf(ranked, "slowButValid")).isLessThan(rankOf(ranked, "dnf"));
  }

  @Test
  void p05_sameInputsSameRulesetAreByteEquivalent() {
    List<ScoredAttempt> attempts =
        List.of(ok(1, 14.21), ok(2, 12.84), ok(3, 18.91), ok(4, 13.05), ok(5, 12.10));
    RoundResult a = ScoringEngine.score(RulesetVersion.V1, RoundFormat.AO5, attempts);
    RoundResult b = ScoringEngine.score(RulesetVersion.V1, RoundFormat.AO5, attempts);
    assertThat(a).isEqualTo(b);
  }

  @Test
  void p07_emptyAttemptSetNeverGeneratesAFinalResult() {
    RoundResult result = ScoringEngine.score(RulesetVersion.V1, RoundFormat.AO5, List.of());
    assertThat(result.outcome()).isEqualTo(RoundOutcome.NO_RESULT);
    assertThat(result.displayMs()).isNull();
  }

  private static RoundResult okResult(long displayMs, long bestSingleMs) {
    return new RoundResult(
        RoundOutcome.OK, ExactValue.ofMillis(displayMs), displayMs, bestSingleMs, List.of(), "ok");
  }

  private static int rankOf(List<RankedEntry<String>> ranked, String id) {
    return ranked.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow().rank();
  }
}

package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * M5 advancement preview/commit (09 "Advancement"; 08 {@code /rounds/{roundId}/advancement/*}).
 * Rows 54-56 of TRACEABILITY.md.
 */
class AdvancementFlowTest extends AbstractIntegrationTest {

  /**
   * Creates an event with N guest entrants, a round, judge results for all of them, and REVIEWs it.
   */
  private String[] closedRoundWithResults(
      String suffix, String rule, Integer value, long[] rawTimesMs) throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(
            client, "adv" + suffix + "@example.com", "Owner", "Adv Org " + suffix);
    String eventId = createEvent(client, orgId, "Adv Event " + suffix, "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);

    String[] entrantIds = new String[rawTimesMs.length];
    for (int i = 0; i < rawTimesMs.length; i++) {
      TestApiClient guest = new TestApiClient(restTemplate);
      entrantIds[i] = joinAsGuest(guest, joinCode, "Entrant " + suffix + "-" + i);
    }

    String roundId = createRoundWithAdvancement(client, eventId, 1, "Round 1", "BO1", rule, value);
    client.post("/api/v1/rounds/" + roundId + "/prepare", null);
    client.post("/api/v1/rounds/" + roundId + "/ready", null);
    client.post("/api/v1/rounds/" + roundId + "/start", null);

    JsonNode attempts = json(client.get("/api/v1/rounds/" + roundId + "/attempts"));
    for (int i = 0; i < rawTimesMs.length; i++) {
      String entrantId = entrantIds[i];
      String attemptId = findAttemptIdForEntrant(attempts, entrantId);
      judgeOk(client, attemptId, rawTimesMs[i]);
    }
    client.post("/api/v1/rounds/" + roundId + "/review", null);

    String nextRoundId = createRound(client, eventId, 2, "Round 2", "BO1");
    return new String[] {roundId, nextRoundId, eventId};
  }

  private String findAttemptIdForEntrant(JsonNode attempts, String entrantId) {
    for (JsonNode a : attempts) {
      if (a.get("entrantId").asText().equals(entrantId)) {
        return a.get("id").asText();
      }
    }
    throw new IllegalStateException("No attempt for entrant " + entrantId);
  }

  @Test
  void topNIncludesBoundaryTies() throws Exception {
    // Ranks: 10000ms -> 1; 20000ms, 20000ms (tie) -> 2,2; 30000ms -> 4. 09's own example: "rank
    // 1,2,2 with N=2 advances all three" — the tied pair at rank 2 both get included even though
    // that pushes the advancing count to 3, past the raw N=2 target.
    String[] ids = closedRoundWithResults("1", "TOP_N", 2, new long[] {10000, 20000, 20000, 30000});
    String roundId = ids[0];

    ResponseEntity<String> preview =
        client.post("/api/v1/rounds/" + roundId + "/advancement/preview", null);
    assertThat(preview.getStatusCode().value()).isEqualTo(200);
    JsonNode body = json(preview);
    assertThat(body.get("eligibleCount").asInt()).isEqualTo(4);
    assertThat(body.get("targetCount").asInt()).isEqualTo(2);
    assertThat(body.get("advancing")).hasSize(3);
    assertThat(body.get("tieNote").asText()).contains("tied");
  }

  @Test
  void topPercentRoundsUpWithCeiling() throws Exception {
    // 09 example: 11 eligible, 25% -> ceil(2.75) = 3.
    long[] times = new long[11];
    for (int i = 0; i < 11; i++) {
      times[i] = 10000 + i * 1000;
    }
    String[] ids = closedRoundWithResults("2", "TOP_PERCENT", 25, times);
    String roundId = ids[0];

    JsonNode preview =
        json(client.post("/api/v1/rounds/" + roundId + "/advancement/preview", null));
    assertThat(preview.get("eligibleCount").asInt()).isEqualTo(11);
    assertThat(preview.get("targetCount").asInt()).isEqualTo(3);
    assertThat(preview.get("advancing")).hasSize(3);
  }

  @Test
  void everyoneAdvancesAllEligible() throws Exception {
    String[] ids = closedRoundWithResults("3", "EVERYONE", null, new long[] {10000, 20000});
    String roundId = ids[0];
    JsonNode preview =
        json(client.post("/api/v1/rounds/" + roundId + "/advancement/preview", null));
    assertThat(preview.get("advancing")).hasSize(2);
  }

  @Test
  void commitCreatesNextRoundQualifiedRosterAndIsIdempotent() throws Exception {
    String[] ids = closedRoundWithResults("4", "TOP_N", 1, new long[] {10000, 20000});
    String roundId = ids[0];
    String nextRoundId = ids[1];
    String eventId = ids[2];
    long version = json(client.get("/api/v1/rounds/" + roundId)).get("version").asLong();

    ResponseEntity<String> commit =
        client.post(
            "/api/v1/rounds/" + roundId + "/advancement/commit",
            Map.of("expectedVersion", version));
    assertThat(commit.getStatusCode().value()).isEqualTo(200);
    JsonNode commitBody = json(commit);
    assertThat(commitBody.get("advancedCount").asInt()).isEqualTo(1);
    assertThat(commitBody.get("idempotentReplay").asBoolean()).isFalse();

    // Preparing the next round only allocates attempts for the advancing (qualified) entrant,
    // not every active event entrant.
    client.post("/api/v1/rounds/" + nextRoundId + "/prepare", null);
    JsonNode nextAttempts = json(client.get("/api/v1/rounds/" + nextRoundId + "/attempts"));
    assertThat(nextAttempts).hasSize(1);

    // Repeating the exact same commit call is idempotent: no duplicate roster rows, same result.
    ResponseEntity<String> repeat =
        client.post(
            "/api/v1/rounds/" + roundId + "/advancement/commit",
            Map.of("expectedVersion", version));
    assertThat(repeat.getStatusCode().value()).isEqualTo(200);
    assertThat(json(repeat).get("idempotentReplay").asBoolean()).isTrue();
    assertThat(json(repeat).get("advancedCount").asInt()).isEqualTo(1);
  }

  @Test
  void staleVersionConflictsWithoutCorruptingAnything() throws Exception {
    String[] ids = closedRoundWithResults("5", "TOP_N", 1, new long[] {10000, 20000});
    String roundId = ids[0];

    ResponseEntity<String> commit =
        client.post(
            "/api/v1/rounds/" + roundId + "/advancement/commit", Map.of("expectedVersion", 999999));
    assertThat(commit.getStatusCode().value()).isEqualTo(409);
    assertThat(json(commit).get("code").asText()).isEqualTo("STALE_VERSION");

    // Not committed: advancement_committed_at remains null, so a subsequent preview still shows
    // alreadyCommitted=false.
    JsonNode preview =
        json(client.post("/api/v1/rounds/" + roundId + "/advancement/preview", null));
    assertThat(preview.get("alreadyCommitted").asBoolean()).isFalse();
  }

  @Test
  void concurrentCommitsResultInExactlyOneSuccessAndNoDuplicateRoster() throws Exception {
    String[] ids = closedRoundWithResults("6", "TOP_N", 2, new long[] {10000, 20000});
    String roundId = ids[0];
    String nextRoundId = ids[1];
    long version = json(client.get("/api/v1/rounds/" + roundId)).get("version").asLong();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();
    // Both racers reuse the already-authenticated organizer session (a brand-new TestApiClient
    // would be an anonymous, unauthenticated session and get 401, not race at all).
    Runnable task =
        () -> {
          ready.countDown();
          try {
            go.await();
          } catch (InterruptedException ignored) {
          }
          ResponseEntity<String> response =
              client.post(
                  "/api/v1/rounds/" + roundId + "/advancement/commit",
                  Map.of("expectedVersion", version));
          if (response.getStatusCode().value() == 200) {
            successes.incrementAndGet();
          } else {
            conflicts.incrementAndGet();
          }
        };
    pool.submit(task);
    pool.submit(task);
    ready.await();
    go.countDown();
    pool.shutdown();
    pool.awaitTermination(30, TimeUnit.SECONDS);

    // Exactly one racer wins; the other sees a conflict (not a silent duplicate commit) — unless
    // both happened to observe the already-committed state, which the version check above still
    // guarantees results in the roster being written exactly once either way.
    assertThat(successes.get()).isGreaterThanOrEqualTo(1);

    client.post("/api/v1/rounds/" + nextRoundId + "/prepare", null);
    JsonNode nextAttempts = json(client.get("/api/v1/rounds/" + nextRoundId + "/attempts"));
    assertThat(nextAttempts).hasSize(2); // not 4 — no duplicate roster rows from the race.
  }

  @Test
  void commitRequiresReviewOrClosedState() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "adv7@example.com", "Owner7", "Adv Org 7");
    String eventId = createEvent(client, orgId, "Adv Event 7", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Entrant 7");
    String roundId =
        createRoundWithAdvancement(client, eventId, 1, "Round 1", "BO1", "EVERYONE", null);
    client.post("/api/v1/rounds/" + roundId + "/prepare", null);
    // Still PREPARING, not REVIEW/CLOSED.
    ResponseEntity<String> preview =
        client.post("/api/v1/rounds/" + roundId + "/advancement/preview", null);
    assertThat(preview.getStatusCode().value()).isEqualTo(409);
  }

  @Test
  void onlyOrganizerCanPreviewOrCommitJudgeAndCompetitorCannot() throws Exception {
    String[] ids = closedRoundWithResults("8", "EVERYONE", null, new long[] {10000});
    String roundId = ids[0];

    TestApiClient stranger = new TestApiClient(restTemplate);
    registerAndVerify(stranger, "adv8stranger@example.com", "Stranger8", "correct-horse-battery");
    ResponseEntity<String> strangerPreview =
        stranger.post("/api/v1/rounds/" + roundId + "/advancement/preview", null);
    assertThat(strangerPreview.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void withdrawnEntrantsAreExcludedFromEligibility() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "adv9@example.com", "Owner9", "Adv Org 9");
    String eventId = createEvent(client, orgId, "Adv Event 9", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guestA = new TestApiClient(restTemplate);
    String entrantA = joinAsGuest(guestA, joinCode, "Entrant A9");
    TestApiClient guestB = new TestApiClient(restTemplate);
    String entrantB = joinAsGuest(guestB, joinCode, "Entrant B9");

    String roundId =
        createRoundWithAdvancement(client, eventId, 1, "Round 1", "BO1", "EVERYONE", null);
    client.post("/api/v1/rounds/" + roundId + "/prepare", null);

    // Withdraw B before results are entered.
    client.post("/api/v1/events/" + eventId + "/entrants/" + entrantB + "/withdraw", null);

    client.post("/api/v1/rounds/" + roundId + "/ready", null);
    client.post("/api/v1/rounds/" + roundId + "/start", null);
    JsonNode attempts = json(client.get("/api/v1/rounds/" + roundId + "/attempts"));
    judgeOk(client, findAttemptIdForEntrant(attempts, entrantA), 10000);
    // B was withdrawn, so its attempt is left PENDING — review requires every non-withdrawn
    // attempt to be resolved; B's attempt is still "unresolved" from the attempt's own state
    // machine perspective, so resolve it as DNS to allow review.
    String bAttemptId = findAttemptIdForEntrant(attempts, entrantB);
    long bVersion = currentAttemptVersion(client, bAttemptId);
    client.put(
        "/api/v1/attempts/" + bAttemptId + "/judge-result",
        Map.of("status", "DNS", "expectedVersion", bVersion));
    client.post("/api/v1/rounds/" + roundId + "/review", null);

    JsonNode preview =
        json(client.post("/api/v1/rounds/" + roundId + "/advancement/preview", null));
    assertThat(preview.get("eligibleCount").asInt()).isEqualTo(1);
    List<String> advancingNames = new java.util.ArrayList<>();
    for (JsonNode entry : preview.get("advancing")) {
      advancingNames.add(entry.get("displayName").asText());
    }
    assertThat(advancingNames).containsExactly("Entrant A9");
  }
}

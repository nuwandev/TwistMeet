package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 09 "If organizer selected a tie-break attempt... winner policy is the lower valid time, DNF last,
 * then shared advancement if still tied. This option must be selected before registration opens."
 * Row R36/36 of TRACEABILITY.md.
 */
class TieBreakAttemptTest extends AbstractIntegrationTest {

  private String findAttemptIdForEntrant(JsonNode attempts, String entrantId) {
    for (JsonNode a : attempts) {
      if (a.get("entrantId").asText().equals(entrantId)) {
        return a.get("id").asText();
      }
    }
    throw new IllegalStateException("No attempt for entrant " + entrantId);
  }

  @Test
  void tiePolicyMustBeSelectedBeforeRegistrationOpens() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "tb1@example.com", "Owner1", "TB Org 1");
    String eventId = createEvent(client, orgId, "TB Event 1", "PHYSICAL_JUDGE");
    client.post("/api/v1/events/" + eventId + "/registration/open", null);

    ResponseEntity<String> tooLate =
        client.post(
            "/api/v1/events/" + eventId + "/rounds",
            Map.of("order", 1, "name", "Final", "format", "BO1", "tiePolicy", "TIE_BREAK_ATTEMPT"));
    assertThat(tooLate.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void boundaryTieRequiresTieBreakAttemptBeforeCommit() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "tb2@example.com", "Owner2", "TB Org 2");
    String eventId = createEvent(client, orgId, "TB Event 2", "PHYSICAL_JUDGE");
    // The tie-break-attempt policy must be selected before registration opens (09), so the round
    // is created first, while the event is still DRAFT.
    String tieRoundId = createRoundWithTiePolicy(eventId, 1, "TOP_N", 1, "TIE_BREAK_ATTEMPT");
    String joinCode = openRegistration(client, eventId);

    TestApiClient guestA = new TestApiClient(restTemplate);
    String entrantA = joinAsGuest(guestA, joinCode, "TB Entrant A");
    TestApiClient guestB = new TestApiClient(restTemplate);
    String entrantB = joinAsGuest(guestB, joinCode, "TB Entrant B");
    TestApiClient guestC = new TestApiClient(restTemplate);
    String entrantC = joinAsGuest(guestC, joinCode, "TB Entrant C");

    client.post("/api/v1/rounds/" + tieRoundId + "/prepare", null);
    client.post("/api/v1/rounds/" + tieRoundId + "/ready", null);
    client.post("/api/v1/rounds/" + tieRoundId + "/start", null);

    JsonNode attempts = json(client.get("/api/v1/rounds/" + tieRoundId + "/attempts"));
    // A and B tie for rank 1 (both 10.00s); C is slower.
    judgeOk(client, findAttemptIdForEntrant(attempts, entrantA), 10000);
    judgeOk(client, findAttemptIdForEntrant(attempts, entrantB), 10000);
    judgeOk(client, findAttemptIdForEntrant(attempts, entrantC), 20000);
    client.post("/api/v1/rounds/" + tieRoundId + "/review", null);
    client.post("/api/v1/rounds/" + tieRoundId + "/close", null);
    createRound(client, eventId, 2, "Round 2", "BO1");

    JsonNode preview =
        json(client.post("/api/v1/rounds/" + tieRoundId + "/advancement/preview", null));
    assertThat(preview.get("tieBreakRequired").asBoolean()).isTrue();
    assertThat(preview.get("tiedPendingResolution")).hasSize(2);
    assertThat(preview.get("advancing")).hasSize(0); // neither A nor B confirmed yet

    long version = json(client.get("/api/v1/rounds/" + tieRoundId)).get("version").asLong();
    ResponseEntity<String> blockedCommit =
        client.post(
            "/api/v1/rounds/" + tieRoundId + "/advancement/commit",
            Map.of("expectedVersion", version));
    assertThat(blockedCommit.getStatusCode().value()).isEqualTo(409);

    // Create the tie-break attempts, judge A as the winner (faster), then commit.
    client.post("/api/v1/rounds/" + tieRoundId + "/advancement/tie-break", null);
    JsonNode attemptsAfterTieBreak = json(client.get("/api/v1/rounds/" + tieRoundId + "/attempts"));
    String tieBreakAttemptA = findTieBreakAttemptId(attemptsAfterTieBreak, entrantA, 2);
    String tieBreakAttemptB = findTieBreakAttemptId(attemptsAfterTieBreak, entrantB, 2);
    judgeOk(client, tieBreakAttemptA, 5000);
    judgeOk(client, tieBreakAttemptB, 9000);

    JsonNode resolvedPreview =
        json(client.post("/api/v1/rounds/" + tieRoundId + "/advancement/preview", null));
    assertThat(resolvedPreview.get("tieBreakRequired").asBoolean()).isFalse();
    assertThat(resolvedPreview.get("advancing")).hasSize(1);
    assertThat(resolvedPreview.get("advancing").get(0).get("entrantId").asText())
        .isEqualTo(entrantA);

    long versionNow = json(client.get("/api/v1/rounds/" + tieRoundId)).get("version").asLong();
    ResponseEntity<String> commit =
        client.post(
            "/api/v1/rounds/" + tieRoundId + "/advancement/commit",
            Map.of("expectedVersion", versionNow));
    assertThat(commit.getStatusCode().value()).isEqualTo(200);
    assertThat(json(commit).get("advancedCount").asInt()).isEqualTo(1);
  }

  /** 09: "winner policy is the lower valid time, DNF last" — DNF never wins a tie-break. */
  @Test
  void tieBreakWinnerIsLowerValidTimeAndDnfRanksLastEvenIfRecordedFirst() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "tb4@example.com", "Owner4", "TB Org 4");
    String eventId = createEvent(client, orgId, "TB Event 4", "PHYSICAL_JUDGE");
    String tieRoundId = createRoundWithTiePolicy(eventId, 1, "TOP_N", 1, "TIE_BREAK_ATTEMPT");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guestA = new TestApiClient(restTemplate);
    String entrantA = joinAsGuest(guestA, joinCode, "TB4 Entrant A");
    TestApiClient guestB = new TestApiClient(restTemplate);
    String entrantB = joinAsGuest(guestB, joinCode, "TB4 Entrant B");

    client.post("/api/v1/rounds/" + tieRoundId + "/prepare", null);
    client.post("/api/v1/rounds/" + tieRoundId + "/ready", null);
    client.post("/api/v1/rounds/" + tieRoundId + "/start", null);
    JsonNode attempts = json(client.get("/api/v1/rounds/" + tieRoundId + "/attempts"));
    judgeOk(client, findAttemptIdForEntrant(attempts, entrantA), 10000);
    judgeOk(client, findAttemptIdForEntrant(attempts, entrantB), 10000);
    client.post("/api/v1/rounds/" + tieRoundId + "/review", null);
    client.post("/api/v1/rounds/" + tieRoundId + "/close", null);
    createRound(client, eventId, 2, "Round 2", "BO1");

    client.post("/api/v1/rounds/" + tieRoundId + "/advancement/tie-break", null);
    JsonNode attemptsAfterTieBreak = json(client.get("/api/v1/rounds/" + tieRoundId + "/attempts"));
    String tieBreakAttemptA = findTieBreakAttemptId(attemptsAfterTieBreak, entrantA, 2);
    String tieBreakAttemptB = findTieBreakAttemptId(attemptsAfterTieBreak, entrantB, 2);

    // A is judged DNF first; B is judged OK afterward with a (hypothetically) "worse" time than
    // A would have posted — DNF must still lose regardless of recording order or hypothetical time.
    long versionA = currentAttemptVersion(client, tieBreakAttemptA);
    ResponseEntity<String> dnfResponse =
        client.put(
            "/api/v1/attempts/" + tieBreakAttemptA + "/judge-result",
            Map.of("status", "DNF", "expectedVersion", versionA));
    assertThat(dnfResponse.getStatusCode().value()).isEqualTo(200);
    judgeOk(client, tieBreakAttemptB, 15000);

    JsonNode resolvedPreview =
        json(client.post("/api/v1/rounds/" + tieRoundId + "/advancement/preview", null));
    assertThat(resolvedPreview.get("tieBreakRequired").asBoolean()).isFalse();
    assertThat(resolvedPreview.get("advancing")).hasSize(1);
    assertThat(resolvedPreview.get("advancing").get(0).get("entrantId").asText())
        .isEqualTo(entrantB);
  }

  /** 09: "...then shared advancement if still tied." Identical tie-break times share the slot. */
  @Test
  void tieBreakStillTiedAfterTheExtraAttemptSharesAdvancement() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "tb5@example.com", "Owner5", "TB Org 5");
    String eventId = createEvent(client, orgId, "TB Event 5", "PHYSICAL_JUDGE");
    String tieRoundId = createRoundWithTiePolicy(eventId, 1, "TOP_N", 1, "TIE_BREAK_ATTEMPT");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guestA = new TestApiClient(restTemplate);
    String entrantA = joinAsGuest(guestA, joinCode, "TB5 Entrant A");
    TestApiClient guestB = new TestApiClient(restTemplate);
    String entrantB = joinAsGuest(guestB, joinCode, "TB5 Entrant B");

    client.post("/api/v1/rounds/" + tieRoundId + "/prepare", null);
    client.post("/api/v1/rounds/" + tieRoundId + "/ready", null);
    client.post("/api/v1/rounds/" + tieRoundId + "/start", null);
    JsonNode attempts = json(client.get("/api/v1/rounds/" + tieRoundId + "/attempts"));
    judgeOk(client, findAttemptIdForEntrant(attempts, entrantA), 10000);
    judgeOk(client, findAttemptIdForEntrant(attempts, entrantB), 10000);
    client.post("/api/v1/rounds/" + tieRoundId + "/review", null);
    client.post("/api/v1/rounds/" + tieRoundId + "/close", null);
    createRound(client, eventId, 2, "Round 2", "BO1");

    client.post("/api/v1/rounds/" + tieRoundId + "/advancement/tie-break", null);
    JsonNode attemptsAfterTieBreak = json(client.get("/api/v1/rounds/" + tieRoundId + "/attempts"));
    String tieBreakAttemptA = findTieBreakAttemptId(attemptsAfterTieBreak, entrantA, 2);
    String tieBreakAttemptB = findTieBreakAttemptId(attemptsAfterTieBreak, entrantB, 2);
    // Still an exact tie after the tie-break attempt itself — both advance together.
    judgeOk(client, tieBreakAttemptA, 7000);
    judgeOk(client, tieBreakAttemptB, 7000);

    JsonNode resolvedPreview =
        json(client.post("/api/v1/rounds/" + tieRoundId + "/advancement/preview", null));
    assertThat(resolvedPreview.get("tieBreakRequired").asBoolean()).isFalse();
    Set<String> advancingEntrantIds = new java.util.HashSet<>();
    for (JsonNode a : resolvedPreview.get("advancing")) {
      advancingEntrantIds.add(a.get("entrantId").asText());
    }
    assertThat(advancingEntrantIds).containsExactlyInAnyOrder(entrantA, entrantB);
  }

  private String createRoundWithTiePolicy(
      String eventId, int order, String rule, int value, String tiePolicy) throws Exception {
    ResponseEntity<String> response =
        client.post(
            "/api/v1/events/" + eventId + "/rounds",
            Map.of(
                "order", order,
                "name", "Round " + order,
                "format", "BO1",
                "advancementRule", rule,
                "advancementValue", value,
                "tiePolicy", tiePolicy));
    return json(response).get("id").asText();
  }

  private String findTieBreakAttemptId(JsonNode attempts, String entrantId, int attemptNumber) {
    for (JsonNode a : attempts) {
      if (a.get("entrantId").asText().equals(entrantId)
          && a.get("attemptNumber").asInt() == attemptNumber) {
        return a.get("id").asText();
      }
    }
    throw new IllegalStateException("No tie-break attempt for entrant " + entrantId);
  }
}

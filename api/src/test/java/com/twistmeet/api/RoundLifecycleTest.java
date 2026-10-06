package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * 00 §6 round lifecycle (DRAFT -> PREPARING -> READY -> LIVE -> REVIEW -> CLOSED) and format/
 * advancement validation. Rows 30-37 of TRACEABILITY.md.
 */
class RoundLifecycleTest extends AbstractIntegrationTest {

  @Test
  void roundMovesThroughEveryStateInOrderAndRejectsSkippingAState() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "round1@example.com", "Owner", "Round Org");
    String eventId = createEvent(client, orgId, "Round Event", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Competitor");

    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    assertThat(json(client.get("/api/v1/rounds/" + roundId)).get("state").asText())
        .isEqualTo("DRAFT");

    // Cannot skip straight to ready/start/review/close from DRAFT.
    assertThat(client.post("/api/v1/rounds/" + roundId + "/ready", null).getStatusCode().value())
        .isEqualTo(409);
    assertThat(client.post("/api/v1/rounds/" + roundId + "/start", null).getStatusCode().value())
        .isEqualTo(409);
    assertThat(client.post("/api/v1/rounds/" + roundId + "/close", null).getStatusCode().value())
        .isEqualTo(409);

    ResponseEntity<String> prepared = client.post("/api/v1/rounds/" + roundId + "/prepare", null);
    assertThat(json(prepared).get("state").asText()).isEqualTo("PREPARING");
    // Prepare froze one entrant into BO1's single attempt slot.
    assertThat(json(client.get("/api/v1/rounds/" + roundId + "/attempts"))).hasSize(1);

    assertThat(
            json(client.post("/api/v1/rounds/" + roundId + "/ready", null)).get("state").asText())
        .isEqualTo("READY");
    assertThat(
            json(client.post("/api/v1/rounds/" + roundId + "/start", null)).get("state").asText())
        .isEqualTo("LIVE");

    // REVIEW is blocked while the one attempt is still PENDING.
    assertThat(client.post("/api/v1/rounds/" + roundId + "/review", null).getStatusCode().value())
        .isEqualTo(409);

    String attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();
    client.put(
        "/api/v1/attempts/" + attemptId + "/judge-result",
        Map.of("status", "OK", "rawTimeMs", 10000, "expectedVersion", 0));

    assertThat(
            json(client.post("/api/v1/rounds/" + roundId + "/review", null)).get("state").asText())
        .isEqualTo("REVIEW");
    assertThat(
            json(client.post("/api/v1/rounds/" + roundId + "/close", null)).get("state").asText())
        .isEqualTo("CLOSED");
  }

  @Test
  void onlyOrganizerCanConfigureOrTransitionRounds() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "round2@example.com", "Owner", "Round Org 2");
    String eventId = createEvent(client, orgId, "Round Event 2", "PHYSICAL_JUDGE");

    TestApiClient stranger = new TestApiClient(restTemplate);
    registerAndVerify(stranger, "strangerround@example.com", "Stranger", "correct-horse-battery");

    ResponseEntity<String> createByStranger =
        stranger.post(
            "/api/v1/events/" + eventId + "/rounds",
            Map.of("order", 1, "name", "Final", "format", "BO1"));
    assertThat(createByStranger.getStatusCode().value()).isEqualTo(404); // anti-enumeration

    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    ResponseEntity<String> prepareByStranger =
        stranger.post("/api/v1/rounds/" + roundId + "/prepare", null);
    assertThat(prepareByStranger.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void judgeAssignedToTheEventCannotConfigureRoundsButCanEnterResults() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "round3@example.com", "Owner", "Round Org 3");
    String eventId = createEvent(client, orgId, "Round Event 3", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Competitor");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");

    TestApiClient judgeClient = new TestApiClient(restTemplate);
    ResponseEntity<String> judgeVerify =
        registerAndVerify(judgeClient, "judge1@example.com", "Judge One", "correct-horse-battery");
    String judgeUserId = json(judgeVerify).get("id").asText();

    client.post(
        "/api/v1/events/" + eventId + "/staff-assignments",
        Map.of("userId", judgeUserId, "role", "JUDGE"));

    // Judge cannot prepare/configure the round.
    ResponseEntity<String> judgePrepare =
        judgeClient.post("/api/v1/rounds/" + roundId + "/prepare", null);
    assertThat(judgePrepare.getStatusCode().value()).isEqualTo(403);

    prepareReadyStart(client, roundId);

    // Judge CAN enter a result once the round is live.
    String attemptId =
        json(judgeClient.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();
    ResponseEntity<String> judgeResult =
        judgeClient.put(
            "/api/v1/attempts/" + attemptId + "/judge-result",
            Map.of("status", "OK", "rawTimeMs", 9999, "expectedVersion", 0));
    assertThat(judgeResult.getStatusCode().value()).isEqualTo(200);

    // But still cannot close the round.
    client.post("/api/v1/rounds/" + roundId + "/review", null);
    ResponseEntity<String> judgeClose =
        judgeClient.post("/api/v1/rounds/" + roundId + "/close", null);
    assertThat(judgeClose.getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void advancementTopNRequiresAPositiveValueAndCannotExceedFrozenEntrants() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "round4@example.com", "Owner", "Round Org 4");
    String eventId = createEvent(client, orgId, "Round Event 4", "PHYSICAL_JUDGE");

    ResponseEntity<String> invalid =
        client.post(
            "/api/v1/events/" + eventId + "/rounds",
            Map.of("order", 1, "name", "Final", "format", "AO5", "advancementRule", "TOP_N"));
    assertThat(invalid.getStatusCode().value()).isEqualTo(400);

    String joinCode = openRegistration(client, eventId);
    TestApiClient onlyEntrant = new TestApiClient(restTemplate);
    joinAsGuest(onlyEntrant, joinCode, "Solo");

    String roundId =
        json(client.post(
                "/api/v1/events/" + eventId + "/rounds",
                Map.of(
                    "order",
                    1,
                    "name",
                    "Final",
                    "format",
                    "AO5",
                    "advancementRule",
                    "TOP_N",
                    "advancementValue",
                    5)))
            .get("id")
            .asText();
    ResponseEntity<String> prepareTooFewEntrants =
        client.post("/api/v1/rounds/" + roundId + "/prepare", null);
    assertThat(prepareTooFewEntrants.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void nonexistentRoundIs404() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "round5@example.com", "Owner", "Round Org 5");
    createEvent(client, orgId, "Round Event 5", "PHYSICAL_JUDGE");
    ResponseEntity<String> response = client.get("/api/v1/rounds/" + UUID.randomUUID());
    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void roundConfigIsEditableOnlyBeforeLive() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "round6@example.com", "Owner", "Round Org 6");
    String eventId = createEvent(client, orgId, "Round Event 6", "PHYSICAL_JUDGE");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");

    ResponseEntity<String> renamed =
        client.patch("/api/v1/rounds/" + roundId, Map.of("name", "Grand Final", "format", "BO1"));
    assertThat(renamed.getStatusCode().value()).isEqualTo(200);
    assertThat(json(renamed).get("name").asText()).isEqualTo("Grand Final");

    prepareReadyStart(client, roundId);

    ResponseEntity<String> renameAfterLive =
        client.patch("/api/v1/rounds/" + roundId, Map.of("name", "Too Late", "format", "BO1"));
    assertThat(renameAfterLive.getStatusCode().value()).isEqualTo(409);
  }
}

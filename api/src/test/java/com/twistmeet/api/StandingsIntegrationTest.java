package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 09 standings computed live from real attempt rows via the scoring engine (GET
 * /rounds/{roundId}/standings, row 54 of TRACEABILITY.md), and property P06: revisions recalculate
 * the result and downstream standings.
 */
class StandingsIntegrationTest extends AbstractIntegrationTest {

  @Test
  void standingsReflectAo5ScoringAndAreProvisionalUntilClosed() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "standings1@example.com", "Owner", "Standings Org");
    String eventId = createEvent(client, orgId, "Standings Event", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Solo");
    String roundId = createRound(client, eventId, 1, "Final", "AO5");
    prepareReadyStart(client, roundId);

    JsonNode attempts = json(client.get("/api/v1/rounds/" + roundId + "/attempts"));
    assertThat(attempts).hasSize(5);
    long[] times = {14210, 12840, 18910, 13050, 12100}; // 09 conformance vector V01
    for (int i = 0; i < attempts.size(); i++) {
      String attemptId = attempts.get(i).get("id").asText();
      client.put(
          "/api/v1/attempts/" + attemptId + "/judge-result",
          Map.of("status", "OK", "rawTimeMs", times[i], "expectedVersion", 0));
    }

    ResponseEntity<String> standingsBeforeClose =
        client.get("/api/v1/rounds/" + roundId + "/standings");
    JsonNode view = json(standingsBeforeClose);
    assertThat(view.get("provisional").asBoolean()).isTrue();
    JsonNode entrant = view.get("standings").get(0);
    assertThat(entrant.get("rank").asInt()).isEqualTo(1);
    assertThat(entrant.get("displayMs").asLong()).isEqualTo(13370); // 13.37s per V01

    client.post("/api/v1/rounds/" + roundId + "/review", null);
    client.post("/api/v1/rounds/" + roundId + "/close", null);
    ResponseEntity<String> standingsAfterClose =
        client.get("/api/v1/rounds/" + roundId + "/standings");
    assertThat(json(standingsAfterClose).get("provisional").asBoolean()).isFalse();
  }

  @Test
  void revisingAJudgeResultRecalculatesStandings() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "standings2@example.com", "Owner", "Standings Org 2");
    String eventId = createEvent(client, orgId, "Standings Event 2", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Solo");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);
    String attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();

    client.put(
        "/api/v1/attempts/" + attemptId + "/judge-result",
        Map.of("status", "OK", "rawTimeMs", 10000, "expectedVersion", 0));
    assertThat(
            json(client.get("/api/v1/rounds/" + roundId + "/standings"))
                .get("standings")
                .get(0)
                .get("displayMs")
                .asLong())
        .isEqualTo(10000);

    client.put(
        "/api/v1/attempts/" + attemptId + "/judge-result",
        Map.of("status", "OK", "rawTimeMs", 8000, "expectedVersion", 1));
    assertThat(
            json(client.get("/api/v1/rounds/" + roundId + "/standings"))
                .get("standings")
                .get(0)
                .get("displayMs")
                .asLong())
        .isEqualTo(8000);
  }

  @Test
  void standingsAreAuthorizedStaffOnlyNotCompetitor() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "standings3@example.com", "Owner", "Standings Org 3");
    String eventId = createEvent(client, orgId, "Standings Event 3", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Solo");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);

    ResponseEntity<String> guestStandings = guest.get("/api/v1/rounds/" + roundId + "/standings");
    assertThat(guestStandings.getStatusCode().value()).isEqualTo(401);
  }
}

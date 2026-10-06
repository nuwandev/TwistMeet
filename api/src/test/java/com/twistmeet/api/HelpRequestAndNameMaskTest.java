package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 07 P05 "Request judge/help" (distinct from a correction request) and S12 "Public name masking
 * option." Rows R42b/S12 of TRACEABILITY.md.
 */
class HelpRequestAndNameMaskTest extends AbstractIntegrationTest {

  @Test
  void competitorCanRequestHelpBeforeAResultExistsAndStaffCanResolveIt() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "help1@example.com", "Owner1", "Help Org 1");
    String eventId = createEvent(client, orgId, "Help Event 1", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Help Competitor 1");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);
    String attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();

    ResponseEntity<String> created =
        guest.post("/api/v1/attempts/" + attemptId + "/help-requests", null);
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    String helpRequestId = json(created).get("id").asText();

    // Idempotent: a second tap while still pending returns the same request, not a duplicate.
    ResponseEntity<String> again =
        guest.post("/api/v1/attempts/" + attemptId + "/help-requests", null);
    assertThat(json(again).get("id").asText()).isEqualTo(helpRequestId);

    JsonNode pending =
        json(client.get("/api/v1/events/" + eventId + "/help-requests?state=PENDING"));
    assertThat(pending).hasSize(1);

    ResponseEntity<String> resolved =
        client.post("/api/v1/help-requests/" + helpRequestId + "/resolve", null);
    assertThat(resolved.getStatusCode().value()).isEqualTo(200);
    assertThat(json(resolved).get("state").asText()).isEqualTo("RESOLVED");

    JsonNode pendingAfter =
        json(client.get("/api/v1/events/" + eventId + "/help-requests?state=PENDING"));
    assertThat(pendingAfter).hasSize(0);
  }

  @Test
  void helpRequestIsRejectedOnceAResultIsRecorded() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "help2@example.com", "Owner2", "Help Org 2");
    String eventId = createEvent(client, orgId, "Help Event 2", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Help Competitor 2");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);
    String attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();
    judgeOk(client, attemptId, 10000);

    ResponseEntity<String> created =
        guest.post("/api/v1/attempts/" + attemptId + "/help-requests", null);
    assertThat(created.getStatusCode().value()).isEqualTo(409);
  }

  @Test
  void onlyTheOwningCompetitorCanRequestHelpForTheirOwnAttempt() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "help3@example.com", "Owner3", "Help Org 3");
    String eventId = createEvent(client, orgId, "Help Event 3", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guestA = new TestApiClient(restTemplate);
    joinAsGuest(guestA, joinCode, "Help Competitor A3");
    TestApiClient guestB = new TestApiClient(restTemplate);
    joinAsGuest(guestB, joinCode, "Help Competitor B3");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);
    JsonNode attempts = json(client.get("/api/v1/rounds/" + roundId + "/attempts"));
    String attemptAId = attempts.get(0).get("id").asText();

    ResponseEntity<String> crossRequest =
        guestB.post("/api/v1/attempts/" + attemptAId + "/help-requests", null);
    assertThat(crossRequest.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void publicNameMaskHidesRealDisplayNames() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "mask1@example.com", "Owner1", "Mask Org 1");
    String eventId = createEvent(client, orgId, "Mask Event 1", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Real Name Competitor");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);
    String attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();
    judgeOk(client, attemptId, 10000);

    client.post("/api/v1/events/" + eventId + "/public-name-mask", Map.of("masked", true));
    String slug =
        json(client.post("/api/v1/events/" + eventId + "/publish", null))
            .get("publicSlug")
            .asText();

    TestApiClient anon = new TestApiClient(restTemplate);
    JsonNode standings =
        json(anon.get("/api/v1/public/events/" + slug + "/standings?roundId=" + roundId));
    JsonNode entry = standings.get("standings").get(0);
    assertThat(entry.get("displayName").asText()).isNotEqualTo("Real Name Competitor");
    assertThat(entry.get("displayName").asText()).startsWith("Competitor ");
  }
}

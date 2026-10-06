package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 00 §2 "Event create/edit... clone, archive and event history"; 00 §6 "Archived is read-only
 * except export/delete request processes"; 00 §5 "Require an explicit confirmation for... event
 * archive." Rows R30-R32 of TRACEABILITY.md.
 */
class EventArchiveCloneTest extends AbstractIntegrationTest {

  @Test
  void eventCompletesOnlyWhenEveryRoundIsClosed() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "arch1@example.com", "Owner1", "Arch Org 1");
    String eventId = createEvent(client, orgId, "Arch Event 1", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    guest.post("/api/v1/join/" + joinCode, Map.of("displayName", "Arch Competitor"));
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    client.post("/api/v1/rounds/" + roundId + "/prepare", null);

    // Round not closed yet -> complete rejected.
    ResponseEntity<String> tooEarly = client.post("/api/v1/events/" + eventId + "/complete", null);
    assertThat(tooEarly.getStatusCode().value()).isEqualTo(409);

    client.post("/api/v1/rounds/" + roundId + "/ready", null);
    client.post("/api/v1/rounds/" + roundId + "/start", null);
    JsonNode attempt = json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0);
    judgeOk(client, attempt.get("id").asText(), 10000);
    client.post("/api/v1/rounds/" + roundId + "/review", null);
    client.post("/api/v1/rounds/" + roundId + "/close", null);

    ResponseEntity<String> complete = client.post("/api/v1/events/" + eventId + "/complete", null);
    assertThat(complete.getStatusCode().value()).isEqualTo(200);
    assertThat(json(complete).get("state").asText()).isEqualTo("COMPLETED");

    // Reopening requires a reason and moves back to REGISTRATION_LOCKED, audited.
    ResponseEntity<String> reopen =
        client.post(
            "/api/v1/events/" + eventId + "/reopen", Map.of("reason", "Dispute over result"));
    assertThat(reopen.getStatusCode().value()).isEqualTo(200);
    assertThat(json(reopen).get("state").asText()).isEqualTo("REGISTRATION_LOCKED");

    JsonNode audit = json(client.get("/api/v1/events/" + eventId + "/audit"));
    boolean hasReopenReason =
        java.util.stream.StreamSupport.stream(audit.spliterator(), false)
            .anyMatch(
                e ->
                    "EVENT_REOPENED".equals(e.get("action").asText())
                        && "Dispute over result".equals(e.get("reason").asText()));
    assertThat(hasReopenReason).isTrue();
  }

  @Test
  void archivedEventIsReadOnlyExceptAllowedActions() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "arch2@example.com", "Owner2", "Arch Org 2");
    String eventId = createEvent(client, orgId, "Arch Event 2", "PHYSICAL_JUDGE");

    ResponseEntity<String> archived = client.post("/api/v1/events/" + eventId + "/archive", null);
    assertThat(archived.getStatusCode().value()).isEqualTo(200);
    assertThat(json(archived).get("state").asText()).isEqualTo("ARCHIVED");

    // Every mutation is rejected now.
    assertThat(
            client
                .post("/api/v1/events/" + eventId + "/registration/open", null)
                .getStatusCode()
                .value())
        .isEqualTo(409);
    assertThat(
            client
                .post(
                    "/api/v1/events/" + eventId + "/rounds",
                    Map.of("order", 1, "name", "Final", "format", "BO1"))
                .getStatusCode()
                .value())
        .isEqualTo(409);
    assertThat(
            client
                .patch(
                    "/api/v1/events/" + eventId,
                    Map.of("name", "Renamed", "description", "", "venueLabel", ""))
                .getStatusCode()
                .value())
        .isEqualTo(409);

    // Read access (history/export) remains available — archive is read-only, not inaccessible.
    assertThat(client.get("/api/v1/events/" + eventId).getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void cloneCopiesConfigNotEntrantsOrRounds() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "arch3@example.com", "Owner3", "Arch Org 3");
    String eventId = createEvent(client, orgId, "Arch Event 3", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    guest.post("/api/v1/join/" + joinCode, Map.of("displayName", "Arch Competitor 3"));
    createRound(client, eventId, 1, "Final", "BO1");

    ResponseEntity<String> cloneResponse =
        client.post("/api/v1/events/" + eventId + "/clone", null);
    assertThat(cloneResponse.getStatusCode().value()).isEqualTo(201);
    JsonNode cloned = json(cloneResponse);
    assertThat(cloned.get("name").asText()).isEqualTo("Arch Event 3 (copy)");
    assertThat(cloned.get("state").asText()).isEqualTo("DRAFT");
    String clonedEventId = cloned.get("id").asText();
    assertThat(clonedEventId).isNotEqualTo(eventId);

    assertThat(json(client.get("/api/v1/events/" + clonedEventId + "/entrants"))).hasSize(0);
    assertThat(json(client.get("/api/v1/events/" + clonedEventId + "/rounds"))).hasSize(0);
  }

  @Test
  void onlyOrganizerCanCompleteReopenOrArchiveNotAStranger() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "arch4@example.com", "Owner4", "Arch Org 4");
    String eventId = createEvent(client, orgId, "Arch Event 4", "PHYSICAL_JUDGE");

    TestApiClient stranger = new TestApiClient(restTemplate);
    registerAndVerify(stranger, "arch4stranger@example.com", "Stranger4", "correct-horse-battery");
    assertThat(
            stranger.post("/api/v1/events/" + eventId + "/archive", null).getStatusCode().value())
        .isEqualTo(404);
    assertThat(
            stranger.post("/api/v1/events/" + eventId + "/complete", null).getStatusCode().value())
        .isEqualTo(404);
  }
}

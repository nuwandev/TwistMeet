package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 00 §2.4 roster management: add/edit/check-in/remove-before-start/withdraw-after-start, no silent
 * duplicate merge. Row 24-29 of TRACEABILITY.md.
 */
class RosterManagementTest extends AbstractIntegrationTest {

  @Test
  void organizerCanAddEditCheckInAndRemoveAnEntrantBeforeAnyAttemptExists() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "roster1@example.com", "Owner", "Roster Org");
    String eventId = createEvent(client, orgId, "Roster Event", "PHYSICAL_JUDGE");

    ResponseEntity<String> added =
        client.post("/api/v1/events/" + eventId + "/entrants", Map.of("displayName", "Alex"));
    assertThat(added.getStatusCode().value()).isEqualTo(201);
    JsonNode addedBody = json(added);
    String entrantId = addedBody.get("id").asText();
    assertThat(addedBody.get("checkInState").asText()).isEqualTo("NOT_CHECKED_IN");

    ResponseEntity<String> edited =
        client.patch(
            "/api/v1/events/" + eventId + "/entrants/" + entrantId,
            Map.of("displayName", "Alexandra"));
    assertThat(edited.getStatusCode().value()).isEqualTo(200);
    assertThat(json(edited).get("displayName").asText()).isEqualTo("Alexandra");

    ResponseEntity<String> checkedIn =
        client.post("/api/v1/events/" + eventId + "/entrants/" + entrantId + "/check-in", null);
    assertThat(checkedIn.getStatusCode().value()).isEqualTo(200);
    assertThat(json(checkedIn).get("checkInState").asText()).isEqualTo("CHECKED_IN");

    ResponseEntity<String> removed =
        client.delete("/api/v1/events/" + eventId + "/entrants/" + entrantId);
    assertThat(removed.getStatusCode().value()).isEqualTo(200);
    assertThat(json(removed).get("displayName").asText()).isEqualTo("Alexandra");

    ResponseEntity<String> roster = client.get("/api/v1/events/" + eventId + "/entrants");
    assertThat(json(roster)).isEmpty();
  }

  @Test
  void manualAddDisambiguatesDuplicateDisplayNamesLikeGuestJoinDoes() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "roster2@example.com", "Owner", "Roster Org 2");
    String eventId = createEvent(client, orgId, "Roster Event 2", "PHYSICAL_JUDGE");

    client.post("/api/v1/events/" + eventId + "/entrants", Map.of("displayName", "Sam"));
    ResponseEntity<String> second =
        client.post("/api/v1/events/" + eventId + "/entrants", Map.of("displayName", "Sam"));
    assertThat(json(second).get("displayName").asText()).isEqualTo("Sam (2)");
  }

  @Test
  void removingAnEntrantWithAttemptsIsBlockedAndWithdrawIsUsedInstead() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "roster3@example.com", "Owner", "Roster Org 3");
    String eventId = createEvent(client, orgId, "Roster Event 3", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);

    TestApiClient guest = new TestApiClient(restTemplate);
    String entrantId = joinAsGuest(guest, joinCode, "Taylor");

    String roundId = createRound(client, eventId, 1, "Final", "AO5");
    prepareReadyStart(client, roundId);

    ResponseEntity<String> removeAttempt =
        client.delete("/api/v1/events/" + eventId + "/entrants/" + entrantId);
    assertThat(removeAttempt.getStatusCode().value()).isEqualTo(409);

    ResponseEntity<String> withdraw =
        client.post(
            "/api/v1/events/" + eventId + "/entrants/" + entrantId + "/withdraw",
            Map.of("reason", "Left early"));
    assertThat(withdraw.getStatusCode().value()).isEqualTo(200);
    assertThat(json(withdraw).get("status").asText()).isEqualTo("WITHDRAWN");

    // The entrant row (and its attempt history) still exists — withdraw never hard-deletes.
    ResponseEntity<String> roster = client.get("/api/v1/events/" + eventId + "/entrants");
    assertThat(json(roster)).hasSize(1);
  }

  @Test
  void guestCanRenameThemselvesBeforeAttemptsStartButNotAfter() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "roster4@example.com", "Owner", "Roster Org 4");
    String eventId = createEvent(client, orgId, "Roster Event 4", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);

    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Jordan");

    ResponseEntity<String> renamed =
        guest.patch("/api/v1/guest/events/" + eventId + "/me", Map.of("displayName", "J"));
    assertThat(renamed.getStatusCode().value()).isEqualTo(200);
    assertThat(json(renamed).get("displayName").asText()).isEqualTo("J");

    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);

    ResponseEntity<String> me = guest.get("/api/v1/guest/events/" + eventId + "/me");
    String entrantId = json(me).get("id").asText();

    // Record a judge result so the attempt is no longer PENDING.
    ResponseEntity<String> attemptsList = client.get("/api/v1/rounds/" + roundId + "/attempts");
    String attemptId = json(attemptsList).get(0).get("id").asText();
    client.put(
        "/api/v1/attempts/" + attemptId + "/judge-result",
        Map.of("status", "OK", "rawTimeMs", 12000, "expectedVersion", 0));

    ResponseEntity<String> renameAfterStart =
        guest.patch("/api/v1/guest/events/" + eventId + "/me", Map.of("displayName", "Jordan2"));
    assertThat(renameAfterStart.getStatusCode().value()).isEqualTo(409);
  }
}

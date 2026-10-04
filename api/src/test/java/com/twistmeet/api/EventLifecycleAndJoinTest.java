package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class EventLifecycleAndJoinTest extends AbstractIntegrationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void draftEventIsNotJoinable() throws Exception {
    String orgId =
        registerLoginAndCreateOrg(client, "draftorg@example.com", "Draft Owner", "Draft Org");
    JsonNode event = createEvent(client, orgId, "Draft Event");
    String joinCode = event.get("joinCode").asText();

    TestApiClient guest = new TestApiClient(restTemplate);
    ResponseEntity<String> joinResponse =
        guest.post("/api/v1/join/" + joinCode, Map.of("displayName", "Guest"));
    assertThat(joinResponse.getStatusCode().value()).isEqualTo(404);
    assertThat(joinResponse.getBody()).contains("JOIN_CODE_INVALID");
  }

  @Test
  void openRegistrationAllowsJoinWithDuplicateNameDisambiguation() throws Exception {
    String orgId =
        registerLoginAndCreateOrg(client, "openorg@example.com", "Open Owner", "Open Org");
    JsonNode event = createEvent(client, orgId, "Open Event");
    String eventId = event.get("id").asText();
    String joinCode = event.get("joinCode").asText();

    ResponseEntity<String> openResponse =
        client.post("/api/v1/events/" + eventId + "/registration/open", null);
    assertThat(openResponse.getStatusCode().value()).isEqualTo(200);

    TestApiClient guest1 = new TestApiClient(restTemplate);
    ResponseEntity<String> join1 =
        guest1.post("/api/v1/join/" + joinCode, Map.of("displayName", "Sam"));
    assertThat(join1.getStatusCode().value()).isEqualTo(200);
    JsonNode join1Body = objectMapper.readTree(join1.getBody());
    assertThat(join1Body.get("entrant").get("displayName").asText()).isEqualTo("Sam");

    TestApiClient guest2 = new TestApiClient(restTemplate);
    ResponseEntity<String> join2 =
        guest2.post("/api/v1/join/" + joinCode, Map.of("displayName", "Sam"));
    assertThat(join2.getStatusCode().value()).isEqualTo(200);
    JsonNode join2Body = objectMapper.readTree(join2.getBody());
    assertThat(join2Body.get("entrant").get("displayName").asText()).isEqualTo("Sam (2)");

    // Each guest can read only its own entrant record via the event-scoped credential cookie.
    ResponseEntity<String> guest1Me = guest1.get("/api/v1/guest/events/" + eventId + "/me");
    assertThat(guest1Me.getStatusCode().value()).isEqualTo(200);
    assertThat(objectMapper.readTree(guest1Me.getBody()).get("displayName").asText())
        .isEqualTo("Sam");

    // Organizer can see both entrants on the roster.
    ResponseEntity<String> roster = client.get("/api/v1/events/" + eventId + "/entrants");
    assertThat(roster.getStatusCode().value()).isEqualTo(200);
    assertThat(objectMapper.readTree(roster.getBody())).hasSize(2);
  }

  @Test
  void lockedRegistrationRejectsNewJoinsButExistingGuestsStillWork() throws Exception {
    String orgId =
        registerLoginAndCreateOrg(client, "lockorg@example.com", "Lock Owner", "Lock Org");
    JsonNode event = createEvent(client, orgId, "Lock Event");
    String eventId = event.get("id").asText();
    String joinCode = event.get("joinCode").asText();
    client.post("/api/v1/events/" + eventId + "/registration/open", null);

    TestApiClient earlyGuest = new TestApiClient(restTemplate);
    earlyGuest.post("/api/v1/join/" + joinCode, Map.of("displayName", "Early"));

    ResponseEntity<String> lockResponse =
        client.post("/api/v1/events/" + eventId + "/registration/lock", null);
    assertThat(lockResponse.getStatusCode().value()).isEqualTo(200);

    TestApiClient lateGuest = new TestApiClient(restTemplate);
    ResponseEntity<String> lateJoin =
        lateGuest.post("/api/v1/join/" + joinCode, Map.of("displayName", "Late"));
    assertThat(lateJoin.getStatusCode().value()).isEqualTo(404);

    // The guest who joined before the lock can still read their own status.
    ResponseEntity<String> earlyMe = earlyGuest.get("/api/v1/guest/events/" + eventId + "/me");
    assertThat(earlyMe.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void guestCredentialDoesNotCrossEventBoundary() throws Exception {
    String orgId =
        registerLoginAndCreateOrg(
            client, "boundaryorg@example.com", "Boundary Owner", "Boundary Org");

    JsonNode eventOne = createEvent(client, orgId, "Event One");
    String eventOneId = eventOne.get("id").asText();
    client.post("/api/v1/events/" + eventOneId + "/registration/open", null);

    JsonNode eventTwo = createEvent(client, orgId, "Event Two");
    String eventTwoId = eventTwo.get("id").asText();
    client.post("/api/v1/events/" + eventTwoId + "/registration/open", null);

    TestApiClient guest = new TestApiClient(restTemplate);
    guest.post("/api/v1/join/" + eventOne.get("joinCode").asText(), Map.of("displayName", "Cross"));

    // The credential issued for event one must not resolve against event two.
    ResponseEntity<String> crossRequest = guest.get("/api/v1/guest/events/" + eventTwoId + "/me");
    assertThat(crossRequest.getStatusCode().value()).isEqualTo(404);

    // It still works for the event it was actually issued for.
    ResponseEntity<String> ownRequest = guest.get("/api/v1/guest/events/" + eventOneId + "/me");
    assertThat(ownRequest.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void joinCodeRotationInvalidatesOldCode() throws Exception {
    String orgId =
        registerLoginAndCreateOrg(client, "rotateorg@example.com", "Rotate Owner", "Rotate Org");
    JsonNode event = createEvent(client, orgId, "Rotate Event");
    String eventId = event.get("id").asText();
    String oldCode = event.get("joinCode").asText();
    client.post("/api/v1/events/" + eventId + "/registration/open", null);

    ResponseEntity<String> rotateResponse =
        client.post("/api/v1/events/" + eventId + "/join-codes/rotate", null);
    assertThat(rotateResponse.getStatusCode().value()).isEqualTo(200);
    String newCode = objectMapper.readTree(rotateResponse.getBody()).get("joinCode").asText();
    assertThat(newCode).isNotEqualTo(oldCode);

    TestApiClient guest = new TestApiClient(restTemplate);
    ResponseEntity<String> joinWithOldCode =
        guest.post("/api/v1/join/" + oldCode, Map.of("displayName", "Stale"));
    assertThat(joinWithOldCode.getStatusCode().value()).isEqualTo(404);

    ResponseEntity<String> joinWithNewCode =
        guest.post("/api/v1/join/" + newCode, Map.of("displayName", "Fresh"));
    assertThat(joinWithNewCode.getStatusCode().value()).isEqualTo(200);
  }

  private String registerLoginAndCreateOrg(
      TestApiClient apiClient, String email, String displayName, String orgName) throws Exception {
    registerAndVerify(apiClient, email, displayName, "correct-horse-battery");
    ResponseEntity<String> orgResponse =
        apiClient.post(
            "/api/v1/organizations",
            Map.of("name", orgName, "defaultTimezone", "America/Los_Angeles"));
    return objectMapper.readTree(orgResponse.getBody()).get("id").asText();
  }

  private JsonNode createEvent(TestApiClient apiClient, String orgId, String eventName)
      throws Exception {
    ResponseEntity<String> eventResponse =
        apiClient.post(
            "/api/v1/organizations/" + orgId + "/events",
            Map.of(
                "name", eventName,
                "description", "",
                "startsAt", Instant.now().plusSeconds(3600).toString(),
                "timezone", "America/Los_Angeles",
                "venueLabel", "",
                "visibility", "PRIVATE"));
    return objectMapper.readTree(eventResponse.getBody());
  }
}

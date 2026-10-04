package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Roster visibility is organization-staff-only (00 §5 Spectator/Competitor cannot see rosters). */
class RosterAuthorizationTest extends AbstractIntegrationTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Test
  void onlyOrganizationStaffCanReadTheRoster() throws Exception {
    TestApiClient organizer = client;
    registerAndVerify(organizer, "roster-owner@example.com", "Owner", "correct-horse-battery");
    ResponseEntity<String> orgResponse =
        organizer.post(
            "/api/v1/organizations",
            Map.of("name", "Roster Org", "defaultTimezone", "America/Los_Angeles"));
    String orgId = objectMapper.readTree(orgResponse.getBody()).get("id").asText();

    ResponseEntity<String> eventResponse =
        organizer.post(
            "/api/v1/organizations/" + orgId + "/events",
            Map.of(
                "name", "Roster Event",
                "description", "",
                "startsAt", Instant.now().plusSeconds(3600).toString(),
                "timezone", "America/Los_Angeles",
                "venueLabel", "",
                "visibility", "PRIVATE"));
    JsonNode event = objectMapper.readTree(eventResponse.getBody());
    String eventId = event.get("id").asText();
    organizer.post("/api/v1/events/" + eventId + "/registration/open", null);

    TestApiClient guest = new TestApiClient(restTemplate);
    guest.post("/api/v1/join/" + event.get("joinCode").asText(), Map.of("displayName", "Rostered"));

    // Organizer can read the roster.
    ResponseEntity<String> rosterAsOrganizer =
        organizer.get("/api/v1/events/" + eventId + "/entrants");
    assertThat(rosterAsOrganizer.getStatusCode().value()).isEqualTo(200);
    assertThat(objectMapper.readTree(rosterAsOrganizer.getBody())).hasSize(1);

    // A guest (no staff session at all) cannot read the roster; unauthenticated staff endpoint.
    ResponseEntity<String> rosterAsGuest = guest.get("/api/v1/events/" + eventId + "/entrants");
    assertThat(rosterAsGuest.getStatusCode().value()).isEqualTo(401);

    // A staff user who is not a member of this organization cannot read the roster either.
    TestApiClient outsider = new TestApiClient(restTemplate);
    registerAndVerify(outsider, "roster-outsider@example.com", "Outsider", "correct-horse-battery");
    ResponseEntity<String> rosterAsOutsider =
        outsider.get("/api/v1/events/" + eventId + "/entrants");
    assertThat(rosterAsOutsider.getStatusCode().value()).isEqualTo(404);
  }
}

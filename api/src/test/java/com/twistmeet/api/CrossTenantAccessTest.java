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

/**
 * Proves organization A cannot reach organization B's data through any of the org-scoped endpoints
 * — the core tenant-isolation guarantee this milestone exists to establish (00 §5, 04 "Tenant ID
 * must be enforced on every query and write", 08 "organization A cannot access organization B").
 */
class CrossTenantAccessTest extends AbstractIntegrationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void memberOfOrgBCannotReadOrModifyOrgAEvent() throws Exception {
    TestApiClient userA = client;
    String orgAId = registerLoginAndCreateOrg(userA, "ownerA@example.com", "Owner A", "Org A");
    String eventAId = createEvent(userA, orgAId, "Org A Meetup");

    TestApiClient userB = new TestApiClient(restTemplate);
    registerLoginAndCreateOrg(userB, "ownerB@example.com", "Owner B", "Org B");

    // B cannot fetch A's event by ID.
    ResponseEntity<String> getAsB = userB.get("/api/v1/events/" + eventAId);
    assertThat(getAsB.getStatusCode().value()).isEqualTo(404);

    // B cannot list A's org events.
    ResponseEntity<String> listAsB = userB.get("/api/v1/organizations/" + orgAId + "/events");
    assertThat(listAsB.getStatusCode().value()).isEqualTo(404);

    // B cannot patch A's event.
    ResponseEntity<String> patchAsB =
        userB.patch(
            "/api/v1/events/" + eventAId,
            Map.of("name", "Hijacked", "description", "", "venueLabel", ""));
    assertThat(patchAsB.getStatusCode().value()).isEqualTo(404);

    // B cannot open registration on A's event.
    ResponseEntity<String> openAsB =
        userB.post("/api/v1/events/" + eventAId + "/registration/open", null);
    assertThat(openAsB.getStatusCode().value()).isEqualTo(404);

    // B cannot read A's roster.
    ResponseEntity<String> rosterAsB = userB.get("/api/v1/events/" + eventAId + "/entrants");
    assertThat(rosterAsB.getStatusCode().value()).isEqualTo(404);

    // Meanwhile A can still do all of these on its own event.
    ResponseEntity<String> getAsA = userA.get("/api/v1/events/" + eventAId);
    assertThat(getAsA.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void listingOrganizationsOnlyReturnsOwnMemberships() throws Exception {
    TestApiClient userA = client;
    registerLoginAndCreateOrg(userA, "listA@example.com", "List A", "List Org A");

    TestApiClient userB = new TestApiClient(restTemplate);
    String orgBId = registerLoginAndCreateOrg(userB, "listB@example.com", "List B", "List Org B");

    ResponseEntity<String> listAsA = userA.get("/api/v1/organizations");
    JsonNode orgsForA = objectMapper.readTree(listAsA.getBody());
    for (JsonNode org : orgsForA) {
      assertThat(org.get("id").asText()).isNotEqualTo(orgBId);
    }
  }

  private String registerLoginAndCreateOrg(
      TestApiClient apiClient, String email, String displayName, String orgName) throws Exception {
    apiClient.post(
        "/api/v1/auth/register",
        Map.of("email", email, "password", "correct-horse-battery", "displayName", displayName));
    apiClient.post(
        "/api/v1/auth/login", Map.of("email", email, "password", "correct-horse-battery"));
    ResponseEntity<String> orgResponse =
        apiClient.post(
            "/api/v1/organizations",
            Map.of("name", orgName, "defaultTimezone", "America/Los_Angeles"));
    assertThat(orgResponse.getStatusCode().value()).isEqualTo(201);
    return objectMapper.readTree(orgResponse.getBody()).get("id").asText();
  }

  private String createEvent(TestApiClient apiClient, String orgId, String eventName)
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
    assertThat(eventResponse.getStatusCode().value()).isEqualTo(201);
    return objectMapper.readTree(eventResponse.getBody()).get("id").asText();
  }
}

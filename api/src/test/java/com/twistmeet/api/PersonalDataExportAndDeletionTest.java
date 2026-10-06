package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 04 "Give organizers export and deletion processes." Policy-independent mechanism only: a
 * self-service export of everything a person can already see about themselves, and an idempotent
 * deletion-request flag that performs no automated deletion (see DATA_RETENTION_DECISIONS.md for
 * the still-open retention policy).
 */
class PersonalDataExportAndDeletionTest extends AbstractIntegrationTest {

  @Test
  void staffMemberCanExportOwnProfileOrgMembershipsAndEventAssignments() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "privacy1@example.com", "Owner1", "Privacy Org 1");
    String eventId = createEvent(client, orgId, "Privacy Event 1", "PHYSICAL_JUDGE");

    ResponseEntity<String> exportResponse = client.get("/api/v1/me/export");
    assertThat(exportResponse.getStatusCode().value()).isEqualTo(200);
    JsonNode export = json(exportResponse);
    assertThat(export.get("profile").get("email").asText()).isEqualTo("privacy1@example.com");
    assertThat(export.get("organizationMemberships")).hasSize(1);
    assertThat(export.get("organizationMemberships").get(0).get("organizationId").asText())
        .isEqualTo(orgId);
    assertThat(export.get("organizationMemberships").get(0).get("organizationName").asText())
        .isEqualTo("Privacy Org 1");
    // Owner created the org, not assigned event staff directly, so no event-scoped assignments yet.
    assertThat(export.get("eventStaffAssignments")).hasSize(0);
    assertThat(export.has("exportedAt")).isTrue();
  }

  @Test
  void deletionRequestIsIdempotentAndNeverDeletesAnything() throws Exception {
    registerVerifyAndCreateOrg(client, "privacy2@example.com", "Owner2", "Privacy Org 2");

    ResponseEntity<String> first = client.post("/api/v1/me/deletion-request", null);
    assertThat(first.getStatusCode().value()).isEqualTo(200);
    assertThat(json(first).get("deletionRequestedAt").asText()).isNotNull();

    // Compare two post-persist reads (not the first write's in-memory, pre-round-trip value
    // against a later one) since Postgres TIMESTAMPTZ rounds to microsecond precision on write —
    // comparing a full-nanosecond in-memory Instant against a rounded one is flaky.
    String afterFirstRequest = json(client.get("/api/v1/me")).get("deletionRequestedAt").asText();

    ResponseEntity<String> second = client.post("/api/v1/me/deletion-request", null);
    assertThat(json(second).get("deletionRequestedAt").asText()).isEqualTo(afterFirstRequest);

    // The account is untouched: still able to export data and read /me normally.
    ResponseEntity<String> me = client.get("/api/v1/me");
    assertThat(me.getStatusCode().value()).isEqualTo(200);
    assertThat(json(me).get("email").asText()).isEqualTo("privacy2@example.com");
  }

  @Test
  void guestCompetitorCanExportOwnEntrantAttemptsAndCorrectionsOnly() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "privacy3@example.com", "Owner3", "Privacy Org 3");
    String eventId = createEvent(client, orgId, "Privacy Event 3", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guestA = new TestApiClient(restTemplate);
    joinAsGuest(guestA, joinCode, "Privacy Competitor A3");
    TestApiClient guestB = new TestApiClient(restTemplate);
    joinAsGuest(guestB, joinCode, "Privacy Competitor B3");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);
    JsonNode attempts = json(client.get("/api/v1/rounds/" + roundId + "/attempts"));
    String attemptAId = attempts.get(0).get("id").asText();
    judgeOk(client, attemptAId, 10000);
    ResponseEntity<String> correctionResponse =
        guestA.post(
            "/api/v1/attempts/" + attemptAId + "/correction-requests",
            java.util.Map.of("category", "TIMER_OR_ENTRY_ISSUE", "note", "typo"));
    assertThat(correctionResponse.getStatusCode().value()).isEqualTo(201);

    ResponseEntity<String> exportResponse =
        guestA.get("/api/v1/guest/events/" + eventId + "/me/export");
    assertThat(exportResponse.getStatusCode().value()).isEqualTo(200);
    JsonNode export = json(exportResponse);
    assertThat(export.get("entrant").get("displayName").asText())
        .isEqualTo("Privacy Competitor A3");
    assertThat(export.get("attempts")).hasSize(1);
    assertThat(export.get("attempts").get(0).get("id").asText()).isEqualTo(attemptAId);
    assertThat(export.get("correctionsFiled")).hasSize(1);
    assertThat(export.get("helpRequestsFiled")).hasSize(0);

    // Guest B's export never includes guest A's data.
    ResponseEntity<String> exportB = guestB.get("/api/v1/guest/events/" + eventId + "/me/export");
    JsonNode exportBJson = json(exportB);
    assertThat(exportBJson.get("attempts")).hasSize(1);
    assertThat(exportBJson.get("attempts").get(0).get("id").asText()).isNotEqualTo(attemptAId);
    assertThat(exportBJson.get("correctionsFiled")).hasSize(0);
  }
}

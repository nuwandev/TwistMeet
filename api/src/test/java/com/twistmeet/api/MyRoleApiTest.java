package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * 00 §5/§9 "Display role and event scope visibly on staff pages": GET /events/{eventId}/my-role
 * backs the web RoleBanner component. A judge with no organization membership must still be able to
 * discover their own role, since every other organizer-scoped read 404s for them.
 */
class MyRoleApiTest extends AbstractIntegrationTest {

  @Test
  void ownerAndOrganizerSeeTheirOwnRoleAndAStrangerGets404() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "role1@example.com", "Owner", "Role Org");
    String eventId = createEvent(client, orgId, "Role Event", "PHYSICAL_JUDGE");

    ResponseEntity<String> ownerRole = client.get("/api/v1/events/" + eventId + "/my-role");
    assertThat(ownerRole.getStatusCode().value()).isEqualTo(200);
    assertThat(json(ownerRole).get("role").asText()).isEqualTo("OWNER");

    TestApiClient stranger = new TestApiClient(restTemplate);
    registerAndVerify(stranger, "strangerrole@example.com", "Stranger", "correct-horse-battery");
    ResponseEntity<String> strangerRole = stranger.get("/api/v1/events/" + eventId + "/my-role");
    assertThat(strangerRole.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void judgeWithNoOrgMembershipCanSeeOwnRoleButNotOrganizerOnlyData() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "role2@example.com", "Owner", "Role Org 2");
    String eventId = createEvent(client, orgId, "Role Event 2", "PHYSICAL_JUDGE");

    TestApiClient judgeClient = new TestApiClient(restTemplate);
    ResponseEntity<String> judgeVerify =
        registerAndVerify(judgeClient, "judgerole@example.com", "Judge", "correct-horse-battery");
    String judgeUserId = json(judgeVerify).get("id").asText();
    client.post(
        "/api/v1/events/" + eventId + "/staff-assignments",
        Map.of("userId", judgeUserId, "role", "JUDGE"));

    ResponseEntity<String> judgeRole = judgeClient.get("/api/v1/events/" + eventId + "/my-role");
    assertThat(judgeRole.getStatusCode().value()).isEqualTo(200);
    assertThat(json(judgeRole).get("role").asText()).isEqualTo("JUDGE");

    // The judge is a recognized actor for this event but still can't read organizer-only data.
    ResponseEntity<String> judgeEventGet = judgeClient.get("/api/v1/events/" + eventId);
    assertThat(judgeEventGet.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void nonexistentEventIs404ForMyRole() throws Exception {
    registerVerifyAndCreateOrg(client, "role3@example.com", "Owner", "Role Org 3");
    ResponseEntity<String> response =
        client.get("/api/v1/events/" + UUID.randomUUID() + "/my-role");
    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }
}

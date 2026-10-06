package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 02 "Corrections and extra attempts". Rows 51-53 of TRACEABILITY.md. Decisions are Organizer-only
 * (see CorrectionService's class comment on the 00/08-vs-02 conflict, recorded in DECISIONS.md).
 */
class CorrectionFlowTest extends AbstractIntegrationTest {

  private String eventId;
  private String roundId;
  private String attemptId;
  private TestApiClient guest;

  private void setUpJudgedAttempt(String emailSuffix) throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(
            client, "correction" + emailSuffix + "@example.com", "Owner", "C Org " + emailSuffix);
    eventId = createEvent(client, orgId, "C Event " + emailSuffix, "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Competitor");
    roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);
    attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();
    client.put(
        "/api/v1/attempts/" + attemptId + "/judge-result",
        Map.of("status", "OK", "rawTimeMs", 12000, "expectedVersion", 0));
  }

  @Test
  void competitorCanRequestACorrectionOnlyForTheirOwnAttempt() throws Exception {
    setUpJudgedAttempt("1");
    ResponseEntity<String> ownRequest =
        guest.post(
            "/api/v1/attempts/" + attemptId + "/correction-requests",
            Map.of("category", "TIMER_OR_ENTRY_ISSUE", "note", "Timer glitched"));
    assertThat(ownRequest.getStatusCode().value()).isEqualTo(201);
    assertThat(json(ownRequest).get("state").asText()).isEqualTo("PENDING");

    TestApiClient otherGuest = new TestApiClient(restTemplate);
    String joinCode = json(client.get("/api/v1/events/" + eventId)).get("joinCode").asText();
    joinAsGuest(otherGuest, joinCode, "Someone Else");
    ResponseEntity<String> othersRequest =
        otherGuest.post(
            "/api/v1/attempts/" + attemptId + "/correction-requests",
            Map.of("category", "OTHER", "note", "Not mine"));
    assertThat(othersRequest.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void acceptNoRetryVoidsTheAttemptButPreservesTheOriginalValueInHistory() throws Exception {
    setUpJudgedAttempt("2");
    ResponseEntity<String> request =
        guest.post(
            "/api/v1/attempts/" + attemptId + "/correction-requests",
            Map.of("category", "SCRAMBLE_CONCERN", "note", "Pre-scrambled"));
    JsonNode correction = json(request);
    String correctionId = correction.get("id").asText();

    ResponseEntity<String> decided =
        client.post(
            "/api/v1/corrections/" + correctionId + "/decision",
            Map.of("action", "ACCEPT_NO_RETRY", "reason", "Confirmed issue", "expectedVersion", 0));
    assertThat(decided.getStatusCode().value()).isEqualTo(200);
    assertThat(json(decided).get("state").asText()).isEqualTo("DECIDED");

    ResponseEntity<String> attempt = client.get("/api/v1/attempts/" + attemptId);
    assertThat(json(attempt).get("state").asText()).isEqualTo("VOIDED");
    assertThat(json(attempt).get("resultStatus").asText()).isEqualTo("VOID");
    // Original raw time preserved in the attempt row itself, and in the revision history.
    assertThat(json(attempt).get("rawTimeMs").asLong()).isEqualTo(12000);
    ResponseEntity<String> revisions = client.get("/api/v1/attempts/" + attemptId + "/revisions");
    JsonNode last = json(revisions).get(json(revisions).size() - 1);
    assertThat(last.get("newResultStatus").asText()).isEqualTo("VOID");
    assertThat(last.get("previousRawTimeMs").asLong()).isEqualTo(12000);

    // Deciding an already-decided correction is rejected, not silently re-applied.
    ResponseEntity<String> redecide =
        client.post(
            "/api/v1/corrections/" + correctionId + "/decision",
            Map.of("action", "REJECT", "reason", "n/a", "expectedVersion", 1));
    assertThat(redecide.getStatusCode().value()).isEqualTo(409);
  }

  @Test
  void acceptRetryVoidsAndCreatesAReplacementAttempt() throws Exception {
    setUpJudgedAttempt("3");
    ResponseEntity<String> request =
        guest.post(
            "/api/v1/attempts/" + attemptId + "/correction-requests",
            Map.of("category", "INTERRUPTION", "note", "Fire alarm"));
    String correctionId = json(request).get("id").asText();

    client.post(
        "/api/v1/corrections/" + correctionId + "/decision",
        Map.of("action", "ACCEPT_RETRY", "reason", "Clearly interrupted", "expectedVersion", 0));

    ResponseEntity<String> attempts = client.get("/api/v1/rounds/" + roundId + "/attempts");
    JsonNode attemptsJson = json(attempts);
    assertThat(attemptsJson).hasSize(2);
    boolean hasReplacementPending =
        attemptsJson.get(0).get("state").asText().equals("PENDING")
            || attemptsJson.get(1).get("state").asText().equals("PENDING");
    assertThat(hasReplacementPending).isTrue();
  }

  @Test
  void onlyOrganizerCanDecideACorrectionNotJudgeOrStranger() throws Exception {
    setUpJudgedAttempt("4");
    ResponseEntity<String> request =
        guest.post(
            "/api/v1/attempts/" + attemptId + "/correction-requests",
            Map.of("category", "OTHER", "note", "n/a"));
    String correctionId = json(request).get("id").asText();

    TestApiClient judgeClient = new TestApiClient(restTemplate);
    ResponseEntity<String> judgeVerify =
        registerAndVerify(judgeClient, "judge4@example.com", "Judge", "correct-horse-battery");
    String judgeUserId = json(judgeVerify).get("id").asText();
    client.post(
        "/api/v1/events/" + eventId + "/staff-assignments",
        Map.of("userId", judgeUserId, "role", "JUDGE"));

    ResponseEntity<String> judgeDecision =
        judgeClient.post(
            "/api/v1/corrections/" + correctionId + "/decision",
            Map.of("action", "REJECT", "reason", "n/a", "expectedVersion", 0));
    assertThat(judgeDecision.getStatusCode().value()).isEqualTo(403);

    // But the judge CAN see the pending correction in the list (organizer/judge authorized).
    ResponseEntity<String> judgeList =
        judgeClient.get("/api/v1/events/" + eventId + "/corrections");
    assertThat(judgeList.getStatusCode().value()).isEqualTo(200);
    assertThat(json(judgeList)).hasSize(1);
  }

  @Test
  void decisionRequiresANonBlankReason() throws Exception {
    setUpJudgedAttempt("5");
    ResponseEntity<String> request =
        guest.post(
            "/api/v1/attempts/" + attemptId + "/correction-requests",
            Map.of("category", "OTHER", "note", "n/a"));
    String correctionId = json(request).get("id").asText();

    ResponseEntity<String> blankReason =
        client.post(
            "/api/v1/corrections/" + correctionId + "/decision",
            Map.of("action", "REJECT", "reason", "", "expectedVersion", 0));
    assertThat(blankReason.getStatusCode().value()).isEqualTo(400);

    ResponseEntity<String> missingReason =
        client.post(
            "/api/v1/corrections/" + correctionId + "/decision",
            Map.of("action", "REJECT", "expectedVersion", 0));
    assertThat(missingReason.getStatusCode().value()).isEqualTo(400);
  }
}

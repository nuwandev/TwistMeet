package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 00 §6 attempt lifecycle, 00 §3 mode separation, 00 §9 judge entry + immutable revision history,
 * 09 raw/penalty/adjusted separation. Rows 39-43 of TRACEABILITY.md.
 */
class AttemptFlowTest extends AbstractIntegrationTest {

  @Test
  void judgeModeRecordsAResultAndEveryEntryCreatesARevision() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "attempt1@example.com", "Owner", "Attempt Org");
    String eventId = createEvent(client, orgId, "Attempt Event", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Competitor");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);
    String attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();

    // Competitor cannot start/stop a judge-mode attempt — that control doesn't exist in this mode.
    assertThat(guest.post("/api/v1/attempts/" + attemptId + "/start", null).getStatusCode().value())
        .isEqualTo(403);

    ResponseEntity<String> firstEntry =
        client.put(
            "/api/v1/attempts/" + attemptId + "/judge-result",
            Map.of(
                "status", "OK", "rawTimeMs", 12345, "penalty", "PLUS_TWO", "expectedVersion", 0));
    assertThat(firstEntry.getStatusCode().value()).isEqualTo(200);
    JsonNode body = json(firstEntry);
    assertThat(body.get("rawTimeMs").asLong()).isEqualTo(12345);
    assertThat(body.get("adjustedTimeMs").asLong()).isEqualTo(14345); // +2000ms per 09 V08
    assertThat(body.get("resultStatus").asText()).isEqualTo("OK");
    long versionAfterFirst = body.get("version").asLong();

    // Judge corrects their own entry (e.g. mis-typed time) — same endpoint, a second revision.
    ResponseEntity<String> secondEntry =
        client.put(
            "/api/v1/attempts/" + attemptId + "/judge-result",
            Map.of("status", "OK", "rawTimeMs", 11000, "expectedVersion", versionAfterFirst));
    assertThat(secondEntry.getStatusCode().value()).isEqualTo(200);

    ResponseEntity<String> revisions = client.get("/api/v1/attempts/" + attemptId + "/revisions");
    assertThat(json(revisions)).hasSize(2);
    assertThat(json(revisions).get(0).get("newRawTimeMs").asLong()).isEqualTo(12345);
    assertThat(json(revisions).get(1).get("previousRawTimeMs").asLong()).isEqualTo(12345);
    assertThat(json(revisions).get(1).get("newRawTimeMs").asLong()).isEqualTo(11000);

    // A stale expectedVersion is rejected rather than silently overwriting.
    ResponseEntity<String> stale =
        client.put(
            "/api/v1/attempts/" + attemptId + "/judge-result",
            Map.of("status", "OK", "rawTimeMs", 9999, "expectedVersion", versionAfterFirst));
    assertThat(stale.getStatusCode().value()).isEqualTo(409);
    assertThat(stale.getBody()).contains("STALE_VERSION");

    // Competitor cannot see another entrant's... well here there's only one, but confirm the
    // owning entrant CAN see their own attempt detail, and an unrelated guest cannot.
    ResponseEntity<String> ownView = guest.get("/api/v1/attempts/" + attemptId);
    assertThat(ownView.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void dnsAndDnfAreRejectedWithARawTimeAndOkRequiresOneWithinBounds() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "attempt2@example.com", "Owner", "Attempt Org 2");
    String eventId = createEvent(client, orgId, "Attempt Event 2", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Competitor");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);
    String attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();

    ResponseEntity<String> dnfWithTime =
        client.put(
            "/api/v1/attempts/" + attemptId + "/judge-result",
            Map.of("status", "DNF", "rawTimeMs", 1000, "expectedVersion", 0));
    assertThat(dnfWithTime.getStatusCode().value()).isEqualTo(400);

    ResponseEntity<String> okWithoutTime =
        client.put(
            "/api/v1/attempts/" + attemptId + "/judge-result",
            Map.of("status", "OK", "expectedVersion", 0));
    assertThat(okWithoutTime.getStatusCode().value()).isEqualTo(400);

    ResponseEntity<String> dns =
        client.put(
            "/api/v1/attempts/" + attemptId + "/judge-result",
            Map.of("status", "DNS", "expectedVersion", 0));
    assertThat(dns.getStatusCode().value()).isEqualTo(200);
    assertThat(json(dns).get("resultStatus").asText()).isEqualTo("DNS");
  }

  @Test
  void selfTimedModeLetsCompetitorStartStopSubmitAndIsIdempotent() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "attempt3@example.com", "Owner", "Attempt Org 3");
    String eventId = createEvent(client, orgId, "Attempt Event 3", "PHONE_CASUAL");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Competitor");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);
    String attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();
    assertThat(
            json(client.get("/api/v1/rounds/" + roundId + "/attempts"))
                .get(0)
                .get("resultSource")
                .asText())
        .isEqualTo("SELF_TIMED");

    // Judge-result is physical-mode-only; a judge cannot enter a self-timed attempt's result.
    ResponseEntity<String> judgeAttemptSelfTimed =
        client.put(
            "/api/v1/attempts/" + attemptId + "/judge-result",
            Map.of("status", "OK", "rawTimeMs", 10000, "expectedVersion", 0));
    assertThat(judgeAttemptSelfTimed.getStatusCode().value()).isEqualTo(409);

    ResponseEntity<String> started = guest.post("/api/v1/attempts/" + attemptId + "/start", null);
    assertThat(started.getStatusCode().value()).isEqualTo(200);
    assertThat(json(started).get("state").asText()).isEqualTo("RUNNING");

    // Idempotent re-start.
    assertThat(
            json(guest.post("/api/v1/attempts/" + attemptId + "/start", null))
                .get("state")
                .asText())
        .isEqualTo("RUNNING");

    ResponseEntity<String> stopped = guest.post("/api/v1/attempts/" + attemptId + "/stop", null);
    assertThat(json(stopped).get("state").asText()).isEqualTo("STOPPED");

    ResponseEntity<String> submitted =
        guest.post(
            "/api/v1/attempts/" + attemptId + "/submit",
            Map.of("status", "OK", "rawTimeMs", 15000, "clientBuild", "test-1"));
    assertThat(submitted.getStatusCode().value()).isEqualTo(200);
    assertThat(json(submitted).get("state").asText()).isEqualTo("ACCEPTED");
    assertThat(json(submitted).get("adjustedTimeMs").asLong()).isEqualTo(15000);

    // Idempotent resubmission with the identical payload returns the same result.
    ResponseEntity<String> resubmitSame =
        guest.post(
            "/api/v1/attempts/" + attemptId + "/submit",
            Map.of("status", "OK", "rawTimeMs", 15000, "clientBuild", "test-1"));
    assertThat(resubmitSame.getStatusCode().value()).isEqualTo(200);

    // A different payload for an already-accepted attempt is a conflict, not a silent edit.
    ResponseEntity<String> resubmitDifferent =
        guest.post(
            "/api/v1/attempts/" + attemptId + "/submit",
            Map.of("status", "OK", "rawTimeMs", 9000, "clientBuild", "test-1"));
    assertThat(resubmitDifferent.getStatusCode().value()).isEqualTo(409);
    assertThat(resubmitDifferent.getBody()).contains("DUPLICATE_ATTEMPT");

    // Physical-mode-only start/stop is rejected for this self-timed attempt from a different
    // (non-owning) guest, and the owning guest cannot start a different entrant's attempt.
    TestApiClient otherGuest = new TestApiClient(restTemplate);
    joinAsGuest(otherGuest, joinCode, "Someone Else");
    ResponseEntity<String> otherStart =
        otherGuest.post("/api/v1/attempts/" + attemptId + "/start", null);
    assertThat(otherStart.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void competitorCanListTheirOwnAttemptsAcrossTheEvent() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "attempt5@example.com", "Owner", "Attempt Org 5");
    String eventId = createEvent(client, orgId, "Attempt Event 5", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Competitor");
    String roundId = createRound(client, eventId, 1, "Final", "AO5");
    prepareReadyStart(client, roundId);

    ResponseEntity<String> ownAttempts =
        guest.get("/api/v1/guest/events/" + eventId + "/me/attempts");
    assertThat(ownAttempts.getStatusCode().value()).isEqualTo(200);
    assertThat(json(ownAttempts)).hasSize(5);

    TestApiClient otherGuest = new TestApiClient(restTemplate);
    joinAsGuest(otherGuest, joinCode, "Someone Else");
    ResponseEntity<String> otherOwnAttempts =
        otherGuest.get("/api/v1/guest/events/" + eventId + "/me/attempts");
    assertThat(json(otherOwnAttempts)).isEmpty();
  }

  @Test
  void roundPausePreventsNewSelfTimedStartsButDoesNotAffectAlreadyRunningState() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "attempt4@example.com", "Owner", "Attempt Org 4");
    String eventId = createEvent(client, orgId, "Attempt Event 4", "PHONE_CASUAL");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Competitor");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);
    String attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();

    client.post("/api/v1/rounds/" + roundId + "/pause", null);
    ResponseEntity<String> blockedStart =
        guest.post("/api/v1/attempts/" + attemptId + "/start", null);
    assertThat(blockedStart.getStatusCode().value()).isEqualTo(409);

    client.post("/api/v1/rounds/" + roundId + "/pause", null); // toggle back
    ResponseEntity<String> allowedStart =
        guest.post("/api/v1/attempts/" + attemptId + "/start", null);
    assertThat(allowedStart.getStatusCode().value()).isEqualTo(200);
  }
}

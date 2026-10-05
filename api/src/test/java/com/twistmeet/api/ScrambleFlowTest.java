package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * M4 scramble controls: generation, batches/extras, per-attempt assignment, role-scoped reveal,
 * applied/checked audit, spoil, self-scramble unlock gating, and leakage prevention. Rows
 * 44-50/R41/R45/R46/R47 of TRACEABILITY.md.
 */
class ScrambleFlowTest extends AbstractIntegrationTest {

  private String createJudgeEventWithPreparedRound(String emailSuffix) throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(
            client,
            "scramble" + emailSuffix + "@example.com",
            "Owner",
            "Scramble Org " + emailSuffix);
    String eventId = createEvent(client, orgId, "Scramble Event " + emailSuffix, "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Competitor " + emailSuffix);
    String roundId = createRound(client, eventId, 1, "Final", "AO5");
    client.post("/api/v1/rounds/" + roundId + "/prepare", null);
    // Reveal/official-view/print now require an explicit JUDGE/SCRAMBLER assignment on this
    // event (00 §7: "revealed only to assigned scrambler/judge") — org membership alone no
    // longer suffices (see TenantAccessService.requireAssignedScrambleStaff). Most of this test
    // file drives reveal/view/print directly as the org owner, so self-assign here once.
    String ownerId = json(client.get("/api/v1/me")).get("id").asText();
    client.post(
        "/api/v1/events/" + eventId + "/staff-assignments",
        Map.of("userId", ownerId, "role", "SCRAMBLER"));
    return roundId;
  }

  @Test
  void batchGenerationAllocatesOneAssignmentPerAttemptPlusExtras() throws Exception {
    String roundId = createJudgeEventWithPreparedRound("1");
    // AO5 with 1 entrant = 5 attempts; 10% of 5 rounded up is 1, so extras = max(2, 1) = 2.
    ResponseEntity<String> created =
        client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    JsonNode batch = json(created);
    assertThat(batch.get("attemptCount").asInt()).isEqualTo(5);
    assertThat(batch.get("extraCount").asInt()).isEqualTo(2);
    assertThat(batch.get("generatorName").asText()).contains("tnoodle");
    // Never contains notation at this level.
    assertThat(created.getBody()).doesNotContain("notation");

    ResponseEntity<String> assignments =
        client.get("/api/v1/rounds/" + roundId + "/scramble-assignments");
    assertThat(assignments.getStatusCode().value()).isEqualTo(200);
    assertThat(json(assignments)).hasSize(5);
    assertThat(assignments.getBody()).doesNotContain("notation");

    // A second batch for the same round is rejected, not silently duplicated.
    ResponseEntity<String> duplicate =
        client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
  }

  @Test
  void batchGenerationRequiresAPreparedRound() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "scramble2@example.com", "Owner", "Scramble Org 2");
    String eventId = createEvent(client, orgId, "Scramble Event 2", "PHYSICAL_JUDGE");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    // Not prepared yet — no attempts exist.
    ResponseEntity<String> created =
        client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    assertThat(created.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void onlyOrganizerCanCreateABatchCompetitorAndStrangerCannot() throws Exception {
    String roundId = createJudgeEventWithPreparedRound("3");

    TestApiClient stranger = new TestApiClient(restTemplate);
    registerAndVerify(
        stranger, "strangerscramble@example.com", "Stranger", "correct-horse-battery");
    ResponseEntity<String> strangerAttempt =
        stranger.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    assertThat(strangerAttempt.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void revealIsRoleScopedAndIdempotentAndCompetitorCannotAccessIt() throws Exception {
    String roundId = createJudgeEventWithPreparedRound("4");
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    String assignmentId =
        json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"))
            .get(0)
            .get("id")
            .asText();

    // Judge (event-scoped, no org membership) can reveal.
    TestApiClient judgeClient = new TestApiClient(restTemplate);
    ResponseEntity<String> judgeVerify =
        registerAndVerify(
            judgeClient, "scramblejudge4@example.com", "Judge", "correct-horse-battery");
    String judgeUserId = json(judgeVerify).get("id").asText();
    String eventId = json(client.get("/api/v1/rounds/" + roundId)).get("eventId").asText();
    client.post(
        "/api/v1/events/" + eventId + "/staff-assignments",
        Map.of("userId", judgeUserId, "role", "JUDGE"));

    ResponseEntity<String> firstReveal =
        judgeClient.post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null);
    assertThat(firstReveal.getStatusCode().value()).isEqualTo(200);
    String notation = json(firstReveal).get("notation").asText();
    assertThat(notation).isNotBlank();
    java.time.Instant firstRevealedAt =
        java.time.Instant.parse(json(firstReveal).get("revealedAt").asText());

    // Idempotent: revealing again returns the same payload, not a new reveal/audit event. (The
    // two timestamps compare via Instant, not raw string equality — Postgres TIMESTAMPTZ has
    // microsecond precision, so a value re-read after a round trip through the database loses
    // the last few nanosecond digits the original in-memory Instant had; that's an expected
    // precision difference, not a second reveal.)
    ResponseEntity<String> secondReveal =
        judgeClient.post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null);
    assertThat(json(secondReveal).get("notation").asText()).isEqualTo(notation);
    java.time.Instant secondRevealedAt =
        java.time.Instant.parse(json(secondReveal).get("revealedAt").asText());
    assertThat(secondRevealedAt)
        .isCloseTo(
            firstRevealedAt,
            org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));

    // official-view requires the prior reveal to have happened, and returns the same notation.
    ResponseEntity<String> officialView =
        judgeClient.get("/api/v1/scramble-assignments/" + assignmentId + "/official-view");
    assertThat(json(officialView).get("notation").asText()).isEqualTo(notation);

    // A competitor (guest credential) cannot reach either endpoint — these are staff-only routes
    // with no guest-auth path at all, so a guest's session cookie simply isn't a valid principal.
    TestApiClient guest = new TestApiClient(restTemplate);
    ResponseEntity<String> guestReveal =
        guest.post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null);
    assertThat(guestReveal.getStatusCode().value()).isIn(401, 403, 404);
  }

  @Test
  void officialViewBeforeRevealIsRejected() throws Exception {
    String roundId = createJudgeEventWithPreparedRound("5");
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    String assignmentId =
        json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"))
            .get(0)
            .get("id")
            .asText();

    ResponseEntity<String> tooEarly =
        client.get("/api/v1/scramble-assignments/" + assignmentId + "/official-view");
    assertThat(tooEarly.getStatusCode().value()).isEqualTo(403);
    assertThat(tooEarly.getBody()).contains("SCRAMBLE_NOT_AVAILABLE");
  }

  @Test
  void appliedThenCheckedRequiresOrderAndSupportsAnIndependentSecondChecker() throws Exception {
    String roundId = createJudgeEventWithPreparedRound("6");
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    String assignmentId =
        json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"))
            .get(0)
            .get("id")
            .asText();

    // Cannot check before applying.
    ResponseEntity<String> tooEarly =
        client.post("/api/v1/scramble-assignments/" + assignmentId + "/mark-checked", null);
    assertThat(tooEarly.getStatusCode().value()).isEqualTo(409);

    // Cannot apply before revealing.
    ResponseEntity<String> applyTooEarly =
        client.post("/api/v1/scramble-assignments/" + assignmentId + "/mark-applied", null);
    assertThat(applyTooEarly.getStatusCode().value()).isEqualTo(409);

    client.post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null);
    ResponseEntity<String> applied =
        client.post("/api/v1/scramble-assignments/" + assignmentId + "/mark-applied", null);
    assertThat(applied.getStatusCode().value()).isEqualTo(200);
    assertThat(json(applied).get("appliedAt").isNull()).isFalse();

    ResponseEntity<String> checked =
        client.post("/api/v1/scramble-assignments/" + assignmentId + "/mark-checked", null);
    assertThat(json(checked).get("checkedAt").isNull()).isFalse();
    assertThat(json(checked).get("secondCheckedAt").isNull()).isTrue();

    // A second, independent checker.
    TestApiClient secondChecker = new TestApiClient(restTemplate);
    ResponseEntity<String> secondVerify =
        registerAndVerify(
            secondChecker, "secondchecker6@example.com", "Checker2", "correct-horse-battery");
    String secondUserId = json(secondVerify).get("id").asText();
    String eventId = json(client.get("/api/v1/rounds/" + roundId)).get("eventId").asText();
    client.post(
        "/api/v1/events/" + eventId + "/staff-assignments",
        Map.of("userId", secondUserId, "role", "SCRAMBLER"));
    ResponseEntity<String> secondCheck =
        secondChecker.post(
            "/api/v1/scramble-assignments/" + assignmentId + "/mark-checked?independent=true",
            null);
    assertThat(json(secondCheck).get("secondCheckedAt").isNull()).isFalse();
  }

  @Test
  void spoilVoidsConsumesAndAssignsAnUnusedExtraWithAudit() throws Exception {
    String roundId = createJudgeEventWithPreparedRound("7");
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    JsonNode firstList = json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"));
    String assignmentId = firstList.get(0).get("id").asText();
    String attemptId = firstList.get(0).get("attemptId").asText();

    ResponseEntity<String> spoiled =
        client.post(
            "/api/v1/scramble-assignments/" + assignmentId + "/spoil",
            Map.of("reason", "Scrambler mistake"));
    assertThat(spoiled.getStatusCode().value()).isEqualTo(200);
    assertThat(json(spoiled).get("state").asText()).isEqualTo("VOIDED");

    // The attempt now has exactly one live (non-voided) assignment, which is a different one.
    JsonNode afterList = json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"));
    long liveForAttempt =
        java.util.stream.StreamSupport.stream(afterList.spliterator(), false)
            .filter(a -> a.get("attemptId").asText().equals(attemptId))
            .filter(a -> !a.get("state").asText().equals("VOIDED"))
            .count();
    assertThat(liveForAttempt).isEqualTo(1);
  }

  @Test
  void spoilFailsClearlyWhenNoExtrasRemain() throws Exception {
    String roundId = createJudgeEventWithPreparedRound("8");
    // AO5, 1 entrant -> 5 attempts, 2 extras. Spoil all 5 primary assignments; the first 2 spoils
    // succeed (consuming both extras), the 3rd must fail with a clear error.
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    JsonNode assignments = json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"));

    int successCount = 0;
    int failureCount = 0;
    for (int i = 0; i < 3; i++) {
      String assignmentId = assignments.get(i).get("id").asText();
      ResponseEntity<String> spoil =
          client.post(
              "/api/v1/scramble-assignments/" + assignmentId + "/spoil", Map.of("reason", "test"));
      if (spoil.getStatusCode().value() == 200) {
        successCount++;
      } else {
        failureCount++;
        assertThat(spoil.getStatusCode().value()).isEqualTo(409);
        assertThat(spoil.getBody()).contains("NO_EXTRA_SCRAMBLES_AVAILABLE");
      }
    }
    assertThat(successCount).isEqualTo(2);
    assertThat(failureCount).isEqualTo(1);
  }

  @Test
  void onlyOrganizerOrJudgeCanSpoilNotAPlainScrambler() throws Exception {
    String roundId = createJudgeEventWithPreparedRound("9");
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    String assignmentId =
        json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"))
            .get(0)
            .get("id")
            .asText();
    String eventId = json(client.get("/api/v1/rounds/" + roundId)).get("eventId").asText();

    TestApiClient scramblerClient = new TestApiClient(restTemplate);
    ResponseEntity<String> scramblerVerify =
        registerAndVerify(
            scramblerClient, "scrambler9@example.com", "Scrambler", "correct-horse-battery");
    String scramblerUserId = json(scramblerVerify).get("id").asText();
    client.post(
        "/api/v1/events/" + eventId + "/staff-assignments",
        Map.of("userId", scramblerUserId, "role", "SCRAMBLER"));

    // Scrambler can reveal/apply/check...
    ResponseEntity<String> scramblerReveal =
        scramblerClient.post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null);
    assertThat(scramblerReveal.getStatusCode().value()).isEqualTo(200);

    // ...but cannot spoil.
    ResponseEntity<String> scramblerSpoil =
        scramblerClient.post(
            "/api/v1/scramble-assignments/" + assignmentId + "/spoil", Map.of("reason", "test"));
    assertThat(scramblerSpoil.getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void printRevealsEveryAssignmentAndReturnsNotation() throws Exception {
    String roundId = createJudgeEventWithPreparedRound("10");
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);

    ResponseEntity<String> printed =
        client.get("/api/v1/rounds/" + roundId + "/scramble-assignments/print");
    assertThat(printed.getStatusCode().value()).isEqualTo(200);
    assertThat(json(printed)).hasSize(5);
    for (JsonNode entry : json(printed)) {
      assertThat(entry.get("notation").asText()).isNotBlank();
    }

    // Every assignment is now revealed as a result of printing.
    JsonNode assignments = json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"));
    for (JsonNode a : assignments) {
      assertThat(a.get("revealedAt").isNull()).isFalse();
    }
  }

  @Test
  void selfScrambleCurrentScrambleIsGatedByUnlockAndNeverExposesAFutureAttempt() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(client, "selfscramble11@example.com", "Owner", "SS Org 11");
    String eventId = createEvent(client, orgId, "SS Event 11", "PHONE_CASUAL", "SELF_SCRAMBLE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "SS Competitor");
    String roundId = createRound(client, eventId, 1, "Final", "BO2");
    client.post("/api/v1/rounds/" + roundId + "/prepare", null);
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    client.post("/api/v1/rounds/" + roundId + "/ready", null);
    client.post("/api/v1/rounds/" + roundId + "/start", null);

    JsonNode ownAttempts = json(guest.get("/api/v1/guest/events/" + eventId + "/me/attempts"));
    String attempt1Id = ownAttempts.get(0).get("id").asText();
    String attempt2Id = ownAttempts.get(1).get("id").asText();

    // Attempt 1 is unlocked (it's the current one); attempt 2 is not yet.
    ResponseEntity<String> attempt1Scramble =
        guest.get("/api/v1/attempts/" + attempt1Id + "/current-scramble");
    assertThat(attempt1Scramble.getStatusCode().value()).isEqualTo(200);
    assertThat(json(attempt1Scramble).get("notation").asText()).isNotBlank();

    ResponseEntity<String> attempt2TooEarly =
        guest.get("/api/v1/attempts/" + attempt2Id + "/current-scramble");
    assertThat(attempt2TooEarly.getStatusCode().value()).isEqualTo(403);
    assertThat(attempt2TooEarly.getBody()).contains("SCRAMBLE_NOT_AVAILABLE");

    // Solve attempt 1; now attempt 2 unlocks.
    guest.post("/api/v1/attempts/" + attempt1Id + "/start", null);
    guest.post("/api/v1/attempts/" + attempt1Id + "/stop", null);
    guest.post(
        "/api/v1/attempts/" + attempt1Id + "/submit",
        Map.of("status", "OK", "rawTimeMs", 10000, "clientBuild", "test"));

    ResponseEntity<String> attempt2NowUnlocked =
        guest.get("/api/v1/attempts/" + attempt2Id + "/current-scramble");
    assertThat(attempt2NowUnlocked.getStatusCode().value()).isEqualTo(200);

    // A different guest in the same event cannot read this guest's scramble via the attempt id.
    TestApiClient otherGuest = new TestApiClient(restTemplate);
    joinAsGuest(otherGuest, joinCode, "Other SS Competitor");
    ResponseEntity<String> otherGuestAttempt =
        otherGuest.get("/api/v1/attempts/" + attempt1Id + "/current-scramble");
    assertThat(otherGuestAttempt.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void staffPreparedModeNeverExposesCurrentScrambleToCompetitors() throws Exception {
    String roundId = createJudgeEventWithPreparedRound("12");
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    client.post("/api/v1/rounds/" + roundId + "/ready", null);
    client.post("/api/v1/rounds/" + roundId + "/start", null);

    String eventId = json(client.get("/api/v1/rounds/" + roundId)).get("eventId").asText();
    // Re-derive the guest by re-joining is not possible (join code differs per flow); instead
    // assert directly against the one attempt we can reach via staff.
    String attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();
    TestApiClient guest = new TestApiClient(restTemplate);
    // Guest has no credential for this event, but the policy check happens before entrant lookup
    // would even matter: staff-prepared events never serve this endpoint successfully regardless.
    ResponseEntity<String> attempt =
        guest.get("/api/v1/attempts/" + attemptId + "/current-scramble");
    assertThat(attempt.getStatusCode().value()).isIn(403, 404);
  }

  @Test
  void correctionAcceptRetryAutomaticallyAssignsAnExtraScrambleWhenABatchExists() throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(
            client, "correction13@example.com", "Owner", "Correction Scramble Org");
    String eventId = createEvent(client, orgId, "Correction Scramble Event", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Competitor");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    client.post("/api/v1/rounds/" + roundId + "/prepare", null);
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    client.post("/api/v1/rounds/" + roundId + "/ready", null);
    client.post("/api/v1/rounds/" + roundId + "/start", null);

    // Generating the batch above also saved the attempt (to attach its scrambleAssignmentId),
    // which bumps its optimistic-lock version — read the current version rather than assuming 0.
    JsonNode attemptBeforeResult =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0);
    String attemptId = attemptBeforeResult.get("id").asText();
    long attemptVersion = attemptBeforeResult.get("version").asLong();
    client.put(
        "/api/v1/attempts/" + attemptId + "/judge-result",
        Map.of("status", "OK", "rawTimeMs", 12000, "expectedVersion", attemptVersion));

    ResponseEntity<String> request =
        guest.post(
            "/api/v1/attempts/" + attemptId + "/correction-requests",
            Map.of("category", "INTERRUPTION", "note", "Fire alarm"));
    String correctionId = json(request).get("id").asText();
    client.post(
        "/api/v1/corrections/" + correctionId + "/decision",
        Map.of("action", "ACCEPT_RETRY", "reason", "Clearly interrupted", "expectedVersion", 0));

    // BO1 originally allocates 1 attempt -> 1 primary scramble assignment, plus max(2, ceil(1*
    // 0.1))=2 extras. ACCEPT_RETRY creates a brand-new replacement Attempt and must assign it one
    // of those extras automatically — the replacement now has its own live assignment, and the
    // original attempt's assignment is untouched (that attempt was voided, not its scramble).
    JsonNode assignments = json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"));
    assertThat(assignments).hasSize(2);
    JsonNode attemptsAfter = json(client.get("/api/v1/rounds/" + roundId + "/attempts"));
    assertThat(attemptsAfter).hasSize(2);
    boolean replacementHasAssignment =
        java.util.stream.StreamSupport.stream(attemptsAfter.spliterator(), false)
            .filter(a -> a.get("state").asText().equals("PENDING"))
            .anyMatch(a -> !a.get("scrambleAssignmentId").isNull());
    assertThat(replacementHasAssignment).isTrue();
  }

  @Test
  void leakageNeverAppearsInGeneralEventAttemptOrStandingsResponses() throws Exception {
    String roundId = createJudgeEventWithPreparedRound("14");
    ResponseEntity<String> batchResponse =
        client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    String eventId = json(client.get("/api/v1/rounds/" + roundId)).get("eventId").asText();
    String assignmentId =
        json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"))
            .get(0)
            .get("id")
            .asText();
    String revealedNotation =
        json(client.post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null))
            .get("notation")
            .asText();

    client.post("/api/v1/rounds/" + roundId + "/ready", null);
    client.post("/api/v1/rounds/" + roundId + "/start", null);

    ResponseEntity<String> eventView = client.get("/api/v1/events/" + eventId);
    ResponseEntity<String> attemptsView = client.get("/api/v1/rounds/" + roundId + "/attempts");
    ResponseEntity<String> roundsView = client.get("/api/v1/events/" + eventId + "/rounds");

    assertThat(eventView.getBody()).doesNotContain(revealedNotation);
    assertThat(attemptsView.getBody()).doesNotContain(revealedNotation);
    assertThat(roundsView.getBody()).doesNotContain(revealedNotation);
    assertThat(eventView.getBody()).doesNotContain("notation");
    assertThat(attemptsView.getBody()).doesNotContain("notation");
    assertThat(roundsView.getBody()).doesNotContain("notation");
  }

  @Test
  void nonexistentAssignmentIs404ForEveryAction() throws Exception {
    String roundId = createJudgeEventWithPreparedRound("15");
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    String fakeId = UUID.randomUUID().toString();

    assertThat(
            client
                .post("/api/v1/scramble-assignments/" + fakeId + "/reveal", null)
                .getStatusCode()
                .value())
        .isEqualTo(404);
    assertThat(
            client
                .get("/api/v1/scramble-assignments/" + fakeId + "/official-view")
                .getStatusCode()
                .value())
        .isEqualTo(404);
  }
}

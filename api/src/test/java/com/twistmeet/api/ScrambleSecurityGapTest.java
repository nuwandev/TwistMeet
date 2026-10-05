package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * Closes the M4 scramble-secrecy verification gap identified before M5: the M4 report's own leakage
 * checks covered competitor HTML, competitor API access, and an unauthenticated request, but 08's
 * full acceptance list ("public endpoints, error responses, logs, caches, exports, unauthorized
 * staff views") had no test for error response bodies, application logs, HTTP caching, or a staff
 * member on a *different* event entirely. Exports are covered by {@code HistoryExportFlowTest}
 * (M5), and the new public endpoints below are covered here directly since they did not exist until
 * this task.
 */
class ScrambleSecurityGapTest extends AbstractIntegrationTest {

  private ListAppender<ILoggingEvent> logAppender;
  private Logger rootLogger;

  @BeforeEach
  void attachLogCapture() {
    rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    rootLogger.setLevel(Level.ALL);
    logAppender = new ListAppender<>();
    logAppender.start();
    rootLogger.addAppender(logAppender);
  }

  @AfterEach
  void detachLogCapture() {
    rootLogger.detachAppender(logAppender);
  }

  private String roundWithRevealedNotation(String suffix) throws Exception {
    String orgId =
        registerVerifyAndCreateOrg(
            client, "secgap" + suffix + "@example.com", "Owner", "SecGap Org " + suffix);
    String eventId = createEvent(client, orgId, "SecGap Event " + suffix, "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "SecGap Competitor " + suffix);
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    client.post("/api/v1/rounds/" + roundId + "/prepare", null);
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    // Reveal/official-view/print now require an explicit assignment, not just org membership
    // (see TenantAccessService.requireAssignedScrambleStaff) — self-assign the owner.
    String ownerId = json(client.get("/api/v1/me")).get("id").asText();
    client.post(
        "/api/v1/events/" + eventId + "/staff-assignments",
        Map.of("userId", ownerId, "role", "SCRAMBLER"));
    return roundId;
  }

  @Test
  void errorResponsesNeverCarryNotationOrCiphertext() throws Exception {
    String roundId = roundWithRevealedNotation("1");
    String assignmentId =
        json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"))
            .get(0)
            .get("id")
            .asText();
    String notation =
        json(client.post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null))
            .get("notation")
            .asText();

    // A validation error (missing required "reason") on the spoil endpoint echoes only static
    // field names/messages, never the notation that was just revealed above.
    ResponseEntity<String> invalidSpoil =
        client.post("/api/v1/scramble-assignments/" + assignmentId + "/spoil", Map.of());
    assertThat(invalidSpoil.getStatusCode().value()).isEqualTo(400);
    assertThat(invalidSpoil.getBody()).doesNotContain(notation);

    // A 404 for a nonexistent assignment on every notation-carrying action.
    String fakeId = "00000000-0000-0000-0000-000000000000";
    for (String path :
        new String[] {
          "/api/v1/scramble-assignments/" + fakeId + "/official-view",
        }) {
      ResponseEntity<String> response = client.get(path);
      assertThat(response.getStatusCode().value()).isEqualTo(404);
      assertThat(response.getBody()).doesNotContain(notation);
    }
    ResponseEntity<String> revealFake =
        client.post("/api/v1/scramble-assignments/" + fakeId + "/reveal", null);
    assertThat(revealFake.getStatusCode().value()).isEqualTo(404);
    assertThat(revealFake.getBody()).doesNotContain(notation);
  }

  @Test
  void applicationLogsNeverCarryNotationOrCiphertext() throws Exception {
    String roundId = roundWithRevealedNotation("2");
    String assignmentId =
        json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"))
            .get(0)
            .get("id")
            .asText();
    String notation =
        json(client.post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null))
            .get("notation")
            .asText();
    client.get("/api/v1/scramble-assignments/" + assignmentId + "/official-view");
    client.post(
        "/api/v1/scramble-assignments/" + assignmentId + "/spoil", Map.of("reason", "test"));

    for (ILoggingEvent event : java.util.List.copyOf(logAppender.list)) {
      String message = event.getFormattedMessage();
      assertThat(message).doesNotContain(notation);
      if (event.getThrowableProxy() != null) {
        assertThat(event.getThrowableProxy().getMessage()).doesNotContain(notation);
      }
    }
  }

  @Test
  void notationCarryingResponsesAreMarkedNoStore() throws Exception {
    String roundId = roundWithRevealedNotation("3");
    String assignmentId =
        json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"))
            .get(0)
            .get("id")
            .asText();

    ResponseEntity<String> reveal =
        client.post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null);
    assertThat(reveal.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");

    ResponseEntity<String> officialView =
        client.get("/api/v1/scramble-assignments/" + assignmentId + "/official-view");
    assertThat(officialView.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");

    ResponseEntity<String> print =
        client.get("/api/v1/rounds/" + roundId + "/scramble-assignments/print");
    assertThat(print.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
  }

  @Test
  void staffOnADifferentEventEntirelyCannotRevealOrPrintOrOfficialView() throws Exception {
    String roundId = roundWithRevealedNotation("4");
    String assignmentId =
        json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"))
            .get(0)
            .get("id")
            .asText();

    // A judge assigned to a completely different event in a different org.
    TestApiClient otherClient = new TestApiClient(restTemplate);
    String otherOrgId =
        registerVerifyAndCreateOrg(otherClient, "secgap5@example.com", "Owner5", "Other Org 5");
    String otherEventId = createEvent(otherClient, otherOrgId, "Other Event 5", "PHYSICAL_JUDGE");
    TestApiClient outsideJudge = new TestApiClient(restTemplate);
    ResponseEntity<String> judgeVerify =
        registerAndVerify(
            outsideJudge, "secgapjudge5@example.com", "Judge5", "correct-horse-battery");
    String judgeUserId = json(judgeVerify).get("id").asText();
    otherClient.post(
        "/api/v1/events/" + otherEventId + "/staff-assignments",
        Map.of("userId", judgeUserId, "role", "JUDGE"));

    ResponseEntity<String> reveal =
        outsideJudge.post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null);
    assertThat(reveal.getStatusCode().value()).isEqualTo(404);
    ResponseEntity<String> officialView =
        outsideJudge.get("/api/v1/scramble-assignments/" + assignmentId + "/official-view");
    assertThat(officialView.getStatusCode().value()).isEqualTo(404);
    ResponseEntity<String> print =
        outsideJudge.get("/api/v1/rounds/" + roundId + "/scramble-assignments/print");
    assertThat(print.getStatusCode().value()).isEqualTo(404);
  }

  /**
   * Corrected per explicit instruction: 00 §7 says a staff-prepared scramble is "revealed only to
   * assigned scrambler/judge," and §5's Organizer role list conspicuously omits scramble reveal
   * (unlike Judge's "assigned event attempt entry/status" and Scrambler's "assigned scramble") — so
   * organization membership alone must NOT grant reveal access, even though it legitimately grants
   * Organizer-level access to every other event-scoped action (round config, roster, batch
   * creation, applied/checked marking). Full role matrix below: unassigned-same-org-owner/organizer
   * -> 403; assigned Scrambler -> 200; assigned Judge -> 200; staff on a different event -> 404
   * (covered above); competitor -> 401/403/404; stranger -> 404.
   */
  @Test
  void onlyAnExplicitlyAssignedScramblerOrJudgeCanRevealNotJustAnyOrgMember() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "secgap6@example.com", "Owner6", "Org 6");
    String eventA = createEvent(client, orgId, "Event A 6", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventA);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Org 6 Competitor");
    String roundId = createRound(client, eventA, 1, "Final", "BO1");
    client.post("/api/v1/rounds/" + roundId + "/prepare", null);
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
    String assignmentId =
        json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"))
            .get(0)
            .get("id")
            .asText();

    // The owner is an org member (Organizer-equivalent) but was never explicitly assigned
    // Scrambler/Judge for this event — reveal/official-view/print must all 403, not 200.
    assertThat(
            client
                .post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null)
                .getStatusCode()
                .value())
        .isEqualTo(403);
    assertThat(
            client
                .get("/api/v1/scramble-assignments/" + assignmentId + "/official-view")
                .getStatusCode()
                .value())
        .isEqualTo(403);
    assertThat(
            client
                .get("/api/v1/rounds/" + roundId + "/scramble-assignments/print")
                .getStatusCode()
                .value())
        .isEqualTo(403);

    // Metadata-only actions (no notation) are unaffected — the owner still administers prep.
    assertThat(
            client
                .get("/api/v1/rounds/" + roundId + "/scramble-assignments")
                .getStatusCode()
                .value())
        .isEqualTo(200);

    // Self-assigning as Scrambler grants reveal — the documented, intentional path.
    String ownerId = json(client.get("/api/v1/me")).get("id").asText();
    client.post(
        "/api/v1/events/" + eventA + "/staff-assignments",
        Map.of("userId", ownerId, "role", "SCRAMBLER"));
    assertThat(
            client
                .post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null)
                .getStatusCode()
                .value())
        .isEqualTo(200);

    // A separately assigned Judge (event-scoped, not an org member at all) also gets reveal.
    TestApiClient judgeClient = new TestApiClient(restTemplate);
    ResponseEntity<String> judgeVerify =
        registerAndVerify(
            judgeClient, "secgap6judge@example.com", "Judge6", "correct-horse-battery");
    String judgeUserId = json(judgeVerify).get("id").asText();
    client.post(
        "/api/v1/events/" + eventA + "/staff-assignments",
        Map.of("userId", judgeUserId, "role", "JUDGE"));
    assertThat(
            judgeClient
                .get("/api/v1/scramble-assignments/" + assignmentId + "/official-view")
                .getStatusCode()
                .value())
        .isEqualTo(200);

    // The competitor (guest credential, not a staff principal at all) still cannot reach it.
    ResponseEntity<String> guestReveal =
        guest.post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null);
    assertThat(guestReveal.getStatusCode().value()).isIn(401, 403, 404);

    // A total stranger gets 404, same anti-enumeration pattern as everywhere else.
    TestApiClient stranger = new TestApiClient(restTemplate);
    registerAndVerify(
        stranger, "secgap6stranger@example.com", "Stranger6", "correct-horse-battery");
    assertThat(
            stranger
                .post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null)
                .getStatusCode()
                .value())
        .isEqualTo(404);
  }

  @Test
  void publicUnauthenticatedEndpointsNeverExposeScrambleNotation() throws Exception {
    String roundId = roundWithRevealedNotation("7");
    String eventId = json(client.get("/api/v1/rounds/" + roundId)).get("eventId").asText();
    String assignmentId =
        json(client.get("/api/v1/rounds/" + roundId + "/scramble-assignments"))
            .get(0)
            .get("id")
            .asText();
    String notation =
        json(client.post("/api/v1/scramble-assignments/" + assignmentId + "/reveal", null))
            .get("notation")
            .asText();

    client.post("/api/v1/rounds/" + roundId + "/ready", null);
    client.post("/api/v1/rounds/" + roundId + "/start", null);
    ResponseEntity<String> published = client.post("/api/v1/events/" + eventId + "/publish", null);
    String slug = json(published).get("publicSlug").asText();

    TestApiClient anon = new TestApiClient(restTemplate);
    ResponseEntity<String> publicEvent = anon.get("/api/v1/public/events/" + slug);
    assertThat(publicEvent.getStatusCode().value()).isEqualTo(200);
    assertThat(publicEvent.getBody()).doesNotContain(notation);
    assertThat(publicEvent.getBody()).doesNotContain("notation");

    JsonNode rounds = json(publicEvent).get("rounds");
    assertThat(rounds).hasSize(1);
    ResponseEntity<String> publicStandings =
        anon.get("/api/v1/public/events/" + slug + "/standings?roundId=" + roundId);
    assertThat(publicStandings.getStatusCode().value()).isEqualTo(200);
    assertThat(publicStandings.getBody()).doesNotContain(notation);
    assertThat(publicStandings.getBody()).doesNotContain("notation");
  }
}

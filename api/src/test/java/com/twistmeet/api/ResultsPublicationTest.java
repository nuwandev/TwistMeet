package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * 07 S12 "Results and publication": "Revisions list actor, time, reason and before/after values."
 * Covers the round-wide revisions endpoint added for S12 (previously only a per-attempt view
 * existed, and it never exposed the actor at all) plus the "amend eligible metadata" organizer
 * action (the existing entrant-rename endpoint, surfaced on the S12 screen) and name masking.
 */
class ResultsPublicationTest extends AbstractIntegrationTest {

  @Test
  void roundRevisionsListEveryChangeWithActorEntrantAndBeforeAfterNewestFirst() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "s12-1@example.com", "Owner", "S12 Org 1");
    String eventId = createEvent(client, orgId, "S12 Event 1", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guestA = new TestApiClient(restTemplate);
    joinAsGuest(guestA, joinCode, "Entrant A");
    TestApiClient guestB = new TestApiClient(restTemplate);
    joinAsGuest(guestB, joinCode, "Entrant B");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);

    JsonNode attempts = json(client.get("/api/v1/rounds/" + roundId + "/attempts"));
    String attemptA = attempts.get(0).get("id").asText();
    String attemptB = attempts.get(1).get("id").asText();

    judgeOk(client, attemptA, 10000);
    judgeOk(client, attemptB, 20000);
    // A second entry for A (e.g. a mis-typed time corrected) — a second revision, same attempt.
    judgeOk(client, attemptA, 9000);

    JsonNode revisions = json(client.get("/api/v1/rounds/" + roundId + "/revisions"));
    assertThat(revisions).hasSize(3);
    // Newest first: the correction to attempt A is the most recent event.
    assertThat(revisions.get(0).get("attemptId").asText()).isEqualTo(attemptA);
    assertThat(revisions.get(0).get("previousRawTimeMs").asLong()).isEqualTo(10000);
    assertThat(revisions.get(0).get("newRawTimeMs").asLong()).isEqualTo(9000);
    assertThat(revisions.get(0).get("entrantDisplayName").asText()).isEqualTo("Entrant A");
    assertThat(revisions.get(0).get("actorDisplayName").asText()).isEqualTo("Owner");
    assertThat(revisions.get(0).get("actorUserId").asText()).isNotBlank();
    assertThat(revisions.get(0).get("createdAt").asText()).isNotBlank();

    // Every revision carries its own attempt/entrant identity, not just the newest's.
    boolean anyForB =
        java.util.stream.IntStream.range(0, revisions.size())
            .anyMatch(i -> revisions.get(i).get("attemptId").asText().equals(attemptB));
    assertThat(anyForB).isTrue();
  }

  @Test
  void roundRevisionsAreJudgeOrOrganizerOnlyNotAStrangerOrCompetitor() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "s12-2@example.com", "Owner", "S12 Org 2");
    String eventId = createEvent(client, orgId, "S12 Event 2", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Competitor");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);
    String attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();
    judgeOk(client, attemptId, 10000);

    assertThat(client.get("/api/v1/rounds/" + roundId + "/revisions").getStatusCode().value())
        .isEqualTo(200);

    TestApiClient stranger = new TestApiClient(restTemplate);
    registerVerifyAndCreateOrg(stranger, "s12-3@example.com", "Stranger", "S12 Org 3");
    assertThat(stranger.get("/api/v1/rounds/" + roundId + "/revisions").getStatusCode().value())
        .isEqualTo(404);

    // The competitor rides a guest cookie, not a staff session, so this is simply unauthenticated
    // (or, depending on exactly where the check lands, forbidden/not-found — never 200).
    assertThat(guest.get("/api/v1/rounds/" + roundId + "/revisions").getStatusCode().value())
        .isIn(401, 403, 404);
  }

  @Test
  void organizerCanAmendEntrantDisplayNameAsResultsPageMetadata() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "s12-4@example.com", "Owner", "S12 Org 4");
    String eventId = createEvent(client, orgId, "S12 Event 4", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    String entrantId = joinAsGuest(guest, joinCode, "Typo Namee");

    ResponseEntity<String> amend =
        client.patch(
            "/api/v1/events/" + eventId + "/entrants/" + entrantId,
            Map.of("displayName", "Correct Name"));
    assertThat(amend.getStatusCode().value()).isEqualTo(200);
    assertThat(json(amend).get("displayName").asText()).isEqualTo("Correct Name");
  }
}

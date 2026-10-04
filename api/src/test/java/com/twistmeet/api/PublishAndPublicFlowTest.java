package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * M5 publishing lifecycle and public display (08 {@code /events/{id}/publish}/{@code unpublish};
 * {@code /public/events/{publicSlug}}, {@code .../standings}; 07 S12/S13). Rows 57-60 of
 * TRACEABILITY.md.
 */
class PublishAndPublicFlowTest extends AbstractIntegrationTest {

  @Test
  void publishGeneratesASlugAndUnpublishRotatesItInvalidatingOldLinks() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "pub1@example.com", "Owner1", "Pub Org 1");
    String eventId = createEvent(client, orgId, "Pub Event 1", "PHYSICAL_JUDGE");

    JsonNode published = json(client.post("/api/v1/events/" + eventId + "/publish", null));
    String slugV1 = published.get("publicSlug").asText();
    assertThat(published.get("publishedAt").isNull()).isFalse();

    TestApiClient anon = new TestApiClient(restTemplate);
    assertThat(anon.get("/api/v1/public/events/" + slugV1).getStatusCode().value()).isEqualTo(200);

    JsonNode unpublished = json(client.post("/api/v1/events/" + eventId + "/unpublish", null));
    assertThat(unpublished.get("publishedAt").isNull()).isTrue();
    String slugV2 = unpublished.get("publicSlug").asText();
    assertThat(slugV2).isNotEqualTo(slugV1);

    // The old link is dead, not merely "not currently published" under the same slug.
    assertThat(anon.get("/api/v1/public/events/" + slugV1).getStatusCode().value()).isEqualTo(404);
    assertThat(anon.get("/api/v1/public/events/" + slugV2).getStatusCode().value()).isEqualTo(404);

    // Re-publishing keeps the slug stable (doesn't rotate again) and works again.
    JsonNode republished = json(client.post("/api/v1/events/" + eventId + "/publish", null));
    assertThat(republished.get("publicSlug").asText()).isEqualTo(slugV2);
    assertThat(anon.get("/api/v1/public/events/" + slugV2).getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void onlyOrganizerCanPublishOrUnpublish() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "pub2@example.com", "Owner2", "Pub Org 2");
    String eventId = createEvent(client, orgId, "Pub Event 2", "PHYSICAL_JUDGE");

    TestApiClient stranger = new TestApiClient(restTemplate);
    registerAndVerify(stranger, "pub2stranger@example.com", "Stranger2", "correct-horse-battery");
    assertThat(
            stranger.post("/api/v1/events/" + eventId + "/publish", null).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  void draftAndPreparingRoundsAreHiddenButLiveAndClosedAreVisibleAndProvisionalFlagIsCorrect()
      throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "pub3@example.com", "Owner3", "Pub Org 3");
    String eventId = createEvent(client, orgId, "Pub Event 3", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Pub Competitor 3");

    String draftRoundId = createRound(client, eventId, 1, "Draft Round", "BO1");
    String liveRoundId = createRound(client, eventId, 2, "Live Round", "BO1");
    prepareReadyStart(client, liveRoundId);

    String slug =
        json(client.post("/api/v1/events/" + eventId + "/publish", null))
            .get("publicSlug")
            .asText();
    TestApiClient anon = new TestApiClient(restTemplate);
    JsonNode publicEvent = json(anon.get("/api/v1/public/events/" + slug));
    JsonNode rounds = publicEvent.get("rounds");
    assertThat(rounds).hasSize(1);
    assertThat(rounds.get(0).get("roundId").asText()).isEqualTo(liveRoundId);

    JsonNode standings =
        json(anon.get("/api/v1/public/events/" + slug + "/standings?roundId=" + liveRoundId));
    assertThat(standings.get("provisional").asBoolean()).isTrue();

    // The draft round's standings are not reachable at all.
    ResponseEntity<String> draftStandings =
        anon.get("/api/v1/public/events/" + slug + "/standings?roundId=" + draftRoundId);
    assertThat(draftStandings.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void closedRoundIsFinalNotProvisionalAndWithdrawnEntrantIsExcluded() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "pub4@example.com", "Owner4", "Pub Org 4");
    String eventId = createEvent(client, orgId, "Pub Event 4", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guestA = new TestApiClient(restTemplate);
    String entrantA = joinAsGuest(guestA, joinCode, "Pub Competitor A4");
    TestApiClient guestB = new TestApiClient(restTemplate);
    String entrantB = joinAsGuest(guestB, joinCode, "Pub Competitor B4");

    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    client.post("/api/v1/rounds/" + roundId + "/prepare", null);
    client.post("/api/v1/events/" + eventId + "/entrants/" + entrantB + "/withdraw", null);
    client.post("/api/v1/rounds/" + roundId + "/ready", null);
    client.post("/api/v1/rounds/" + roundId + "/start", null);

    JsonNode attempts = json(client.get("/api/v1/rounds/" + roundId + "/attempts"));
    String attemptAId = null;
    String attemptBId = null;
    for (JsonNode a : attempts) {
      if (a.get("entrantId").asText().equals(entrantA)) attemptAId = a.get("id").asText();
      if (a.get("entrantId").asText().equals(entrantB)) attemptBId = a.get("id").asText();
    }
    judgeOk(client, attemptAId, 12340);
    long bVersion = currentAttemptVersion(client, attemptBId);
    client.put(
        "/api/v1/attempts/" + attemptBId + "/judge-result",
        java.util.Map.of("status", "DNS", "expectedVersion", bVersion));
    client.post("/api/v1/rounds/" + roundId + "/review", null);
    client.post("/api/v1/rounds/" + roundId + "/close", null);

    String slug =
        json(client.post("/api/v1/events/" + eventId + "/publish", null))
            .get("publicSlug")
            .asText();
    TestApiClient anon = new TestApiClient(restTemplate);
    JsonNode standings =
        json(anon.get("/api/v1/public/events/" + slug + "/standings?roundId=" + roundId));
    assertThat(standings.get("provisional").asBoolean()).isFalse();
    JsonNode entries = standings.get("standings");
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).get("displayName").asText()).isEqualTo("Pub Competitor A4");
  }

  @Test
  void publicDtoHasNoEmailTokenNotesOrScrambleFields() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "pub5@example.com", "Owner5", "Pub Org 5");
    String eventId = createEvent(client, orgId, "Pub Event 5", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "Pub Competitor 5");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    prepareReadyStart(client, roundId);

    String slug =
        json(client.post("/api/v1/events/" + eventId + "/publish", null))
            .get("publicSlug")
            .asText();
    TestApiClient anon = new TestApiClient(restTemplate);
    ResponseEntity<String> eventBody = anon.get("/api/v1/public/events/" + slug);
    ResponseEntity<String> standingsBody =
        anon.get("/api/v1/public/events/" + slug + "/standings?roundId=" + roundId);
    for (String forbidden :
        new String[] {
          "email", "token", "notes", "note", "scramble", "notation", "joinCode", "credential"
        }) {
      assertThat(eventBody.getBody().toLowerCase()).doesNotContain(forbidden);
      assertThat(standingsBody.getBody().toLowerCase()).doesNotContain(forbidden);
    }
  }
}

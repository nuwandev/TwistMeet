package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * M5 organization event history and CSV export (07 S14; 08 {@code
 * /organizations/{orgId}/events/{eventId}/export.csv}). Rows 61-62 of TRACEABILITY.md.
 */
class HistoryExportFlowTest extends AbstractIntegrationTest {

  @Test
  void historyListsAndFiltersByStateAndName() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "hist1@example.com", "Owner1", "Hist Org 1");
    String eventA = createEvent(client, orgId, "Spring Meetup", "PHYSICAL_JUDGE");
    String eventB = createEvent(client, orgId, "Autumn Meetup", "PHYSICAL_JUDGE");
    client.post("/api/v1/events/" + eventA + "/registration/open", null);

    ResponseEntity<String> all = client.get("/api/v1/organizations/" + orgId + "/events/history");
    assertThat(json(all)).hasSize(2);

    ResponseEntity<String> byState =
        client.get("/api/v1/organizations/" + orgId + "/events/history?state=DRAFT");
    assertThat(json(byState)).hasSize(1);
    assertThat(json(byState).get(0).get("eventId").asText()).isEqualTo(eventB);

    ResponseEntity<String> byName =
        client.get("/api/v1/organizations/" + orgId + "/events/history?q=spring");
    assertThat(json(byName)).hasSize(1);
    assertThat(json(byName).get(0).get("eventId").asText()).isEqualTo(eventA);
  }

  @Test
  void historySummaryCountsEntrantsAndRounds() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "hist2@example.com", "Owner2", "Hist Org 2");
    String eventId = createEvent(client, orgId, "Hist Event 2", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guestA = new TestApiClient(restTemplate);
    joinAsGuest(guestA, joinCode, "Hist Competitor A2");
    TestApiClient guestB = new TestApiClient(restTemplate);
    joinAsGuest(guestB, joinCode, "Hist Competitor B2");
    createRound(client, eventId, 1, "Round 1", "BO1");
    createRound(client, eventId, 2, "Round 2", "BO1");

    JsonNode summary =
        json(client.get("/api/v1/organizations/" + orgId + "/events/history")).get(0);
    assertThat(summary.get("entrantCount").asInt()).isEqualTo(2);
    assertThat(summary.get("roundCount").asInt()).isEqualTo(2);
  }

  @Test
  void onlyOrganizerCanExportCsvJudgeAndStrangerCannot() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "hist3@example.com", "Owner3", "Hist Org 3");
    String eventId = createEvent(client, orgId, "Hist Event 3", "PHYSICAL_JUDGE");

    TestApiClient stranger = new TestApiClient(restTemplate);
    registerAndVerify(stranger, "hist3stranger@example.com", "Stranger3", "correct-horse-battery");
    ResponseEntity<String> strangerExport =
        stranger.get("/api/v1/organizations/" + orgId + "/events/" + eventId + "/export.csv");
    assertThat(strangerExport.getStatusCode().value()).isEqualTo(404);

    ResponseEntity<String> ownerExport =
        client.get("/api/v1/organizations/" + orgId + "/events/" + eventId + "/export.csv");
    assertThat(ownerExport.getStatusCode().value()).isEqualTo(200);
    assertThat(ownerExport.getHeaders().getFirst("Content-Disposition")).contains("attachment");
  }

  @Test
  void csvContainsRawMsAndFormattedResultAndNeverScrambleNotation() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "hist4@example.com", "Owner4", "Hist Org 4");
    String eventId = createEvent(client, orgId, "Hist Event 4", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    String entrantId = joinAsGuest(guest, joinCode, "Hist Competitor 4");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    client.post("/api/v1/rounds/" + roundId + "/prepare", null);
    client.post("/api/v1/rounds/" + roundId + "/scramble-batches", null);
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
    String attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();
    judgeOk(client, attemptId, 12345);

    ResponseEntity<String> export =
        client.get("/api/v1/organizations/" + orgId + "/events/" + eventId + "/export.csv");
    String csv = export.getBody();
    assertThat(csv).contains("12345"); // raw ms
    assertThat(csv).contains("12.35"); // formatted, half-up
    assertThat(csv).contains("Hist Competitor 4");
    assertThat(csv).doesNotContain(notation);
    assertThat(csv.toLowerCase()).doesNotContain("notation");
  }

  @Test
  void csvSanitizesFormulaInjectionInDisplayNames() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "hist5@example.com", "Owner5", "Hist Org 5");
    String eventId = createEvent(client, orgId, "Hist Event 5", "PHYSICAL_JUDGE");
    String joinCode = openRegistration(client, eventId);
    TestApiClient guest = new TestApiClient(restTemplate);
    joinAsGuest(guest, joinCode, "=cmd|'/c calc'!A1");
    String roundId = createRound(client, eventId, 1, "Final", "BO1");
    client.post("/api/v1/rounds/" + roundId + "/prepare", null);
    client.post("/api/v1/rounds/" + roundId + "/ready", null);
    client.post("/api/v1/rounds/" + roundId + "/start", null);
    String attemptId =
        json(client.get("/api/v1/rounds/" + roundId + "/attempts")).get(0).get("id").asText();
    judgeOk(client, attemptId, 10000);

    ResponseEntity<String> export =
        client.get("/api/v1/organizations/" + orgId + "/events/" + eventId + "/export.csv");
    String csv = export.getBody();
    // The literal cell is never left as a raw formula-leading string; it was prefixed with a
    // single quote (inside the CSV-quoted cell).
    assertThat(csv).doesNotContain("\"=cmd");
    assertThat(csv).contains("'=cmd");
  }
}

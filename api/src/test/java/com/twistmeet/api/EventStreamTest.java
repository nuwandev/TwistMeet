package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * 08 "Real-time updates": "Use SSE or WebSocket with authenticated, event-scoped subscriptions...
 * Public channel emits only published fields." A plain blocking HTTP call via {@code TestApiClient}
 * cannot exercise the streaming endpoints themselves (the connection is intentionally left open,
 * so {@code TestRestTemplate} would hang waiting for a body that never ends) — those two paths are
 * covered here with a raw {@link HttpClient} that reads only the initial "connected" comment line
 * and then disconnects. Authorization boundaries, which complete immediately with no emitter ever
 * created (a 404/403/404 response, not a stream), are covered with the ordinary blocking client.
 */
class EventStreamTest extends AbstractIntegrationTest {

  @Test
  void staffStreamIsAnyRecognizedStaffButStrangerGets404() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "stream1@example.com", "Owner1", "Stream Org 1");
    String eventId = createEvent(client, orgId, "Stream Event 1", "PHYSICAL_JUDGE");

    String rootUri = restTemplate.getRootUri();
    HttpClient httpClient = HttpClient.newHttpClient();

    // Owner (organization member) is a recognized staff relationship: the stream connects and
    // sends its initial "connected" comment before we disconnect.
    HttpRequest ownerRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(rootUri + "/api/v1/events/" + eventId + "/stream"))
            .header("Cookie", client.cookieHeaderForRawRequest())
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
    HttpResponse<java.io.InputStream> ownerResponse =
        httpClient.send(ownerRequest, HttpResponse.BodyHandlers.ofInputStream());
    assertThat(ownerResponse.statusCode()).isEqualTo(200);
    assertThat(ownerResponse.headers().firstValue("Content-Type").orElse(""))
        .contains("text/event-stream");
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(ownerResponse.body(), StandardCharsets.UTF_8))) {
      String firstLine = reader.readLine();
      assertThat(firstLine).contains("connected");
    }

    // A total stranger (no org membership, no staff assignment) gets the standard
    // anti-enumeration 404, exactly as every other event-scoped read does — no emitter created.
    TestApiClient stranger = new TestApiClient(restTemplate);
    registerVerifyAndCreateOrg(stranger, "stream2@example.com", "Owner2", "Stream Org 2");
    ResponseEntity<String> strangerResponse = stranger.get("/api/v1/events/" + eventId + "/stream");
    assertThat(strangerResponse.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void publicStreamRequiresThePublishedEvent() throws Exception {
    String orgId = registerVerifyAndCreateOrg(client, "stream3@example.com", "Owner3", "Stream Org 3");
    String eventId = createEvent(client, orgId, "Stream Event 3", "PHYSICAL_JUDGE");

    // Not yet published: unknown slug, so the public stream 404s immediately (no emitter).
    ResponseEntity<String> beforePublish =
        client.get("/api/v1/public/events/does-not-exist/stream");
    assertThat(beforePublish.getStatusCode().value()).isEqualTo(404);

    ResponseEntity<String> publishResponse =
        client.post("/api/v1/events/" + eventId + "/publish", null);
    String publicSlug = json(publishResponse).get("publicSlug").asText();

    String rootUri = restTemplate.getRootUri();
    HttpClient httpClient = HttpClient.newHttpClient();
    HttpRequest publicRequest =
        HttpRequest.newBuilder()
            .uri(URI.create(rootUri + "/api/v1/public/events/" + publicSlug + "/stream"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
    HttpResponse<java.io.InputStream> publicResponse =
        httpClient.send(publicRequest, HttpResponse.BodyHandlers.ofInputStream());
    assertThat(publicResponse.statusCode()).isEqualTo(200);
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(publicResponse.body(), StandardCharsets.UTF_8))) {
      assertThat(reader.readLine()).contains("connected");
    }
  }
}

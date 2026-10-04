package com.twistmeet.api.support;

import com.twistmeet.api.common.SimpleRateLimiter;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boots the real application against a real PostgreSQL database (see
 * src/test/resources/application-test.yml) and resets the schema before every test so tests never
 * depend on execution order or leak state across each other. This connects to an actual Postgres
 * instance (local service in dev/this sandbox, a `postgres:` service container in CI) rather than
 * Testcontainers, because Testcontainers requires a Docker daemon that is not available in every
 * environment this suite runs in — see DECISIONS.md "Test infrastructure."
 *
 * <p>{@link SimpleRateLimiter} is a singleton bean whose in-memory state would otherwise persist
 * across test methods within the same Spring context (unlike the database, which Flyway resets
 * below) — it is cleared here too so one test's rate-limit usage never affects another's.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(StatelessHttpClientTestConfig.class)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  @Autowired protected TestRestTemplate restTemplate;
  @Autowired protected Flyway flyway;
  @Autowired protected SimpleRateLimiter rateLimiter;
  @Autowired protected CapturingMailService capturingMailService;

  protected TestApiClient client;
  protected final ObjectMapper objectMapper = JsonMapper.builder().build();

  @BeforeEach
  void resetDatabaseAndClient() {
    flyway.clean();
    flyway.migrate();
    rateLimiter.clearAll();
    capturingMailService.clearAll();
    client = new TestApiClient(restTemplate);
  }

  /**
   * Drives the full double-opt-in flow (register, read the token back out of the captured email,
   * verify-with-password) and leaves {@code apiClient} holding an authenticated session, exactly as
   * a real user clicking the emailed link and setting a password would end up. This is the standard
   * way every test gets a logged-in staff session — there is no faster path, by design: see {@code
   * AuthController#register}'s class comment.
   */
  protected ResponseEntity<String> registerAndVerify(
      TestApiClient apiClient, String email, String displayName, String password) {
    // AuthController normalizes the email to lowercase before storing/sending; look it up the
    // same way so a mixed-case email in a test doesn't miss the captured message.
    String normalizedEmail = email.trim().toLowerCase();
    apiClient.post("/api/v1/auth/register", Map.of("email", email, "displayName", displayName));
    String token = capturingMailService.latestTokenFor(normalizedEmail);
    if (token == null) {
      throw new IllegalStateException("No verification email was captured for " + normalizedEmail);
    }
    return apiClient.post(
        "/api/v1/auth/email/verify", Map.of("token", token, "password", password));
  }

  protected JsonNode json(ResponseEntity<String> response) {
    return objectMapper.readTree(response.getBody());
  }

  /** Registers+verifies a fresh staff user, then creates an organization; returns the org id. */
  protected String registerVerifyAndCreateOrg(
      TestApiClient apiClient, String email, String displayName, String orgName) {
    registerAndVerify(apiClient, email, displayName, "correct-horse-battery");
    ResponseEntity<String> orgResponse =
        apiClient.post(
            "/api/v1/organizations",
            Map.of("name", orgName, "defaultTimezone", "America/Los_Angeles"));
    return json(orgResponse).get("id").asText();
  }

  /** Creates a draft event under the given org with the given timer mode; returns its id. */
  protected String createEvent(
      TestApiClient apiClient, String orgId, String name, String timerMode) {
    ResponseEntity<String> created =
        apiClient.post(
            "/api/v1/organizations/" + orgId + "/events",
            Map.of(
                "name", name,
                "description", "",
                "startsAt", "2027-01-01T00:00:00Z",
                "timezone", "America/Los_Angeles",
                "venueLabel", "Test venue",
                "timerMode", timerMode));
    return json(created).get("id").asText();
  }

  /** Same as {@link #createEvent}, also setting an explicit scramble policy. */
  protected String createEvent(
      TestApiClient apiClient, String orgId, String name, String timerMode, String scramblePolicy) {
    ResponseEntity<String> created =
        apiClient.post(
            "/api/v1/organizations/" + orgId + "/events",
            Map.of(
                "name", name,
                "description", "",
                "startsAt", "2027-01-01T00:00:00Z",
                "timezone", "America/Los_Angeles",
                "venueLabel", "Test venue",
                "timerMode", timerMode,
                "scramblePolicy", scramblePolicy));
    return json(created).get("id").asText();
  }

  protected String openRegistration(TestApiClient apiClient, String eventId) {
    ResponseEntity<String> response =
        apiClient.post("/api/v1/events/" + eventId + "/registration/open", null);
    return json(response).get("joinCode").asText();
  }

  protected String joinAsGuest(TestApiClient guestClient, String joinCode, String displayName) {
    ResponseEntity<String> response =
        guestClient.post("/api/v1/join/" + joinCode, Map.of("displayName", displayName));
    return json(response).get("entrant").get("id").asText();
  }

  protected String createRound(
      TestApiClient apiClient, String eventId, int order, String name, String format) {
    ResponseEntity<String> response =
        apiClient.post(
            "/api/v1/events/" + eventId + "/rounds",
            Map.of("order", order, "name", name, "format", format));
    return json(response).get("id").asText();
  }

  protected void prepareReadyStart(TestApiClient apiClient, String roundId) {
    apiClient.post("/api/v1/rounds/" + roundId + "/prepare", null);
    apiClient.post("/api/v1/rounds/" + roundId + "/ready", null);
    apiClient.post("/api/v1/rounds/" + roundId + "/start", null);
  }

  /** Like {@link #createRound} but also sets advancement rule/value, for M5 advancement tests. */
  protected String createRoundWithAdvancement(
      TestApiClient apiClient,
      String eventId,
      int order,
      String name,
      String format,
      String advancementRule,
      Integer advancementValue) {
    java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("order", order);
    body.put("name", name);
    body.put("format", format);
    body.put("advancementRule", advancementRule);
    if (advancementValue != null) {
      body.put("advancementValue", advancementValue);
    }
    ResponseEntity<String> response = apiClient.post("/api/v1/events/" + eventId + "/rounds", body);
    return json(response).get("id").asText();
  }

  /** Records an OK judge result for an attempt at its current version, returning that version. */
  protected void judgeOk(TestApiClient apiClient, String attemptId, long rawTimeMs) {
    long version = currentAttemptVersion(apiClient, attemptId);
    apiClient.put(
        "/api/v1/attempts/" + attemptId + "/judge-result",
        java.util.Map.of("status", "OK", "rawTimeMs", rawTimeMs, "expectedVersion", version));
  }

  protected long currentAttemptVersion(TestApiClient apiClient, String attemptId) {
    return json(apiClient.get("/api/v1/attempts/" + attemptId)).get("version").asLong();
  }
}

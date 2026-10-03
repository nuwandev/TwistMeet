package com.twistmeet.api.support;

import com.twistmeet.api.common.SimpleRateLimiter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

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
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  @Autowired protected TestRestTemplate restTemplate;
  @Autowired protected Flyway flyway;
  @Autowired protected SimpleRateLimiter rateLimiter;

  protected TestApiClient client;

  @BeforeEach
  void resetDatabaseAndClient() {
    flyway.clean();
    flyway.migrate();
    rateLimiter.clearAll();
    client = new TestApiClient(restTemplate);
  }
}

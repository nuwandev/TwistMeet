package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 12 security checklist: "CSRF/XSS/session... checks complete." SecurityConfig's own class comment
 * claims the session cookie is "secure, HttpOnly, SameSite" — found during this review to be untrue
 * (curling the dev server showed `JSESSIONID` carried only `HttpOnly`, and the CSRF cookie carried
 * neither attribute). Fixed in application-production.yml (session cookie) and SecurityConfig's
 * csrfTokenRepository customizer (CSRF cookie), both gated on the "production" profile so local/CI
 * dev over plain HTTP is unaffected (a `Secure` cookie is never sent over HTTP at all, which would
 * otherwise break every authenticated dev flow).
 *
 * <p>A separate Spring context from {@link com.twistmeet.api.support.AbstractIntegrationTest}'s
 * (different active profiles), so this boots its own context rather than extending it. A valid
 * scramble key is supplied via {@code twistmeet.scramble.encryption-key} so that {@code
 * ScrambleEncryptionService}'s now-production-required key check (see {@code
 * ScrambleEncryptionServiceTest}) doesn't block this context from starting.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles({"test", "production"})
@TestPropertySource(
    properties = "twistmeet.scramble.encryption-key=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
class SecurityCookieAttributesTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private Flyway flyway;
  @Autowired private com.twistmeet.api.support.CapturingMailService capturingMailService;

  @BeforeEach
  void resetDatabase() {
    flyway.clean();
    flyway.migrate();
    capturingMailService.clearAll();
  }

  @Test
  void csrfCookieIsSecureAndSameSiteUnderTheProductionProfile() {
    ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/me", String.class);
    List<String> setCookies = response.getHeaders().get("Set-Cookie");
    assertThat(setCookies).isNotNull();

    String csrfCookie =
        setCookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
    assertThat(csrfCookie).containsIgnoringCase("Secure");
    assertThat(csrfCookie).containsIgnoringCase("SameSite=Lax");
    // The CSRF cookie must stay readable by JavaScript — the web client reads it to echo back
    // as X-XSRF-TOKEN — so it must NOT carry HttpOnly.
    assertThat(csrfCookie).doesNotContainIgnoringCase("HttpOnly");
  }

  @Test
  void sessionCookieIsSecureAndSameSiteOnceASessionActuallyExists() throws Exception {
    // A session is only created once something is actually stored in it (Spring Security's lazy
    // session creation) — an anonymous GET never creates one. Registering+verifying establishes
    // a real authenticated session, exactly like AbstractIntegrationTest's registerAndVerify, but
    // inlined here since this test needs the raw verify response's headers, not just its cookies'
    // values (TestApiClient's cookie jar discards the Secure/SameSite attributes).
    String email = "cookie-check@example.com";
    restTemplate.postForEntity(
        "/api/v1/auth/register",
        Map.of("email", email, "displayName", "Cookie Checker"),
        String.class);
    String token = capturingMailService.latestTokenFor(email);
    assertThat(token).isNotNull();

    ResponseEntity<String> verifyResponse =
        restTemplate.postForEntity(
            "/api/v1/auth/email/verify",
            Map.of("token", token, "password", "correct-horse-battery"),
            String.class);
    assertThat(verifyResponse.getStatusCode().value()).isEqualTo(200);

    List<String> setCookies = verifyResponse.getHeaders().get("Set-Cookie");
    assertThat(setCookies).isNotNull();
    String sessionCookie =
        setCookies.stream().filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElseThrow();
    assertThat(sessionCookie).containsIgnoringCase("HttpOnly");
    assertThat(sessionCookie).containsIgnoringCase("Secure");
    assertThat(sessionCookie).containsIgnoringCase("SameSite=Lax");
  }
}

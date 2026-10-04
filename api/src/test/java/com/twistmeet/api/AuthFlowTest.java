package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twistmeet.api.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthFlowTest extends AbstractIntegrationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void registerThenLoginThenMeWorks() throws Exception {
    ResponseEntity<String> registerResponse =
        client.post(
            "/api/v1/auth/register",
            Map.of(
                "email",
                "alex@example.com",
                "password",
                "correct-horse-battery",
                "displayName",
                "Alex"));
    assertThat(registerResponse.getStatusCode().value()).isEqualTo(202);

    ResponseEntity<String> meBeforeLogin = client.get("/api/v1/me");
    assertThat(meBeforeLogin.getStatusCode().value()).isEqualTo(401);

    ResponseEntity<String> loginResponse =
        client.post(
            "/api/v1/auth/login",
            Map.of("email", "alex@example.com", "password", "correct-horse-battery"));
    assertThat(loginResponse.getStatusCode().value()).isEqualTo(200);

    ResponseEntity<String> meResponse = client.get("/api/v1/me");
    assertThat(meResponse.getStatusCode().value()).isEqualTo(200);
    JsonNode me = objectMapper.readTree(meResponse.getBody());
    assertThat(me.get("email").asText()).isEqualTo("alex@example.com");
    assertThat(me.get("emailVerified").asBoolean()).isFalse();
  }

  @Test
  void registrationDoesNotRevealWhetherTheEmailIsAlreadyRegistered() throws Exception {
    // Review finding: registration used to answer 201 for a new email and 409 EMAIL_IN_USE for
    // an existing one — two distinguishable outcomes a caller could use to enumerate which
    // emails are registered. The two REGISTER calls below (not login) must now be
    // indistinguishable: same status, same body, for a brand-new email and a duplicate one.
    ResponseEntity<String> registerNewEmail =
        client.post(
            "/api/v1/auth/register",
            Map.of(
                "email",
                "dup@example.com",
                "password",
                "correct-horse-battery",
                "displayName",
                "Dup"));
    client.clearCookies();

    ResponseEntity<String> registerSameEmailAgain =
        client.post(
            "/api/v1/auth/register",
            Map.of(
                "email", "dup@example.com", "password", "another-password", "displayName", "Dup2"));
    client.clearCookies();

    // This is the comparison the review asked for: the register endpoint's own status and body,
    // for the new-email case vs. the duplicate-email case, must be byte-for-byte identical.
    assertThat(registerSameEmailAgain.getStatusCode().value())
        .isEqualTo(registerNewEmail.getStatusCode().value());
    assertThat(registerSameEmailAgain.getBody()).isEqualTo(registerNewEmail.getBody());
    assertThat(registerSameEmailAgain.getStatusCode().value()).isEqualTo(202);

    JsonNode body = objectMapper.readTree(registerNewEmail.getBody());
    assertThat(body.has("id")).isFalse();
    assertThat(body.has("emailVerified")).isFalse();
    assertThat(body.has("createdAt")).isFalse();

    // Separately (this is LOGIN, a different endpoint, not part of the comparison above): the
    // second register call must not have taken over the account. The original password still
    // logs in, and the password submitted on the duplicate register attempt does not — because
    // it was never stored, not because login is somehow aware a duplicate registration happened.
    ResponseEntity<String> loginWithOriginalPassword =
        client.post(
            "/api/v1/auth/login",
            Map.of("email", "dup@example.com", "password", "correct-horse-battery"));
    assertThat(loginWithOriginalPassword.getStatusCode().value()).isEqualTo(200);
    client.clearCookies();

    ResponseEntity<String> loginWithUnstoredPassword =
        client.post(
            "/api/v1/auth/login",
            Map.of("email", "dup@example.com", "password", "another-password"));
    assertThat(loginWithUnstoredPassword.getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void registrationIsRateLimited() {
    String password = "correct-horse-battery";
    int attempts = 0;
    ResponseEntity<String> lastResponse = null;
    // The limiter allows 8 attempts per 15 minutes per client; the 9th must be refused.
    for (int i = 1; i <= 9; i++) {
      lastResponse =
          client.post(
              "/api/v1/auth/register",
              Map.of(
                  "email",
                  "rate-limit-register-" + i + "@example.com",
                  "password",
                  password,
                  "displayName",
                  "RL" + i));
      client.clearCookies();
      attempts++;
      if (lastResponse.getStatusCode().value() == 429) {
        break;
      }
    }
    assertThat(attempts).isLessThanOrEqualTo(9);
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(429);
    assertThat(lastResponse.getBody()).contains("RATE_LIMITED");
  }

  @Test
  void emailVerificationIsRateLimited() {
    ResponseEntity<String> lastResponse = null;
    // The limiter allows 10 attempts per 15 minutes per client; the 11th must be refused, even
    // though every token here is invalid (rate limiting applies before the token is checked).
    for (int i = 1; i <= 11; i++) {
      lastResponse =
          client.post("/api/v1/auth/email/verify", Map.of("token", "not-a-real-token-" + i));
      if (lastResponse.getStatusCode().value() == 429) {
        break;
      }
      assertThat(lastResponse.getStatusCode().value()).isEqualTo(400);
    }
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(429);
    assertThat(lastResponse.getBody()).contains("RATE_LIMITED");
  }

  @Test
  void loginWithWrongPasswordIsRejected() {
    client.post(
        "/api/v1/auth/register",
        Map.of(
            "email",
            "wrongpass@example.com",
            "password",
            "correct-horse-battery",
            "displayName",
            "WP"));
    client.clearCookies();

    ResponseEntity<String> loginResponse =
        client.post(
            "/api/v1/auth/login",
            Map.of("email", "wrongpass@example.com", "password", "not-the-password"));
    assertThat(loginResponse.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  void logoutEndsTheSession() {
    client.post(
        "/api/v1/auth/register",
        Map.of(
            "email",
            "logout@example.com",
            "password",
            "correct-horse-battery",
            "displayName",
            "Lo"));
    client.post(
        "/api/v1/auth/login",
        Map.of("email", "logout@example.com", "password", "correct-horse-battery"));
    assertThat(client.get("/api/v1/me").getStatusCode().value()).isEqualTo(200);

    client.post("/api/v1/auth/logout", null);
    assertThat(client.get("/api/v1/me").getStatusCode().value()).isEqualTo(401);
  }
}

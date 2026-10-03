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
    assertThat(registerResponse.getStatusCode().value()).isEqualTo(201);

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
  void duplicateRegistrationIsRejected() {
    client.post(
        "/api/v1/auth/register",
        Map.of(
            "email", "dup@example.com", "password", "correct-horse-battery", "displayName", "Dup"));
    client.clearCookies();

    ResponseEntity<String> secondAttempt =
        client.post(
            "/api/v1/auth/register",
            Map.of(
                "email", "dup@example.com", "password", "another-password", "displayName", "Dup2"));
    assertThat(secondAttempt.getStatusCode().value()).isEqualTo(409);
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

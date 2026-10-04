package com.twistmeet.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.twistmeet.api.support.AbstractIntegrationTest;
import com.twistmeet.api.support.TestApiClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class AuthFlowTest extends AbstractIntegrationTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().build();

  @Test
  void registerVerifyThenLoginWorks() throws Exception {
    ResponseEntity<String> verifyResponse =
        registerAndVerify(client, "alex@example.com", "Alex", "correct-horse-battery");
    assertThat(verifyResponse.getStatusCode().value()).isEqualTo(200);
    JsonNode verifyBody = objectMapper.readTree(verifyResponse.getBody());
    assertThat(verifyBody.get("email").asText()).isEqualTo("alex@example.com");
    assertThat(verifyBody.get("emailVerified").asBoolean()).isTrue();

    // Verifying already established a session (see AuthController#verifyEmail).
    ResponseEntity<String> meAfterVerify = client.get("/api/v1/me");
    assertThat(meAfterVerify.getStatusCode().value()).isEqualTo(200);

    client.post("/api/v1/auth/logout", null);
    client.clearCookies();

    ResponseEntity<String> loginResponse =
        client.post(
            "/api/v1/auth/login",
            Map.of("email", "alex@example.com", "password", "correct-horse-battery"));
    assertThat(loginResponse.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void unverifiedRegistrationCannotLoginAndIsIndistinguishableFromNoAccountAtAll()
      throws Exception {
    // M1 follow-up review finding: after the earlier non-enumeration fix, a *newly registered
    // but unverified* account could still log in successfully (it existed as a User row with a
    // caller-chosen password), while a login attempt against an *existing verified* email with
    // the wrong password got 401 — still a distinguishing signal. The fix (double opt-in, no
    // User row before verification) means there is now no account at all to log into here.
    client.post(
        "/api/v1/auth/register",
        Map.of("email", "justregistered@example.com", "displayName", "JR"));
    client.clearCookies();

    ResponseEntity<String> loginAfterRegisterOnly =
        client.post(
            "/api/v1/auth/login",
            Map.of("email", "justregistered@example.com", "password", "whatever-password-123"));
    assertThat(loginAfterRegisterOnly.getStatusCode().value()).isEqualTo(401);
    client.clearCookies();

    // Compare against a login attempt for an email nobody has ever registered at all.
    ResponseEntity<String> loginForNeverRegisteredEmail =
        client.post(
            "/api/v1/auth/login",
            Map.of(
                "email",
                "never-registered-at-all@example.com",
                "password",
                "whatever-password-123"));
    assertThat(loginForNeverRegisteredEmail.getStatusCode().value())
        .isEqualTo(loginAfterRegisterOnly.getStatusCode().value());
    // Compare the error code/message, not the raw body: `requestId` legitimately differs per
    // request (08 API conventions), so a byte-for-byte body comparison would never pass.
    JsonNode afterRegisterOnlyBody = objectMapper.readTree(loginAfterRegisterOnly.getBody());
    JsonNode neverRegisteredBody = objectMapper.readTree(loginForNeverRegisteredEmail.getBody());
    assertThat(afterRegisterOnlyBody.get("code")).isEqualTo(neverRegisteredBody.get("code"));
    assertThat(afterRegisterOnlyBody.get("message")).isEqualTo(neverRegisteredBody.get("message"));
  }

  @Test
  void registrationDoesNotRevealWhetherTheEmailIsAlreadyVerified() throws Exception {
    // Review finding (original form): registration used to answer differently for a new email
    // vs. an already-registered one. The two REGISTER calls below must be indistinguishable:
    // same status, same body, for a brand-new email and an email with a verified account.
    registerAndVerify(client, "already-verified@example.com", "Already", "correct-horse-battery");
    client.clearCookies();

    ResponseEntity<String> registerExistingVerifiedEmail =
        client.post(
            "/api/v1/auth/register",
            Map.of("email", "already-verified@example.com", "displayName", "Impersonator"));
    client.clearCookies();

    ResponseEntity<String> registerBrandNewEmail =
        client.post(
            "/api/v1/auth/register",
            Map.of("email", "brand-new@example.com", "displayName", "New"));
    client.clearCookies();

    assertThat(registerExistingVerifiedEmail.getStatusCode().value())
        .isEqualTo(registerBrandNewEmail.getStatusCode().value());
    assertThat(registerExistingVerifiedEmail.getStatusCode().value()).isEqualTo(202);

    JsonNode existingBody = objectMapper.readTree(registerExistingVerifiedEmail.getBody());
    JsonNode newBody = objectMapper.readTree(registerBrandNewEmail.getBody());
    assertThat(existingBody.get("message")).isEqualTo(newBody.get("message"));
    assertThat(existingBody.has("id")).isFalse();
    assertThat(existingBody.has("emailVerified")).isFalse();

    // The "re-registration" attempt on the verified email must not have taken over the
    // account: the original password still logs in.
    ResponseEntity<String> loginWithOriginalPassword =
        client.post(
            "/api/v1/auth/login",
            Map.of("email", "already-verified@example.com", "password", "correct-horse-battery"));
    assertThat(loginWithOriginalPassword.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void aSecondRegistrationForAnUnverifiedEmailInvalidatesTheFirstLinkRatherThanStealingIt()
      throws Exception {
    // This is the specific hijack scenario the review called out: an attacker registers
    // someone else's email with an attacker-chosen display name, hoping the mailbox owner will
    // later click a link and accidentally activate an attacker-controlled account. Because the
    // password is only ever chosen at verification time (never at registration), the attacker
    // can never inject a password here — but we also verify the *first* registration's link
    // stops working once a second registration for the same still-unverified email arrives,
    // so an attacker can't keep an old link alive indefinitely after the real owner re-registers.
    String email = "contested@example.com";

    client.post("/api/v1/auth/register", Map.of("email", email, "displayName", "Attacker"));
    String firstToken = capturingMailService.latestTokenFor(email);
    client.clearCookies();

    client.post("/api/v1/auth/register", Map.of("email", email, "displayName", "RealOwner"));
    String secondToken = capturingMailService.latestTokenFor(email);
    client.clearCookies();

    assertThat(secondToken).isNotEqualTo(firstToken);

    // The superseded first link no longer works, for anyone, with any password.
    ResponseEntity<String> verifyWithFirstToken =
        client.post(
            "/api/v1/auth/email/verify",
            Map.of("token", firstToken, "password", "attacker-chosen-password"));
    assertThat(verifyWithFirstToken.getStatusCode().value()).isEqualTo(400);
    assertThat(verifyWithFirstToken.getBody()).contains("TOKEN_INVALID");
    client.clearCookies();

    // The real owner's own (second, current) link works, and the password is whatever THEY
    // type here — the account is created with their password and the latest display name,
    // never the attacker's.
    ResponseEntity<String> verifyWithSecondToken =
        client.post(
            "/api/v1/auth/email/verify",
            Map.of("token", secondToken, "password", "real-owner-password"));
    assertThat(verifyWithSecondToken.getStatusCode().value()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(verifyWithSecondToken.getBody());
    assertThat(body.get("displayName").asText()).isEqualTo("RealOwner");
    client.clearCookies();

    ResponseEntity<String> loginAsRealOwner =
        client.post(
            "/api/v1/auth/login", Map.of("email", email, "password", "real-owner-password"));
    assertThat(loginAsRealOwner.getStatusCode().value()).isEqualTo(200);
    client.clearCookies();

    ResponseEntity<String> loginAsAttacker =
        client.post(
            "/api/v1/auth/login", Map.of("email", email, "password", "attacker-chosen-password"));
    assertThat(loginAsAttacker.getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void verificationTokenIsSingleUse() throws Exception {
    String email = "single-use@example.com";
    client.post("/api/v1/auth/register", Map.of("email", email, "displayName", "SU"));
    String token = capturingMailService.latestTokenFor(email);
    client.clearCookies();

    ResponseEntity<String> firstUse =
        client.post(
            "/api/v1/auth/email/verify", Map.of("token", token, "password", "first-password"));
    assertThat(firstUse.getStatusCode().value()).isEqualTo(200);
    client.clearCookies();

    ResponseEntity<String> secondUse =
        client.post(
            "/api/v1/auth/email/verify", Map.of("token", token, "password", "second-password"));
    assertThat(secondUse.getStatusCode().value()).isEqualTo(400);
    assertThat(secondUse.getBody()).contains("TOKEN_INVALID");
  }

  @Test
  void registrationIsRateLimited() {
    int attempts = 0;
    ResponseEntity<String> lastResponse = null;
    // The limiter allows 8 attempts per 15 minutes per client; the 9th must be refused.
    for (int i = 1; i <= 9; i++) {
      lastResponse =
          client.post(
              "/api/v1/auth/register",
              Map.of(
                  "email", "rate-limit-register-" + i + "@example.com", "displayName", "RL" + i));
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
          client.post(
              "/api/v1/auth/email/verify",
              Map.of("token", "not-a-real-token-" + i, "password", "some-long-enough-password"));
      if (lastResponse.getStatusCode().value() == 429) {
        break;
      }
      assertThat(lastResponse.getStatusCode().value()).isEqualTo(400);
    }
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(429);
    assertThat(lastResponse.getBody()).contains("RATE_LIMITED");
  }

  @Test
  void loginWithWrongPasswordIsRejected() throws Exception {
    registerAndVerify(client, "wrongpass@example.com", "WP", "correct-horse-battery");
    client.clearCookies();

    ResponseEntity<String> loginResponse =
        client.post(
            "/api/v1/auth/login",
            Map.of("email", "wrongpass@example.com", "password", "not-the-password"));
    assertThat(loginResponse.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  void logoutEndsTheSession() throws Exception {
    TestApiClient fresh = new TestApiClient(restTemplate);
    registerAndVerify(fresh, "logout@example.com", "Lo", "correct-horse-battery");
    assertThat(fresh.get("/api/v1/me").getStatusCode().value()).isEqualTo(200);

    fresh.post("/api/v1/auth/logout", null);
    assertThat(fresh.get("/api/v1/me").getStatusCode().value()).isEqualTo(401);
  }
}

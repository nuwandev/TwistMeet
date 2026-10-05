package com.twistmeet.api.scramble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * 00 §7 "encrypted at rest"; 08 DB invariant "encryption keys managed outside DB." No full Spring
 * context needed — exercises the service directly with its {@code @PostConstruct} init called
 * manually, the same ephemeral-key path a real process takes when the env var is unset and the
 * "production" profile is not active.
 */
class ScrambleEncryptionServiceTest {

  private ScrambleEncryptionService service;

  @BeforeEach
  void setUp() throws Exception {
    service = newService(new MockEnvironment());
  }

  private ScrambleEncryptionService newService(MockEnvironment env) throws Exception {
    ScrambleEncryptionService s = new ScrambleEncryptionService(env);
    Method init = ScrambleEncryptionService.class.getDeclaredMethod("init");
    init.setAccessible(true);
    init.invoke(s);
    return s;
  }

  private void setConfiguredKey(ScrambleEncryptionService s, String value) throws Exception {
    Field field = ScrambleEncryptionService.class.getDeclaredField("configuredKeyBase64");
    field.setAccessible(true);
    field.set(s, value);
  }

  private MockEnvironment activeProfile(String profile) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(profile);
    return env;
  }

  @Test
  void roundTripsPlaintextExactly() {
    String plaintext = "R U R' U' F2 L D2 B U2 R' F L2 D' R2 U B2 L' F' D";
    ScrambleEncryptionService.Encrypted encrypted = service.encrypt(plaintext);
    assertThat(encrypted.ciphertextBase64()).isNotBlank();
    assertThat(encrypted.nonceBase64()).isNotBlank();
    assertThat(encrypted.ciphertextBase64()).doesNotContain(plaintext);

    String decrypted = service.decrypt(encrypted.ciphertextBase64(), encrypted.nonceBase64());
    assertThat(decrypted).isEqualTo(plaintext);
  }

  @Test
  void sameInputEncryptsDifferentlyEachTime() {
    String plaintext = "R U R' U'";
    var first = service.encrypt(plaintext);
    var second = service.encrypt(plaintext);
    // A fresh random nonce per call means the ciphertext differs even for identical plaintext —
    // otherwise two competitors with the same scramble would be distinguishable from ciphertext
    // alone, and repeated encryption would leak a pattern.
    assertThat(first.ciphertextBase64()).isNotEqualTo(second.ciphertextBase64());
    assertThat(first.nonceBase64()).isNotEqualTo(second.nonceBase64());
  }

  @Test
  void tamperedCiphertextFailsToDecrypt() {
    var encrypted = service.encrypt("R U R' U'");
    String tampered = encrypted.ciphertextBase64().equals("AA") ? "AB" : "AA";
    assertThatThrownBy(() -> service.decrypt(tampered, encrypted.nonceBase64()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void productionProfileWithNoKeyRefusesToStart() throws Exception {
    MockEnvironment prod = activeProfile("production");
    ScrambleEncryptionService prodService = new ScrambleEncryptionService(prod);
    Method init = ScrambleEncryptionService.class.getDeclaredMethod("init");
    init.setAccessible(true);
    assertThatThrownBy(() -> init.invoke(prodService))
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "TWISTMEET_SCRAMBLE_ENCRYPTION_KEY is required when the 'production' Spring profile"
                + " is active (32 random bytes, Base64, from your platform secret manager) —"
                + " refusing to start with no durable scramble encryption key.");
  }

  @Test
  void productionProfileWithAValidKeyStartsNormally() throws Exception {
    MockEnvironment prod = activeProfile("production");
    ScrambleEncryptionService prodService = new ScrambleEncryptionService(prod);
    setConfiguredKey(prodService, java.util.Base64.getEncoder().encodeToString(new byte[32]));
    Method init = ScrambleEncryptionService.class.getDeclaredMethod("init");
    init.setAccessible(true);
    init.invoke(prodService); // does not throw
    var encrypted = prodService.encrypt("R U R' U'");
    assertThat(prodService.decrypt(encrypted.ciphertextBase64(), encrypted.nonceBase64()))
        .isEqualTo("R U R' U'");
  }

  @Test
  void nonProductionProfileWithNoKeyStillFallsBackToAnEphemeralKey() throws Exception {
    // The default/dev/test profile set (no "production") must keep working without any key
    // configured — this is the existing, intentional dev-only fallback, unaffected by the new
    // production guard.
    ScrambleEncryptionService devService = newService(new MockEnvironment());
    var encrypted = devService.encrypt("R U R' U'");
    assertThat(devService.decrypt(encrypted.ciphertextBase64(), encrypted.nonceBase64()))
        .isEqualTo("R U R' U'");
  }
}

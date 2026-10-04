package com.twistmeet.api.scramble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 00 §7 "encrypted at rest"; 08 DB invariant "encryption keys managed outside DB." No Spring
 * context needed — exercises the service directly with its {@code @PostConstruct} init called
 * manually, the same ephemeral-key path a real process takes when the env var is unset.
 */
class ScrambleEncryptionServiceTest {

  private ScrambleEncryptionService service;

  @BeforeEach
  void setUp() throws Exception {
    service = new ScrambleEncryptionService();
    Method init = ScrambleEncryptionService.class.getDeclaredMethod("init");
    init.setAccessible(true);
    init.invoke(service);
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
}

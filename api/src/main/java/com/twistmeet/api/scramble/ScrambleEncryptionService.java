package com.twistmeet.api.scramble;

import jakarta.annotation.PostConstruct;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

/**
 * Scramble-notation encryption at rest (00 §7 "encrypted at rest"; 04 "manage keys outside the
 * database"; 08 DB invariant "encryption keys managed outside DB"). AES-256-GCM: a single
 * authenticated-encryption primitive, no separate MAC to manage.
 *
 * <p>The key comes from {@code TWISTMEET_SCRAMBLE_ENCRYPTION_KEY} (a 32-byte key, Base64-encoded) —
 * environment injection, per 00 §10 "store secrets in platform secret manager/environment
 * injection, never commit." If that variable is unset in any ordinary profile (local dev, CI,
 * tests), a random key is generated once per process at startup and a warning is logged: this keeps
 * local dev/CI working without any setup, but the key does not survive a restart, so anything
 * encrypted under it becomes permanently unreadable after one.
 *
 * <p><b>Isolated production guard:</b> that ephemeral fallback must never silently apply to a real
 * deployment, where restart-induced key loss would make real scramble data permanently unreadable
 * (or, worse, force every scramble to be regenerated mid-event). An operator launches a real
 * deployment with the Spring profile {@code production} active (e.g. {@code
 * SPRING_PROFILES_ACTIVE=production}); when that profile is active, a missing or invalid key fails
 * application startup outright (no fallback, no ephemeral key, no silent degradation) — see {@code
 * application-production.yml} and the README's deployment section.
 */
@Service
public class ScrambleEncryptionService {

  private static final Logger log = LoggerFactory.getLogger(ScrambleEncryptionService.class);
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_TAG_LENGTH_BITS = 128;
  private static final int NONCE_LENGTH_BYTES = 12;
  private static final int KEY_LENGTH_BYTES = 32;

  @Value("${twistmeet.scramble.encryption-key:}")
  private String configuredKeyBase64;

  private final Environment environment;
  private SecretKey key;
  private final SecureRandom secureRandom = new SecureRandom();

  public ScrambleEncryptionService(Environment environment) {
    this.environment = environment;
  }

  @PostConstruct
  void init() {
    boolean isProduction = environment.acceptsProfiles(Profiles.of("production"));
    if (configuredKeyBase64 != null && !configuredKeyBase64.isBlank()) {
      byte[] decoded;
      try {
        decoded = Base64.getDecoder().decode(configuredKeyBase64.trim());
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException("TWISTMEET_SCRAMBLE_ENCRYPTION_KEY is not valid Base64", e);
      }
      if (decoded.length != KEY_LENGTH_BYTES) {
        throw new IllegalStateException(
            "TWISTMEET_SCRAMBLE_ENCRYPTION_KEY must decode to exactly 32 bytes");
      }
      key = new SecretKeySpec(decoded, "AES");
      return;
    }
    if (isProduction) {
      // Fail startup outright — never fall back to an ephemeral key for a real deployment. A
      // scramble encrypted under a key that disappears on restart is a real, not hypothetical,
      // risk: a container restart mid-event would make every already-prepared scramble batch
      // permanently undecryptable.
      throw new IllegalStateException(
          "TWISTMEET_SCRAMBLE_ENCRYPTION_KEY is required when the 'production' Spring profile is"
              + " active (32 random bytes, Base64, from your platform secret manager) — refusing"
              + " to start with no durable scramble encryption key.");
    }
    byte[] ephemeral = new byte[KEY_LENGTH_BYTES];
    secureRandom.nextBytes(ephemeral);
    key = new SecretKeySpec(ephemeral, "AES");
    log.warn(
        "TWISTMEET_SCRAMBLE_ENCRYPTION_KEY is not set — using an ephemeral random key for this"
            + " process only (development/test only; the 'production' profile refuses to start"
            + " without a durable key). Scrambles encrypted now cannot be decrypted after a"
            + " restart. Set TWISTMEET_SCRAMBLE_ENCRYPTION_KEY (32 random bytes, Base64) via your"
            + " secret manager before any real event.");
  }

  public record Encrypted(String ciphertextBase64, String nonceBase64) {}

  public Encrypted encrypt(String plaintext) {
    try {
      byte[] nonce = new byte[NONCE_LENGTH_BYTES];
      secureRandom.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
      byte[] ciphertext =
          cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return new Encrypted(
          Base64.getEncoder().encodeToString(ciphertext),
          Base64.getEncoder().encodeToString(nonce));
    } catch (GeneralSecurityException e) {
      // Never let a stack trace carry ciphertext/nonce bytes that resemble plaintext context;
      // this message is static and contains no scramble-derived data.
      throw new IllegalStateException("Scramble encryption failed", e);
    }
  }

  public String decrypt(String ciphertextBase64, String nonceBase64) {
    try {
      byte[] nonce = Base64.getDecoder().decode(nonceBase64);
      byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
      byte[] plaintext = cipher.doFinal(ciphertext);
      return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Scramble decryption failed", e);
    }
  }
}

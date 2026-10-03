package com.twistmeet.api.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * High-entropy opaque secrets (guest credentials, email-verification/reset tokens, join codes).
 *
 * <p>08 database invariants: "Guest credentials are random >=128-bit secrets, stored hashed,
 * event-scoped, revocable and expiring." The same pattern is used here for verification tokens.
 * Hashing is deterministic (SHA-256, unsalted) rather than a slow password hash, because these
 * values must be looked up by exact match (e.g. a join code typed by a guest) rather than verified
 * against a single known user — this mirrors a join code being "invitation convenience, not a
 * password" (00 §7) while still keeping the plaintext value out of the database and logs.
 */
public final class SecretTokens {

  private static final SecureRandom RANDOM = new SecureRandom();

  private SecretTokens() {}

  /** A URL-safe, >=128-bit random secret for guest credentials and verification tokens. */
  public static String newOpaqueToken() {
    byte[] bytes = new byte[32]; // 256 bits
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * A short, human-typeable join/short code (uppercase letters + digits, ambiguous chars removed).
   */
  public static String newJoinCode() {
    String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    StringBuilder sb = new StringBuilder(8);
    for (int i = 0; i < 8; i++) {
      sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
    }
    return sb.toString();
  }

  public static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}

package com.twistmeet.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An email address that someone has asked to register, not yet proven and not yet a {@link User}.
 * Deliberately carries no password: see the class comment on {@code AuthController}'s
 * register/verify methods for why the password is chosen only at verification time, by whoever
 * controls the mailbox, rather than by whoever submitted the registration request.
 *
 * <p>Unique on {@code email}: a second registration request for the same still-unverified address
 * replaces this row (fresh token, new {@code displayName}), invalidating any outstanding token for
 * that address. This bounds how long an unconsumed link stays live to "since the most recent
 * registration request for this address," and lets a real user who lost their first email simply
 * try again.
 */
@Entity
@Table(name = "pending_registrations")
public class PendingRegistration {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected PendingRegistration() {}

  public PendingRegistration(
      String email, String displayName, String tokenHash, Instant expiresAt) {
    this.email = email;
    this.displayName = displayName;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  /** Replaces this row's claim in place for a fresh registration request on the same email. */
  public void reissue(String displayName, String tokenHash, Instant expiresAt) {
    this.displayName = displayName;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }
}

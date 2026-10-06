package com.twistmeet.api.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified = false;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  /**
   * 04 "`User`: staff identity, authentication settings, deletion state." Set once, by the user
   * themselves (self-service), never cleared — a flag for an operator to act on once a retention
   * policy exists (DATA_RETENTION_DECISIONS.md), not something that triggers deletion on its own.
   */
  @Column(name = "deletion_requested_at")
  private Instant deletionRequestedAt;

  @Version private long version;

  protected User() {}

  public User(String email, String passwordHash, String displayName) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public void markEmailVerified() {
    this.emailVerified = true;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getDeletionRequestedAt() {
    return deletionRequestedAt;
  }

  /** Idempotent: a repeat request leaves the original timestamp untouched. */
  public void requestDeletion() {
    if (deletionRequestedAt == null) {
      deletionRequestedAt = Instant.now();
    }
  }

  public long getVersion() {
    return version;
  }
}

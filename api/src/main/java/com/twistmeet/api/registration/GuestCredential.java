package com.twistmeet.api.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Event-scoped guest credential (04 "random high-entropy, short-lived, event-scoped credentials
 * stored securely"; 08 database invariants: "random >=128-bit secrets, stored hashed, event-scoped,
 * revocable and expiring").
 */
@Entity
@Table(name = "guest_credentials")
public class GuestCredential {

  @Id @GeneratedValue private UUID id;

  @Column(name = "entrant_id", nullable = false)
  private UUID entrantId;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  protected GuestCredential() {}

  public GuestCredential(UUID entrantId, UUID eventId, String tokenHash, Instant expiresAt) {
    this.entrantId = entrantId;
    this.eventId = eventId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEntrantId() {
    return entrantId;
  }

  public UUID getEventId() {
    return eventId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public boolean isValid(Instant now) {
    return revokedAt == null && now.isBefore(expiresAt);
  }
}

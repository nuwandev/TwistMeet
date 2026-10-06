package com.twistmeet.api.scramble;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The vault row: an encrypted scramble notation and nothing else that identifies who it's for.
 * Never serialized directly in any API response — {@code ScrambleService} decrypts the payload on
 * demand for an authorized caller and returns a separate DTO (08 DB invariant: "Scramble notation
 * encrypted separately from ordinary event data").
 */
@Entity
@Table(name = "scramble_secrets")
public class ScrambleSecret {

  @Id @GeneratedValue private UUID id;

  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  @Column(nullable = false)
  private String ciphertext;

  @Column(nullable = false)
  private String nonce;

  @Column(name = "is_extra", nullable = false)
  private boolean extra;

  @Column(nullable = false)
  private boolean consumed;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected ScrambleSecret() {}

  public ScrambleSecret(UUID batchId, String ciphertext, String nonce, boolean extra) {
    this.batchId = batchId;
    this.ciphertext = ciphertext;
    this.nonce = nonce;
    this.extra = extra;
  }

  public UUID getId() {
    return id;
  }

  public UUID getBatchId() {
    return batchId;
  }

  public String getCiphertext() {
    return ciphertext;
  }

  public String getNonce() {
    return nonce;
  }

  public boolean isExtra() {
    return extra;
  }

  public boolean isConsumed() {
    return consumed;
  }

  public void markConsumed() {
    this.consumed = true;
  }
}

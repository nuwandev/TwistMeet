package com.twistmeet.api.scramble;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One generated batch per round (00 §7); never holds notation itself — see {@link ScrambleSecret}.
 */
@Entity
@Table(name = "scramble_batches")
public class ScrambleBatch {

  @Id @GeneratedValue private UUID id;

  @Column(name = "round_id", nullable = false)
  private UUID roundId;

  @Column(name = "puzzle_type", nullable = false)
  private String puzzleType = "3x3x3";

  @Column(name = "generator_name", nullable = false)
  private String generatorName;

  @Column(name = "generator_version", nullable = false)
  private String generatorVersion;

  @Column(name = "ruleset_version", nullable = false)
  private String rulesetVersion;

  @Column(name = "extra_count", nullable = false)
  private int extraCount;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected ScrambleBatch() {}

  public ScrambleBatch(
      UUID roundId,
      String generatorName,
      String generatorVersion,
      String rulesetVersion,
      int extraCount,
      UUID createdBy) {
    this.roundId = roundId;
    this.generatorName = generatorName;
    this.generatorVersion = generatorVersion;
    this.rulesetVersion = rulesetVersion;
    this.extraCount = extraCount;
    this.createdBy = createdBy;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRoundId() {
    return roundId;
  }

  public String getPuzzleType() {
    return puzzleType;
  }

  public String getGeneratorName() {
    return generatorName;
  }

  public String getGeneratorVersion() {
    return generatorVersion;
  }

  public String getRulesetVersion() {
    return rulesetVersion;
  }

  public int getExtraCount() {
    return extraCount;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

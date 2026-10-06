package com.twistmeet.api.competition;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * The roster an advancement commit admits into a round (see {@link AdvancementService}). Absent
 * rows for a round mean "no restriction" — {@link RoundService#prepare} falls back to every active
 * event entrant, preserving exact M1-M4 behavior for a round nobody ever advanced into.
 */
@Entity
@Table(name = "round_qualified_entrants")
public class RoundQualifiedEntrant {

  @EmbeddedId private Key id;

  protected RoundQualifiedEntrant() {}

  public RoundQualifiedEntrant(UUID roundId, UUID entrantId) {
    this.id = new Key(roundId, entrantId);
  }

  public UUID getRoundId() {
    return id.roundId;
  }

  public UUID getEntrantId() {
    return id.entrantId;
  }

  @Embeddable
  public static class Key implements Serializable {
    @Column(name = "round_id")
    private UUID roundId;

    @Column(name = "entrant_id")
    private UUID entrantId;

    protected Key() {}

    public Key(UUID roundId, UUID entrantId) {
      this.roundId = roundId;
      this.entrantId = entrantId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key key)) return false;
      return Objects.equals(roundId, key.roundId) && Objects.equals(entrantId, key.entrantId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(roundId, entrantId);
    }
  }
}

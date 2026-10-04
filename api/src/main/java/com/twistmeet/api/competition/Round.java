package com.twistmeet.api.competition;

import com.twistmeet.api.scoring.RoundFormat;
import com.twistmeet.api.scoring.RulesetVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** 00 §6 round lifecycle; 02/09 format and advancement configuration. */
@Entity
@Table(name = "rounds")
public class Round {

  @Id @GeneratedValue private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "round_order", nullable = false)
  private int order;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RoundFormat format;

  @Enumerated(EnumType.STRING)
  @Column(name = "advancement_rule", nullable = false)
  private AdvancementRule advancementRule = AdvancementRule.EVERYONE;

  @Column(name = "advancement_value")
  private Integer advancementValue;

  @Enumerated(EnumType.STRING)
  @Column(name = "tie_policy", nullable = false)
  private TiePolicy tiePolicy = TiePolicy.SHARED_RANK;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RoundState state = RoundState.DRAFT;

  // S08 "pause new starts": a toggle independent of the formal state machine above (00 §6 has
  // no PAUSED state). 08 lists only a single /pause endpoint with no separate /resume, so pause
  // is implemented as an idempotent toggle: calling it again un-pauses. Documented in
  // DECISIONS.md as a deliberate simplification of an otherwise-unspecified control.
  @Column(nullable = false)
  private boolean paused = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "ruleset_version", nullable = false)
  private RulesetVersion rulesetVersion = RulesetVersion.V1;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  // 07 S08 Tournament Control: "time since round start." Set once, when the round transitions to
  // LIVE; null before that. Distinct from createdAt, which is when the round was configured.
  @Column(name = "started_at")
  private Instant startedAt;

  // M5 advancement commit (09/08). Null until AdvancementService.commit() succeeds exactly once;
  // present thereafter so a repeated commit call is detected and answered idempotently instead
  // of re-mutating the next round's qualified roster.
  @Column(name = "advancement_committed_at")
  private Instant advancementCommittedAt;

  @Column(name = "advancement_committed_by")
  private UUID advancementCommittedBy;

  @Column(name = "advancement_committed_count")
  private Integer advancementCommittedCount;

  @Version private long version;

  protected Round() {}

  public Round(
      UUID eventId,
      int order,
      String name,
      RoundFormat format,
      AdvancementRule advancementRule,
      Integer advancementValue,
      TiePolicy tiePolicy) {
    this.eventId = eventId;
    this.order = order;
    this.name = name;
    this.format = format;
    this.advancementRule = advancementRule == null ? AdvancementRule.EVERYONE : advancementRule;
    this.advancementValue = advancementValue;
    this.tiePolicy = tiePolicy == null ? TiePolicy.SHARED_RANK : tiePolicy;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public int getOrder() {
    return order;
  }

  public String getName() {
    return name;
  }

  public RoundFormat getFormat() {
    return format;
  }

  public AdvancementRule getAdvancementRule() {
    return advancementRule;
  }

  public Integer getAdvancementValue() {
    return advancementValue;
  }

  public TiePolicy getTiePolicy() {
    return tiePolicy;
  }

  public RoundState getState() {
    return state;
  }

  public void setState(RoundState state) {
    this.state = state;
    if (state == RoundState.LIVE && startedAt == null) {
      startedAt = Instant.now();
    }
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public boolean isPaused() {
    return paused;
  }

  public boolean togglePause() {
    this.paused = !this.paused;
    return this.paused;
  }

  public RulesetVersion getRulesetVersion() {
    return rulesetVersion;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getVersion() {
    return version;
  }

  public Instant getAdvancementCommittedAt() {
    return advancementCommittedAt;
  }

  public UUID getAdvancementCommittedBy() {
    return advancementCommittedBy;
  }

  public Integer getAdvancementCommittedCount() {
    return advancementCommittedCount;
  }

  public void markAdvancementCommitted(UUID actorUserId, int count) {
    this.advancementCommittedAt = Instant.now();
    this.advancementCommittedBy = actorUserId;
    this.advancementCommittedCount = count;
  }

  public void applyDraftEdits(
      String name,
      RoundFormat format,
      AdvancementRule advancementRule,
      Integer advancementValue,
      TiePolicy tiePolicy) {
    this.name = name;
    this.format = format;
    this.advancementRule = advancementRule == null ? AdvancementRule.EVERYONE : advancementRule;
    this.advancementValue = advancementValue;
    this.tiePolicy = tiePolicy == null ? TiePolicy.SHARED_RANK : tiePolicy;
  }
}

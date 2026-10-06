package com.twistmeet.api.event;

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

@Entity
@Table(name = "events")
public class Event {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false)
  private String name;

  private String description;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(nullable = false)
  private String timezone;

  @Column(name = "venue_label")
  private String venueLabel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EventVisibility visibility = EventVisibility.PRIVATE;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EventState state = EventState.DRAFT;

  @Column(name = "puzzle_type", nullable = false)
  private String puzzleType = "333";

  @Enumerated(EnumType.STRING)
  @Column(name = "timer_mode", nullable = false)
  private TimerMode timerMode = TimerMode.PHYSICAL_JUDGE;

  @Enumerated(EnumType.STRING)
  @Column(name = "scramble_policy", nullable = false)
  private ScramblePolicy scramblePolicy = ScramblePolicy.STAFF_PREPARED;

  // Join codes are "invitation convenience, not a password" (00 §7) — unlike a guest
  // credential or a password, the organizer must be able to redisplay the current plaintext
  // code at any time (QR/screen/short code), so it is not purely one-way hashed. The plaintext
  // column backs that display; joinCodeHash is a separate indexed lookup key (04 names the
  // entity field "join-code hash") used by the guest join endpoint so the raw value is not the
  // column actually queried against guest input.
  @Column(name = "join_code")
  private String joinCode;

  @Column(name = "join_code_hash", unique = true)
  private String joinCodeHash;

  // 00 §4: "In-app rules text is versioned and snapshotted into the event when registration
  // opens." A JSON string built from RulesetSnapshot; null until registration first opens.
  @Column(name = "ruleset_snapshot")
  private String rulesetSnapshot;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  // M5 publishing (08 `/events/{eventId}/publish`/`/unpublish`; 07 S13 "Hide by revoking
  // token"). publishedAt null means hidden; unpublish rotates publicSlug rather than merely
  // clearing publishedAt, so a previously shared link stops resolving immediately even if the
  // event is later re-published under a new slug (see DECISIONS.md).
  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "public_slug", unique = true)
  private String publicSlug;

  // 07 S12 "Public name masking option." Null/false = show real display names on the public
  // page (current default); true replaces each name with "Competitor N" there only — staff
  // views are never masked.
  @Column(name = "public_name_mask", nullable = false)
  private boolean publicNameMask = false;

  @Version private long version;

  protected Event() {}

  public Event(
      UUID organizationId,
      String name,
      String description,
      Instant startsAt,
      String timezone,
      String venueLabel,
      EventVisibility visibility,
      String joinCode,
      String joinCodeHash) {
    this.organizationId = organizationId;
    this.name = name;
    this.description = description;
    this.startsAt = startsAt;
    this.timezone = timezone;
    this.venueLabel = venueLabel;
    this.visibility = visibility;
    this.joinCode = joinCode;
    this.joinCodeHash = joinCodeHash;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public String getTimezone() {
    return timezone;
  }

  public String getVenueLabel() {
    return venueLabel;
  }

  public EventVisibility getVisibility() {
    return visibility;
  }

  public EventState getState() {
    return state;
  }

  public void setState(EventState state) {
    this.state = state;
  }

  public String getPuzzleType() {
    return puzzleType;
  }

  public TimerMode getTimerMode() {
    return timerMode;
  }

  public void setTimerMode(TimerMode timerMode) {
    this.timerMode = timerMode;
  }

  public ScramblePolicy getScramblePolicy() {
    return scramblePolicy;
  }

  public void setScramblePolicy(ScramblePolicy scramblePolicy) {
    this.scramblePolicy = scramblePolicy;
  }

  public String getJoinCode() {
    return joinCode;
  }

  public String getJoinCodeHash() {
    return joinCodeHash;
  }

  public String getRulesetSnapshot() {
    return rulesetSnapshot;
  }

  public void setRulesetSnapshot(String rulesetSnapshot) {
    this.rulesetSnapshot = rulesetSnapshot;
  }

  public void rotateJoinCode(String joinCode, String joinCodeHash) {
    this.joinCode = joinCode;
    this.joinCodeHash = joinCodeHash;
  }

  public void applyDraftEdits(String name, String description, String venueLabel) {
    this.name = name;
    this.description = description;
    this.venueLabel = venueLabel;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getVersion() {
    return version;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public String getPublicSlug() {
    return publicSlug;
  }

  public void publish(String slugIfAbsent) {
    if (this.publicSlug == null) {
      this.publicSlug = slugIfAbsent;
    }
    this.publishedAt = Instant.now();
  }

  /** Rotating the slug invalidates any previously shared public link immediately. */
  public void unpublish(String newSlug) {
    this.publishedAt = null;
    this.publicSlug = newSlug;
  }

  public boolean isPublicNameMask() {
    return publicNameMask;
  }

  public void setPublicNameMask(boolean publicNameMask) {
    this.publicNameMask = publicNameMask;
  }
}

package com.twistmeet.api.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String slug;

  @Column(name = "default_timezone", nullable = false)
  private String defaultTimezone;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Version private long version;

  protected Organization() {}

  public Organization(String name, String slug, String defaultTimezone) {
    this.name = name;
    this.slug = slug;
    this.defaultTimezone = defaultTimezone;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public String getDefaultTimezone() {
    return defaultTimezone;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getVersion() {
    return version;
  }
}

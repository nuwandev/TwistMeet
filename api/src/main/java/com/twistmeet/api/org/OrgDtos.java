package com.twistmeet.api.org;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class OrgDtos {

  private OrgDtos() {}

  public record CreateOrganizationRequest(
      @NotBlank @Size(min = 2, max = 120) String name,
      @NotBlank @Size(min = 2, max = 60) String defaultTimezone) {}

  public record OrganizationView(
      UUID id, String name, String slug, String defaultTimezone, Instant createdAt, long version) {
    public static OrganizationView of(Organization org) {
      return new OrganizationView(
          org.getId(),
          org.getName(),
          org.getSlug(),
          org.getDefaultTimezone(),
          org.getCreatedAt(),
          org.getVersion());
    }
  }
}

package com.twistmeet.api.org;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.AuditService;
import com.twistmeet.api.org.OrgDtos.CreateOrganizationRequest;
import com.twistmeet.api.org.OrgDtos.OrganizationView;
import jakarta.validation.Valid;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

  private final OrganizationRepository organizationRepository;
  private final OrganizationMembershipRepository membershipRepository;
  private final CurrentUserResolver currentUserResolver;
  private final AuditService auditService;

  public OrganizationController(
      OrganizationRepository organizationRepository,
      OrganizationMembershipRepository membershipRepository,
      CurrentUserResolver currentUserResolver,
      AuditService auditService) {
    this.organizationRepository = organizationRepository;
    this.membershipRepository = membershipRepository;
    this.currentUserResolver = currentUserResolver;
    this.auditService = auditService;
  }

  @PostMapping
  public ResponseEntity<OrganizationView> create(
      @Valid @RequestBody CreateOrganizationRequest request) {
    try {
      ZoneId.of(request.defaultTimezone());
    } catch (DateTimeException e) {
      throw ApiException.badRequest(
          "INVALID_TIMEZONE", "defaultTimezone must be a valid IANA zone id");
    }

    UUID userId = currentUserResolver.requireCurrentUserId();
    String slug = uniqueSlug(request.name());
    Organization org =
        organizationRepository.save(
            new Organization(request.name(), slug, request.defaultTimezone()));
    membershipRepository.save(new OrganizationMembership(org.getId(), userId, OrgRole.OWNER));
    auditService.recordStaffAction(
        org.getId(),
        null,
        userId,
        "ORGANIZATION_CREATED",
        "Organization",
        org.getId().toString(),
        null);
    return ResponseEntity.status(HttpStatus.CREATED).body(OrganizationView.of(org));
  }

  @GetMapping
  public List<OrganizationView> listMine() {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return membershipRepository.findByUserId(userId).stream()
        .map(OrganizationMembership::getOrganizationId)
        .distinct()
        .map(
            id ->
                organizationRepository
                    .findById(id)
                    .orElseThrow(() -> ApiException.notFound("Organization not found")))
        .map(OrganizationView::of)
        .toList();
  }

  private String uniqueSlug(String name) {
    String base =
        name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    if (base.isBlank()) {
      base = "org";
    }
    String candidate = base;
    int suffix = 2;
    while (organizationRepository.existsBySlug(candidate)) {
      candidate = base + "-" + suffix++;
    }
    return candidate;
  }
}

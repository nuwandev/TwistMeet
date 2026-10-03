package com.twistmeet.api.registration;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.org.TenantAccessService;
import com.twistmeet.api.registration.RegistrationDtos.EntrantView;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal organizer-facing roster read. Not one of the 62 routes enumerated in {@code
 * 08-data-api-contract.md} (08 specifies entrant create/edit/check-in/withdraw/delete but no
 * explicit roster-list GET) — added because the organizer otherwise has no way to see who has
 * joined, which the screens in {@code 07} (S05 roster) plainly assume exists. Documented in
 * DECISIONS.md as a minimal, safer-simpler addition per 00 §12, not a scope expansion: it only
 * exposes data the organizer is already authorized to see via other means.
 */
@RestController
public class RosterController {

  private final EventRepository eventRepository;
  private final EventEntrantRepository entrantRepository;
  private final TenantAccessService tenantAccessService;
  private final CurrentUserResolver currentUserResolver;

  public RosterController(
      EventRepository eventRepository,
      EventEntrantRepository entrantRepository,
      TenantAccessService tenantAccessService,
      CurrentUserResolver currentUserResolver) {
    this.eventRepository = eventRepository;
    this.entrantRepository = entrantRepository;
    this.tenantAccessService = tenantAccessService;
    this.currentUserResolver = currentUserResolver;
  }

  @GetMapping("/api/v1/events/{eventId}/entrants")
  public List<EntrantView> list(@PathVariable UUID eventId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> ApiException.notFound("Event not found"));
    tenantAccessService.requireAnyStaffRole(event.getOrganizationId(), userId);
    return entrantRepository.findByEventId(eventId).stream().map(EntrantView::of).toList();
  }
}

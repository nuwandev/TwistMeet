package com.twistmeet.api.registration;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.org.TenantAccessService;
import com.twistmeet.api.registration.RegistrationDtos.AddEntrantRequest;
import com.twistmeet.api.registration.RegistrationDtos.EntrantTombstone;
import com.twistmeet.api.registration.RegistrationDtos.EntrantView;
import com.twistmeet.api.registration.RegistrationDtos.UpdateEntrantRequest;
import com.twistmeet.api.registration.RegistrationDtos.WithdrawRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organizer-facing roster read (M1) plus full roster management (M2: add/edit/check-in/
 * withdraw/remove — 00 §2.4, 07 S05). The plain roster-list GET is not one of 08's enumerated
 * routes (see this class's original M1 comment, preserved in DECISIONS.md); the M2 additions below
 * mostly map directly onto 08's enumerated entrant routes.
 */
@RestController
public class RosterController {

  private final EventRepository eventRepository;
  private final EventEntrantRepository entrantRepository;
  private final RosterService rosterService;
  private final TenantAccessService tenantAccessService;
  private final CurrentUserResolver currentUserResolver;

  public RosterController(
      EventRepository eventRepository,
      EventEntrantRepository entrantRepository,
      RosterService rosterService,
      TenantAccessService tenantAccessService,
      CurrentUserResolver currentUserResolver) {
    this.eventRepository = eventRepository;
    this.entrantRepository = entrantRepository;
    this.rosterService = rosterService;
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

  @PostMapping("/api/v1/events/{eventId}/entrants")
  public ResponseEntity<EntrantView> add(
      @PathVariable UUID eventId, @Valid @RequestBody AddEntrantRequest request) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    EventEntrant entrant = rosterService.add(eventId, userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(EntrantView.of(entrant));
  }

  @PatchMapping("/api/v1/events/{eventId}/entrants/{entrantId}")
  public EntrantView update(
      @PathVariable UUID eventId,
      @PathVariable UUID entrantId,
      @Valid @RequestBody UpdateEntrantRequest request) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return EntrantView.of(rosterService.update(eventId, entrantId, userId, request));
  }

  @PostMapping("/api/v1/events/{eventId}/entrants/{entrantId}/check-in")
  public EntrantView checkIn(@PathVariable UUID eventId, @PathVariable UUID entrantId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return EntrantView.of(rosterService.checkIn(eventId, entrantId, userId));
  }

  @PostMapping("/api/v1/events/{eventId}/entrants/{entrantId}/withdraw")
  public EntrantView withdraw(
      @PathVariable UUID eventId,
      @PathVariable UUID entrantId,
      @RequestBody(required = false) WithdrawRequest request) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    String reason = request == null ? null : request.reason();
    return EntrantView.of(rosterService.withdraw(eventId, entrantId, userId, reason));
  }

  @DeleteMapping("/api/v1/events/{eventId}/entrants/{entrantId}")
  public EntrantTombstone remove(@PathVariable UUID eventId, @PathVariable UUID entrantId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return rosterService.remove(eventId, entrantId, userId);
  }
}

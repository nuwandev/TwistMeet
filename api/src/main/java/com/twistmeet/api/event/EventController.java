package com.twistmeet.api.event;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.event.EventDtos.CreateEventRequest;
import com.twistmeet.api.event.EventDtos.EventView;
import com.twistmeet.api.event.EventDtos.UpdateEventRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EventController {

  private final EventService eventService;
  private final CurrentUserResolver currentUserResolver;

  public EventController(EventService eventService, CurrentUserResolver currentUserResolver) {
    this.eventService = eventService;
    this.currentUserResolver = currentUserResolver;
  }

  @PostMapping("/organizations/{orgId}/events")
  public ResponseEntity<EventView> create(
      @PathVariable UUID orgId, @Valid @RequestBody CreateEventRequest request) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    Event event = eventService.create(orgId, userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(EventView.of(event, true));
  }

  @GetMapping("/organizations/{orgId}/events")
  public List<EventView> listForOrganization(@PathVariable UUID orgId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return eventService.listForOrganization(orgId, userId).stream()
        .map(e -> EventView.of(e, true))
        .toList();
  }

  @GetMapping("/events/{eventId}")
  public EventView get(@PathVariable UUID eventId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return EventView.of(eventService.getForStaff(eventId, userId), true);
  }

  @PatchMapping("/events/{eventId}")
  public EventView update(
      @PathVariable UUID eventId, @Valid @RequestBody UpdateEventRequest request) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return EventView.of(eventService.update(eventId, userId, request), true);
  }

  @PostMapping("/events/{eventId}/registration/open")
  public EventView openRegistration(@PathVariable UUID eventId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return EventView.of(eventService.openRegistration(eventId, userId), true);
  }

  @PostMapping("/events/{eventId}/registration/lock")
  public EventView lockRegistration(@PathVariable UUID eventId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return EventView.of(eventService.lockRegistration(eventId, userId), true);
  }

  @PostMapping("/events/{eventId}/registration/reopen")
  public EventView reopenRegistration(@PathVariable UUID eventId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return EventView.of(eventService.reopenRegistration(eventId, userId), true);
  }

  @PostMapping("/events/{eventId}/join-codes/rotate")
  public EventView rotateJoinCode(@PathVariable UUID eventId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return EventView.of(eventService.rotateJoinCode(eventId, userId), true);
  }
}

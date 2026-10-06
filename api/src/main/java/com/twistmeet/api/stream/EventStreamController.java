package com.twistmeet.api.stream;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.org.TenantAccessService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 08 "Real-time updates". The staff channel requires an authenticated session with some recognized
 * relationship to the event — the same anti-enumeration rule as every other event-scoped read
 * ({@link TenantAccessService#resolveStaffRole} 404s a total stranger, same as every {@code
 * require*} check in that class). The public channel requires only that the event be currently
 * published, exactly matching {@code PublicController}'s own boundary; both live under {@code
 * /api/v1/public/**}, which {@code SecurityConfig} already leaves open to anonymous viewers.
 */
@RestController
public class EventStreamController {

  private final EventStreamService eventStreamService;
  private final EventRepository eventRepository;
  private final TenantAccessService tenantAccessService;
  private final CurrentUserResolver currentUserResolver;

  public EventStreamController(
      EventStreamService eventStreamService,
      EventRepository eventRepository,
      TenantAccessService tenantAccessService,
      CurrentUserResolver currentUserResolver) {
    this.eventStreamService = eventStreamService;
    this.eventRepository = eventRepository;
    this.tenantAccessService = tenantAccessService;
    this.currentUserResolver = currentUserResolver;
  }

  @GetMapping("/api/v1/events/{eventId}/stream")
  public SseEmitter streamStaff(@PathVariable UUID eventId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> ApiException.notFound("Event not found"));
    tenantAccessService.resolveStaffRole(event, userId);
    return eventStreamService.subscribeStaff(eventId);
  }

  @GetMapping("/api/v1/public/events/{publicSlug}/stream")
  public SseEmitter streamPublic(@PathVariable String publicSlug) {
    Event event =
        eventRepository
            .findByPublicSlug(publicSlug)
            .orElseThrow(() -> ApiException.notFound("Event not found"));
    if (event.getPublishedAt() == null) {
      throw ApiException.notFound("Event not found");
    }
    return eventStreamService.subscribePublic(publicSlug);
  }
}

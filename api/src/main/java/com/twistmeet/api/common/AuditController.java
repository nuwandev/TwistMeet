package com.twistmeet.api.common;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.org.TenantAccessService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 08: {@code GET /events/{eventId}/audit} owner/organizer restricted. */
@RestController
public class AuditController {

  private final AuditEventRepository auditEventRepository;
  private final EventRepository eventRepository;
  private final TenantAccessService tenantAccessService;
  private final CurrentUserResolver currentUserResolver;

  public AuditController(
      AuditEventRepository auditEventRepository,
      EventRepository eventRepository,
      TenantAccessService tenantAccessService,
      CurrentUserResolver currentUserResolver) {
    this.auditEventRepository = auditEventRepository;
    this.eventRepository = eventRepository;
    this.tenantAccessService = tenantAccessService;
    this.currentUserResolver = currentUserResolver;
  }

  public record AuditEntryView(
      UUID id,
      String actorType,
      UUID actorId,
      String action,
      String targetType,
      String targetId,
      String reason,
      Instant occurredAt) {
    static AuditEntryView of(AuditEvent e) {
      return new AuditEntryView(
          e.getId(),
          e.getActorType(),
          e.getActorId(),
          e.getAction(),
          e.getTargetType(),
          e.getTargetId(),
          e.getReason(),
          e.getOccurredAt());
    }
  }

  @GetMapping("/api/v1/events/{eventId}/audit")
  public List<AuditEntryView> list(@PathVariable UUID eventId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    Event event =
        eventRepository
            .findById(eventId)
            .orElseThrow(() -> ApiException.notFound("Event not found"));
    tenantAccessService.requireOrganizer(event, userId);
    return auditEventRepository.findByEventIdOrderByOccurredAtDesc(eventId).stream()
        .map(AuditEntryView::of)
        .toList();
  }
}

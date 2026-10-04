package com.twistmeet.api.event;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.auth.UserRepository;
import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.AuditService;
import com.twistmeet.api.event.EventStaffDtos.AssignStaffRequest;
import com.twistmeet.api.event.EventStaffDtos.EventStaffAssignmentView;
import com.twistmeet.api.org.TenantAccessService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal event-scoped staff (judge) assignment — not one of {@code 08}'s enumerated routes, added
 * because judge-level authorization cannot be meaningfully granted or tested otherwise (M1's
 * organization roles are both full-access; see {@code TenantAccessService} and DECISIONS.md).
 */
@RestController
public class EventStaffController {

  private final EventRepository eventRepository;
  private final EventStaffAssignmentRepository assignmentRepository;
  private final UserRepository userRepository;
  private final TenantAccessService tenantAccessService;
  private final CurrentUserResolver currentUserResolver;
  private final AuditService auditService;

  public EventStaffController(
      EventRepository eventRepository,
      EventStaffAssignmentRepository assignmentRepository,
      UserRepository userRepository,
      TenantAccessService tenantAccessService,
      CurrentUserResolver currentUserResolver,
      AuditService auditService) {
    this.eventRepository = eventRepository;
    this.assignmentRepository = assignmentRepository;
    this.userRepository = userRepository;
    this.tenantAccessService = tenantAccessService;
    this.currentUserResolver = currentUserResolver;
    this.auditService = auditService;
  }

  @PostMapping("/api/v1/events/{eventId}/staff-assignments")
  @Transactional
  public ResponseEntity<EventStaffAssignmentView> assign(
      @PathVariable UUID eventId, @Valid @RequestBody AssignStaffRequest request) {
    UUID actorUserId = currentUserResolver.requireCurrentUserId();
    Event event = findOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    if (!userRepository.existsById(request.userId())) {
      throw ApiException.badRequest("USER_NOT_FOUND", "No such user");
    }
    EventStaffAssignment assignment =
        assignmentRepository.save(
            new EventStaffAssignment(eventId, request.userId(), request.role()));
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "EVENT_STAFF_ASSIGNED",
        "EventStaffAssignment",
        assignment.getId().toString(),
        null);
    return ResponseEntity.status(HttpStatus.CREATED).body(EventStaffAssignmentView.of(assignment));
  }

  @GetMapping("/api/v1/events/{eventId}/staff-assignments")
  public List<EventStaffAssignmentView> list(@PathVariable UUID eventId) {
    UUID actorUserId = currentUserResolver.requireCurrentUserId();
    Event event = findOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    return assignmentRepository.findByEventId(eventId).stream()
        .map(EventStaffAssignmentView::of)
        .toList();
  }

  @DeleteMapping("/api/v1/events/{eventId}/staff-assignments/{assignmentId}")
  @Transactional
  public ResponseEntity<Void> revoke(@PathVariable UUID eventId, @PathVariable UUID assignmentId) {
    UUID actorUserId = currentUserResolver.requireCurrentUserId();
    Event event = findOrNotFound(eventId);
    tenantAccessService.requireOrganizer(event, actorUserId);
    EventStaffAssignment assignment =
        assignmentRepository
            .findByIdAndEventId(assignmentId, eventId)
            .orElseThrow(() -> ApiException.notFound("Assignment not found"));
    assignmentRepository.delete(assignment);
    auditService.recordStaffAction(
        event.getOrganizationId(),
        eventId,
        actorUserId,
        "EVENT_STAFF_REVOKED",
        "EventStaffAssignment",
        assignmentId.toString(),
        null);
    return ResponseEntity.noContent().build();
  }

  private Event findOrNotFound(UUID eventId) {
    return eventRepository
        .findById(eventId)
        .orElseThrow(() -> ApiException.notFound("Event not found"));
  }
}

package com.twistmeet.api.common;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

  private final AuditEventRepository repository;

  public AuditService(AuditEventRepository repository) {
    this.repository = repository;
  }

  public void recordStaffAction(
      UUID organizationId,
      UUID eventId,
      UUID actorUserId,
      String action,
      String targetType,
      String targetId,
      String reason) {
    repository.save(
        new AuditEvent(
            organizationId, eventId, "STAFF", actorUserId, action, targetType, targetId, reason));
  }

  public void recordGuestAction(
      UUID eventId, UUID entrantId, String action, String targetType, String targetId) {
    repository.save(
        new AuditEvent(null, eventId, "GUEST", entrantId, action, targetType, targetId, null));
  }

  /**
   * A staff member's own self-service action (e.g. export/deletion request), not scoped to any one
   * organization or event.
   */
  public void recordSelfServiceAction(
      UUID actorUserId, String action, String targetType, String targetId) {
    repository.save(
        new AuditEvent(null, null, "STAFF", actorUserId, action, targetType, targetId, null));
  }
}

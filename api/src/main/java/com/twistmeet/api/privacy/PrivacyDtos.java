package com.twistmeet.api.privacy;

import com.twistmeet.api.auth.AuthDtos.UserView;
import com.twistmeet.api.competition.AttemptDtos.AttemptView;
import com.twistmeet.api.competition.CorrectionDtos.CorrectionView;
import com.twistmeet.api.help.HelpRequestDtos.HelpRequestView;
import com.twistmeet.api.registration.RegistrationDtos.EntrantView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 04 "Give organizers export and deletion processes." Self-service export views: everything a
 * person can see is only what they themselves already have standing to read elsewhere in the API
 * (own staff profile/memberships/assignments, or own guest entrant/attempts/corrections/help
 * requests) — this just collects it into one downloadable response.
 */
public final class PrivacyDtos {

  private PrivacyDtos() {}

  public record OrganizationMembershipExport(
      UUID organizationId, String organizationName, String role, Instant createdAt) {}

  public record EventStaffAssignmentExport(
      UUID eventId, String eventName, String role, Instant assignedAt) {}

  public record MyDataExport(
      UserView profile,
      List<OrganizationMembershipExport> organizationMemberships,
      List<EventStaffAssignmentExport> eventStaffAssignments,
      Instant exportedAt) {}

  public record GuestDataExport(
      EntrantView entrant,
      List<AttemptView> attempts,
      List<CorrectionView> correctionsFiled,
      List<HelpRequestView> helpRequestsFiled,
      Instant exportedAt) {}
}

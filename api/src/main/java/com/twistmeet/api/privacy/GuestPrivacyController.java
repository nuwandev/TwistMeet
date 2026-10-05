package com.twistmeet.api.privacy;

import com.twistmeet.api.privacy.PrivacyDtos.GuestDataExport;
import com.twistmeet.api.registration.EventEntrant;
import com.twistmeet.api.registration.GuestAuthResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 04: a competitor's self-service export of their own entrant record, attempts, and requests. */
@RestController
public class GuestPrivacyController {

  private final GuestAuthResolver guestAuthResolver;
  private final PrivacyService privacyService;

  public GuestPrivacyController(
      GuestAuthResolver guestAuthResolver, PrivacyService privacyService) {
    this.guestAuthResolver = guestAuthResolver;
    this.privacyService = privacyService;
  }

  @GetMapping("/api/v1/guest/events/{eventId}/me/export")
  public GuestDataExport exportMyData(@PathVariable UUID eventId, HttpServletRequest request) {
    EventEntrant entrant = guestAuthResolver.requireEntrantForEvent(request, eventId);
    return privacyService.exportGuestData(entrant);
  }
}

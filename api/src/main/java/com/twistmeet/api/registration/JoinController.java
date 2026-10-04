package com.twistmeet.api.registration;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.AuditService;
import com.twistmeet.api.common.SecretTokens;
import com.twistmeet.api.common.SimpleRateLimiter;
import com.twistmeet.api.competition.AttemptRepository;
import com.twistmeet.api.competition.AttemptState;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.event.EventState;
import com.twistmeet.api.registration.RegistrationDtos.EntrantView;
import com.twistmeet.api.registration.RegistrationDtos.JoinRequest;
import com.twistmeet.api.registration.RegistrationDtos.JoinResponse;
import com.twistmeet.api.registration.RegistrationDtos.UpdateEntrantRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseCookie;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JoinController {

  private static final Duration CREDENTIAL_TTL = Duration.ofDays(30);

  private final EventRepository eventRepository;
  private final EventEntrantRepository entrantRepository;
  private final GuestCredentialRepository credentialRepository;
  private final GuestAuthResolver guestAuthResolver;
  private final AuditService auditService;
  private final SimpleRateLimiter rateLimiter;
  private final AttemptRepository attemptRepository;

  public JoinController(
      EventRepository eventRepository,
      EventEntrantRepository entrantRepository,
      GuestCredentialRepository credentialRepository,
      GuestAuthResolver guestAuthResolver,
      AuditService auditService,
      SimpleRateLimiter rateLimiter,
      AttemptRepository attemptRepository) {
    this.eventRepository = eventRepository;
    this.entrantRepository = entrantRepository;
    this.credentialRepository = credentialRepository;
    this.guestAuthResolver = guestAuthResolver;
    this.auditService = auditService;
    this.rateLimiter = rateLimiter;
    this.attemptRepository = attemptRepository;
  }

  @PostMapping("/api/v1/join/{joinCode}")
  @Transactional
  public JoinResponse join(
      @PathVariable String joinCode,
      @Valid @RequestBody JoinRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    String rateLimitKey = "join:" + httpRequest.getRemoteAddr();
    if (!rateLimiter.tryAcquire(rateLimitKey, 20, Duration.ofMinutes(15))) {
      throw ApiException.rateLimited("Too many join attempts, try again later");
    }

    String normalizedCode = joinCode.trim().toUpperCase();
    Event event =
        eventRepository
            .findByJoinCodeHash(SecretTokens.sha256Hex(normalizedCode))
            .filter(e -> e.getState() == EventState.REGISTRATION_OPEN)
            .orElseThrow(
                () -> ApiException.joinCodeInvalid("Join code is invalid, expired, or closed"));

    String displayName = DisplayNamePolicy.normalize(request.displayName());
    String disambiguated =
        DisplayNamePolicy.disambiguate(entrantRepository.findByEventId(event.getId()), displayName);

    EventEntrant entrant = entrantRepository.save(new EventEntrant(event.getId(), disambiguated));

    String rawToken = SecretTokens.newOpaqueToken();
    Instant expiresAt = Instant.now().plus(CREDENTIAL_TTL);
    credentialRepository.save(
        new GuestCredential(
            entrant.getId(), event.getId(), SecretTokens.sha256Hex(rawToken), expiresAt));
    auditService.recordGuestAction(
        event.getId(), entrant.getId(), "GUEST_JOINED", "EventEntrant", entrant.getId().toString());

    ResponseCookie cookie =
        ResponseCookie.from(GuestAuthResolver.COOKIE_NAME, rawToken)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(CREDENTIAL_TTL)
            .build();
    httpResponse.addHeader("Set-Cookie", cookie.toString());

    return new JoinResponse(event.getId(), event.getName(), EntrantView.of(entrant));
  }

  @GetMapping("/api/v1/guest/events/{eventId}/me")
  public EntrantView me(@PathVariable UUID eventId, HttpServletRequest request) {
    EventEntrant entrant = guestAuthResolver.requireEntrantForEvent(request, eventId);
    return EntrantView.of(entrant);
  }

  /** 08 row 24: "display name only before start" — before any of the entrant's attempts begin. */
  @PatchMapping("/api/v1/guest/events/{eventId}/me")
  @Transactional
  public EntrantView updateMe(
      @PathVariable UUID eventId,
      HttpServletRequest request,
      @Valid @RequestBody UpdateEntrantRequest body) {
    EventEntrant entrant = guestAuthResolver.requireEntrantForEvent(request, eventId);
    boolean anyStarted =
        attemptRepository.findByEntrantId(entrant.getId()).stream()
            .anyMatch(a -> a.getState() != AttemptState.PENDING);
    if (anyStarted) {
      throw ApiException.invalidTransition(
          "Display name can no longer be changed once attempts have started");
    }
    String displayName = DisplayNamePolicy.normalize(body.displayName());
    UUID entrantId = entrant.getId();
    List<EventEntrant> others =
        entrantRepository.findByEventId(eventId).stream()
            .filter(e -> !e.getId().equals(entrantId))
            .toList();
    entrant.rename(DisplayNamePolicy.disambiguate(others, displayName));
    entrant = entrantRepository.save(entrant);
    auditService.recordGuestAction(
        eventId, entrant.getId(), "GUEST_RENAMED_SELF", "EventEntrant", entrant.getId().toString());
    return EntrantView.of(entrant);
  }
}

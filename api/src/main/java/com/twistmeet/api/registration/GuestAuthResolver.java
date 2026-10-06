package com.twistmeet.api.registration;

import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.SecretTokens;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves the current guest's {@link EventEntrant} from the {@code tm_guest} cookie, scoped to one
 * event. A credential issued for event A must never resolve against event B — every caller passes
 * the event ID it expects and gets 404 (not 403, consistent with 08's anti-enumeration rule) if the
 * credential does not match.
 */
@Component
public class GuestAuthResolver {

  public static final String COOKIE_NAME = "tm_guest";

  private final GuestCredentialRepository credentialRepository;
  private final EventEntrantRepository entrantRepository;

  public GuestAuthResolver(
      GuestCredentialRepository credentialRepository, EventEntrantRepository entrantRepository) {
    this.credentialRepository = credentialRepository;
    this.entrantRepository = entrantRepository;
  }

  public EventEntrant requireEntrantForEvent(HttpServletRequest request, UUID eventId) {
    String rawToken = readCookie(request);
    if (rawToken == null) {
      throw ApiException.notFound("No guest session");
    }
    GuestCredential credential =
        credentialRepository
            .findByTokenHash(SecretTokens.sha256Hex(rawToken))
            .filter(c -> c.isValid(Instant.now()))
            .orElseThrow(() -> ApiException.notFound("No guest session"));
    if (!credential.getEventId().equals(eventId)) {
      // Deliberately the same 404 as "no session" — a guest credential for a different event
      // must not reveal that the requested event exists or that the credential is merely
      // scoped elsewhere.
      throw ApiException.notFound("No guest session");
    }
    return entrantRepository
        .findByIdAndEventId(credential.getEntrantId(), eventId)
        .orElseThrow(() -> ApiException.notFound("No guest session"));
  }

  private String readCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}

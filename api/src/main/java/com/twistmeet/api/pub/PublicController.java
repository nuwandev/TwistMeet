package com.twistmeet.api.pub;

import com.twistmeet.api.pub.PublicDtos.PublicEventView;
import com.twistmeet.api.pub.PublicDtos.PublicStandingsView;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated routes (SecurityConfig permits {@code /api/v1/public/**} without a session, same
 * as the guest/join/attempts paths).
 */
@RestController
public class PublicController {

  private final PublicDisplayService publicDisplayService;

  public PublicController(PublicDisplayService publicDisplayService) {
    this.publicDisplayService = publicDisplayService;
  }

  @GetMapping("/api/v1/public/events/{publicSlug}")
  public PublicEventView getEvent(@PathVariable String publicSlug) {
    return publicDisplayService.getEvent(publicSlug);
  }

  @GetMapping("/api/v1/public/events/{publicSlug}/standings")
  public PublicStandingsView getStandings(
      @PathVariable String publicSlug, @RequestParam UUID roundId) {
    return publicDisplayService.getStandings(publicSlug, roundId);
  }
}

package com.twistmeet.api.competition;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.competition.StandingsDtos.StandingsView;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StandingsController {

  private final StandingsService standingsService;
  private final CurrentUserResolver currentUserResolver;

  public StandingsController(
      StandingsService standingsService, CurrentUserResolver currentUserResolver) {
    this.standingsService = standingsService;
    this.currentUserResolver = currentUserResolver;
  }

  @GetMapping("/api/v1/rounds/{roundId}/standings")
  public StandingsView get(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return standingsService.compute(roundId, userId);
  }
}

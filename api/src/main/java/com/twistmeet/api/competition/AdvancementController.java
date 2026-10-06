package com.twistmeet.api.competition;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.competition.AdvancementDtos.AdvancedEntrant;
import com.twistmeet.api.competition.AdvancementDtos.AdvancementCommitView;
import com.twistmeet.api.competition.AdvancementDtos.AdvancementPreviewView;
import com.twistmeet.api.competition.AdvancementDtos.CommitRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdvancementController {

  private final AdvancementService advancementService;
  private final CurrentUserResolver currentUserResolver;

  public AdvancementController(
      AdvancementService advancementService, CurrentUserResolver currentUserResolver) {
    this.advancementService = advancementService;
    this.currentUserResolver = currentUserResolver;
  }

  @PostMapping("/api/v1/rounds/{roundId}/advancement/preview")
  public AdvancementPreviewView preview(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return advancementService.preview(roundId, userId);
  }

  @PostMapping("/api/v1/rounds/{roundId}/advancement/tie-break")
  public List<AdvancedEntrant> createTieBreakAttempts(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return advancementService.createTieBreakAttempts(roundId, userId);
  }

  @PostMapping("/api/v1/rounds/{roundId}/advancement/commit")
  public AdvancementCommitView commit(
      @PathVariable UUID roundId, @Valid @RequestBody CommitRequest body) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return advancementService.commit(roundId, userId, body);
  }
}

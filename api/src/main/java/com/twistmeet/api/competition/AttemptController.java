package com.twistmeet.api.competition;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.competition.AttemptDtos.AttemptView;
import com.twistmeet.api.competition.AttemptDtos.JudgeResultRequest;
import com.twistmeet.api.competition.AttemptDtos.ResultRevisionView;
import com.twistmeet.api.competition.AttemptDtos.SelfTimedSubmitRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttemptController {

  private final AttemptService attemptService;
  private final CurrentUserResolver currentUserResolver;

  public AttemptController(AttemptService attemptService, CurrentUserResolver currentUserResolver) {
    this.attemptService = attemptService;
    this.currentUserResolver = currentUserResolver;
  }

  /**
   * Minimal addition (not one of 08's enumerated routes, same spirit as {@code RosterController}):
   * judge/organizer attempt listing for a round, needed by the judge-entry and control-room UI
   * since 08's {@code control-state} snapshot endpoint is explicitly M3 scope (real-time, not built
   * this milestone).
   */
  /** Minimal addition for P04/P05/P07 (own attempt status, personal result/history). */
  @GetMapping("/api/v1/guest/events/{eventId}/me/attempts")
  public List<AttemptView> listOwn(@PathVariable UUID eventId, HttpServletRequest request) {
    return attemptService.listOwnAttempts(eventId, request).stream().map(AttemptView::of).toList();
  }

  @GetMapping("/api/v1/rounds/{roundId}/attempts")
  public List<AttemptView> listForRound(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return attemptService.listForRoundAuthorized(roundId, userId).stream()
        .map(AttemptView::of)
        .toList();
  }

  @GetMapping("/api/v1/attempts/{attemptId}")
  public AttemptView get(@PathVariable UUID attemptId, HttpServletRequest request) {
    UUID staffUserId = currentUserResolver.currentUserId().orElse(null);
    return AttemptView.of(attemptService.getAuthorized(attemptId, staffUserId, request));
  }

  @GetMapping("/api/v1/attempts/{attemptId}/revisions")
  public List<ResultRevisionView> revisions(@PathVariable UUID attemptId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return attemptService.history(attemptId, userId);
  }

  /** 07 S12 "Revisions" tab — every revision across the round, not just one attempt. */
  @GetMapping("/api/v1/rounds/{roundId}/revisions")
  public List<ResultRevisionView> revisionsForRound(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return attemptService.historyForRound(roundId, userId);
  }

  @PostMapping("/api/v1/attempts/{attemptId}/start")
  public AttemptView start(@PathVariable UUID attemptId, HttpServletRequest request) {
    return AttemptView.of(attemptService.start(attemptId, request));
  }

  @PostMapping("/api/v1/attempts/{attemptId}/stop")
  public AttemptView stop(@PathVariable UUID attemptId, HttpServletRequest request) {
    return AttemptView.of(attemptService.stop(attemptId, request));
  }

  @PostMapping("/api/v1/attempts/{attemptId}/submit")
  public AttemptView submit(
      @PathVariable UUID attemptId,
      HttpServletRequest request,
      @Valid @RequestBody SelfTimedSubmitRequest body) {
    return AttemptView.of(attemptService.submitSelfTimed(attemptId, request, body));
  }

  @PutMapping("/api/v1/attempts/{attemptId}/judge-result")
  public AttemptView judgeResult(
      @PathVariable UUID attemptId, @Valid @RequestBody JudgeResultRequest body) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return AttemptView.of(attemptService.recordJudgeResult(attemptId, userId, body));
  }
}

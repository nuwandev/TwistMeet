package com.twistmeet.api.competition;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.competition.RoundDtos.CreateRoundRequest;
import com.twistmeet.api.competition.RoundDtos.RoundView;
import com.twistmeet.api.competition.RoundDtos.UpdateRoundRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoundController {

  private final RoundService roundService;
  private final CurrentUserResolver currentUserResolver;

  public RoundController(RoundService roundService, CurrentUserResolver currentUserResolver) {
    this.roundService = roundService;
    this.currentUserResolver = currentUserResolver;
  }

  @PostMapping("/api/v1/events/{eventId}/rounds")
  public ResponseEntity<RoundView> create(
      @PathVariable UUID eventId, @Valid @RequestBody CreateRoundRequest request) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    Round round = roundService.create(eventId, userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(RoundView.of(round));
  }

  @GetMapping("/api/v1/events/{eventId}/rounds")
  public List<RoundView> list(@PathVariable UUID eventId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return roundService.listForEvent(eventId, userId).stream().map(RoundView::of).toList();
  }

  @GetMapping("/api/v1/rounds/{roundId}")
  public RoundView get(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return RoundView.of(roundService.getForStaff(roundId, userId));
  }

  @PatchMapping("/api/v1/rounds/{roundId}")
  public RoundView update(
      @PathVariable UUID roundId, @Valid @RequestBody UpdateRoundRequest request) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return RoundView.of(roundService.update(roundId, userId, request));
  }

  @PostMapping("/api/v1/rounds/{roundId}/prepare")
  public RoundView prepare(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return RoundView.of(roundService.prepare(roundId, userId));
  }

  @PostMapping("/api/v1/rounds/{roundId}/ready")
  public RoundView ready(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return RoundView.of(roundService.ready(roundId, userId));
  }

  @PostMapping("/api/v1/rounds/{roundId}/start")
  public RoundView start(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return RoundView.of(roundService.start(roundId, userId));
  }

  @PostMapping("/api/v1/rounds/{roundId}/pause")
  public RoundView pause(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return RoundView.of(roundService.togglePause(roundId, userId));
  }

  @PostMapping("/api/v1/rounds/{roundId}/review")
  public RoundView review(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return RoundView.of(roundService.review(roundId, userId));
  }

  @PostMapping("/api/v1/rounds/{roundId}/close")
  public RoundView close(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return RoundView.of(roundService.close(roundId, userId));
  }
}

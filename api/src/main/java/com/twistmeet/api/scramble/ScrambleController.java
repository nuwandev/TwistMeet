package com.twistmeet.api.scramble;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.scramble.ScrambleDtos.AssignmentView;
import com.twistmeet.api.scramble.ScrambleDtos.BatchView;
import com.twistmeet.api.scramble.ScrambleDtos.PrintEntry;
import com.twistmeet.api.scramble.ScrambleDtos.RevealView;
import com.twistmeet.api.scramble.ScrambleDtos.SpoilRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScrambleController {

  private final ScrambleService scrambleService;
  private final CurrentUserResolver currentUserResolver;

  public ScrambleController(
      ScrambleService scrambleService, CurrentUserResolver currentUserResolver) {
    this.scrambleService = scrambleService;
    this.currentUserResolver = currentUserResolver;
  }

  @PostMapping("/api/v1/rounds/{roundId}/scramble-batches")
  public ResponseEntity<BatchView> createBatch(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(scrambleService.createBatch(roundId, userId));
  }

  @GetMapping("/api/v1/rounds/{roundId}/scramble-assignments")
  public List<AssignmentView> listForRound(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return scrambleService.listForRound(roundId, userId);
  }

  @GetMapping("/api/v1/rounds/{roundId}/scramble-assignments/print")
  public List<PrintEntry> print(@PathVariable UUID roundId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return scrambleService.print(roundId, userId);
  }

  @PostMapping("/api/v1/scramble-assignments/{assignmentId}/reveal")
  public RevealView reveal(@PathVariable UUID assignmentId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return scrambleService.reveal(assignmentId, userId);
  }

  @GetMapping("/api/v1/scramble-assignments/{assignmentId}/official-view")
  public RevealView officialView(@PathVariable UUID assignmentId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return scrambleService.officialView(assignmentId, userId);
  }

  @PostMapping("/api/v1/scramble-assignments/{assignmentId}/mark-applied")
  public AssignmentView markApplied(@PathVariable UUID assignmentId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return scrambleService.markApplied(assignmentId, userId);
  }

  @PostMapping("/api/v1/scramble-assignments/{assignmentId}/mark-checked")
  public AssignmentView markChecked(
      @PathVariable UUID assignmentId, @RequestParam(defaultValue = "false") boolean independent) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return scrambleService.markChecked(assignmentId, userId, independent);
  }

  @PostMapping("/api/v1/scramble-assignments/{assignmentId}/spoil")
  public AssignmentView spoil(
      @PathVariable UUID assignmentId, @Valid @RequestBody SpoilRequest body) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return scrambleService.spoil(assignmentId, userId, body.reason());
  }

  /**
   * Self-scramble mode only; rides the guest cookie like every other {@code /attempts/**} route
   * (see SecurityConfig's {@code permitAll} comment) rather than a Spring Security principal.
   */
  @GetMapping("/api/v1/attempts/{attemptId}/current-scramble")
  public RevealView currentScramble(@PathVariable UUID attemptId, HttpServletRequest request) {
    return scrambleService.currentScrambleForGuest(attemptId, request);
  }
}

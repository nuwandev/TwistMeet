package com.twistmeet.api.help;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.help.HelpRequestDtos.HelpRequestView;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelpRequestController {

  private final HelpRequestService helpRequestService;
  private final CurrentUserResolver currentUserResolver;

  public HelpRequestController(
      HelpRequestService helpRequestService, CurrentUserResolver currentUserResolver) {
    this.helpRequestService = helpRequestService;
    this.currentUserResolver = currentUserResolver;
  }

  @PostMapping("/api/v1/attempts/{attemptId}/help-requests")
  public ResponseEntity<HelpRequestView> create(
      @PathVariable UUID attemptId, HttpServletRequest request) {
    HelpRequestView view = HelpRequestView.of(helpRequestService.create(attemptId, request));
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping("/api/v1/events/{eventId}/help-requests")
  public List<HelpRequestView> list(
      @PathVariable UUID eventId, @RequestParam(required = false) HelpRequestState state) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return helpRequestService.listForEvent(eventId, userId, state).stream()
        .map(HelpRequestView::of)
        .toList();
  }

  @PostMapping("/api/v1/help-requests/{helpRequestId}/resolve")
  public HelpRequestView resolve(@PathVariable UUID helpRequestId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return HelpRequestView.of(helpRequestService.resolve(helpRequestId, userId));
  }
}

package com.twistmeet.api.competition;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.competition.CorrectionDtos.CorrectionView;
import com.twistmeet.api.competition.CorrectionDtos.CreateCorrectionRequest;
import com.twistmeet.api.competition.CorrectionDtos.DecisionRequest;
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
public class CorrectionController {

  private final CorrectionService correctionService;
  private final CurrentUserResolver currentUserResolver;

  public CorrectionController(
      CorrectionService correctionService, CurrentUserResolver currentUserResolver) {
    this.correctionService = correctionService;
    this.currentUserResolver = currentUserResolver;
  }

  @PostMapping("/api/v1/attempts/{attemptId}/correction-requests")
  public ResponseEntity<CorrectionView> create(
      @PathVariable UUID attemptId,
      HttpServletRequest request,
      @Valid @RequestBody CreateCorrectionRequest body) {
    Correction correction = correctionService.create(attemptId, request, body);
    return ResponseEntity.status(HttpStatus.CREATED).body(CorrectionView.of(correction));
  }

  @GetMapping("/api/v1/events/{eventId}/corrections")
  public List<CorrectionView> list(
      @PathVariable UUID eventId, @RequestParam(required = false) CorrectionState state) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return correctionService.listForEvent(eventId, userId, state).stream()
        .map(CorrectionView::of)
        .toList();
  }

  @PostMapping("/api/v1/corrections/{correctionId}/decision")
  public CorrectionView decide(
      @PathVariable UUID correctionId, @Valid @RequestBody DecisionRequest body) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return CorrectionView.of(correctionService.decide(correctionId, userId, body));
  }
}

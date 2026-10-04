package com.twistmeet.api.history;

import com.twistmeet.api.auth.CurrentUserResolver;
import com.twistmeet.api.event.EventState;
import com.twistmeet.api.history.HistoryDtos.EventHistorySummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HistoryController {

  private final HistoryService historyService;
  private final CurrentUserResolver currentUserResolver;

  public HistoryController(HistoryService historyService, CurrentUserResolver currentUserResolver) {
    this.historyService = historyService;
    this.currentUserResolver = currentUserResolver;
  }

  @GetMapping("/api/v1/organizations/{orgId}/events/history")
  public List<EventHistorySummary> history(
      @PathVariable UUID orgId,
      @RequestParam(required = false) EventState state,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) String q) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    return historyService.list(orgId, userId, state, from, to, q);
  }

  @GetMapping("/api/v1/organizations/{orgId}/events/{eventId}/export.csv")
  public ResponseEntity<String> exportCsv(@PathVariable UUID orgId, @PathVariable UUID eventId) {
    UUID userId = currentUserResolver.requireCurrentUserId();
    String csv = historyService.exportCsv(orgId, eventId, userId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"event-export.csv\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(csv);
  }
}

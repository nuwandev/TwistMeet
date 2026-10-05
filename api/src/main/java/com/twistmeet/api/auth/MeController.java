package com.twistmeet.api.auth;

import com.twistmeet.api.auth.AuthDtos.UserView;
import com.twistmeet.api.privacy.PrivacyDtos.MyDataExport;
import com.twistmeet.api.privacy.PrivacyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

  private final CurrentUserResolver currentUserResolver;
  private final PrivacyService privacyService;

  public MeController(CurrentUserResolver currentUserResolver, PrivacyService privacyService) {
    this.currentUserResolver = currentUserResolver;
    this.privacyService = privacyService;
  }

  @GetMapping("/api/v1/me")
  public UserView me() {
    return UserView.of(currentUserResolver.requireCurrentUser());
  }

  /** 04: self-service export of everything this staff member can already see about themselves. */
  @GetMapping("/api/v1/me/export")
  public MyDataExport exportMyData() {
    return privacyService.exportMyData(currentUserResolver.requireCurrentUserId());
  }

  /**
   * 04: records a deletion request only — idempotent, performs no deletion. Acting on it is a
   * manual operator step once a retention policy exists (see DATA_RETENTION_DECISIONS.md).
   */
  @PostMapping("/api/v1/me/deletion-request")
  public UserView requestDeletion() {
    return privacyService.requestMyDeletion(currentUserResolver.requireCurrentUserId());
  }
}

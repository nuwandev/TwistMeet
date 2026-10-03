package com.twistmeet.api.auth;

import com.twistmeet.api.auth.AuthDtos.UserView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

  private final CurrentUserResolver currentUserResolver;

  public MeController(CurrentUserResolver currentUserResolver) {
    this.currentUserResolver = currentUserResolver;
  }

  @GetMapping("/api/v1/me")
  public UserView me() {
    return UserView.of(currentUserResolver.requireCurrentUser());
  }
}

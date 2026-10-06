package com.twistmeet.api.auth;

import com.twistmeet.api.common.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {

  private final UserRepository userRepository;

  public CurrentUserResolver(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public Optional<UUID> currentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
      return Optional.empty();
    }
    return Optional.of(principal.getUserId());
  }

  public UUID requireCurrentUserId() {
    return currentUserId().orElseThrow(() -> ApiException.forbidden("Sign-in required"));
  }

  public User requireCurrentUser() {
    UUID id = requireCurrentUserId();
    return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
  }
}

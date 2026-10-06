package com.twistmeet.api.auth;

import java.util.Collections;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Spring Security principal wrapping our {@link User}; carries the app-level user ID. */
public class AppUserPrincipal implements UserDetails {

  private final UUID userId;
  private final String email;
  private final String passwordHash;
  private final boolean emailVerified;

  public AppUserPrincipal(User user) {
    this.userId = user.getId();
    this.email = user.getEmail();
    this.passwordHash = user.getPasswordHash();
    this.emailVerified = user.isEmailVerified();
  }

  public UUID getUserId() {
    return userId;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  @Override
  public java.util.Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.emptyList();
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }
}

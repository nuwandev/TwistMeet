package com.twistmeet.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

  private AuthDtos() {}

  public record RegisterRequest(
      @NotBlank @Email String email,
      @NotBlank @Size(min = 10, max = 200) String password,
      @NotBlank @Size(min = 1, max = 120) String displayName) {}

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  public record VerifyEmailRequest(@NotBlank String token) {}

  /**
   * Response to {@code POST /auth/register}, deliberately identical whether or not the email was
   * already registered (12 SP04 "generic errors avoid account enumeration"). It echoes back only
   * the caller's own submitted email (not a leak — they already know it) and never includes account
   * fields such as id/emailVerified/createdAt, which would otherwise differ between a brand-new
   * account and a pre-existing one.
   */
  public record RegistrationAccepted(String email, String message) {}

  public record UserView(
      UUID id, String email, String displayName, boolean emailVerified, Instant createdAt) {
    public static UserView of(User user) {
      return new UserView(
          user.getId(),
          user.getEmail(),
          user.getDisplayName(),
          user.isEmailVerified(),
          user.getCreatedAt());
    }
  }
}

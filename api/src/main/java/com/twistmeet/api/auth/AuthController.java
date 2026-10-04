package com.twistmeet.api.auth;

import com.twistmeet.api.auth.AuthDtos.LoginRequest;
import com.twistmeet.api.auth.AuthDtos.RegisterRequest;
import com.twistmeet.api.auth.AuthDtos.UserView;
import com.twistmeet.api.auth.AuthDtos.VerifyEmailRequest;
import com.twistmeet.api.common.ApiException;
import com.twistmeet.api.common.SecretTokens;
import com.twistmeet.api.common.SimpleRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final UserRepository userRepository;
  private final PendingRegistrationRepository pendingRegistrationRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final MailService mailService;
  private final SimpleRateLimiter rateLimiter;
  private final String webBaseUrl;
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  public AuthController(
      UserRepository userRepository,
      PendingRegistrationRepository pendingRegistrationRepository,
      PasswordEncoder passwordEncoder,
      AuthenticationManager authenticationManager,
      MailService mailService,
      SimpleRateLimiter rateLimiter,
      Environment env) {
    this.userRepository = userRepository;
    this.pendingRegistrationRepository = pendingRegistrationRepository;
    this.passwordEncoder = passwordEncoder;
    this.authenticationManager = authenticationManager;
    this.mailService = mailService;
    this.rateLimiter = rateLimiter;
    this.webBaseUrl = env.getProperty("twistmeet.web.base-url", "http://localhost:3000");
  }

  /**
   * Double opt-in, and deliberately takes no password. M1 follow-up review finding: a single- step
   * register(email, password) immediately created a logged-in-capable (if unverified) account,
   * which let a caller distinguish a brand-new email from an already-registered one — either by the
   * old 201-vs-409 response, or (after that was fixed) by the fact that an unverified account the
   * caller just created could still log in while an existing account with a different password
   * could not. Both gaps share one root cause: a {@link User} row, with a caller-supplied password,
   * existed before anyone proved they control the mailbox.
   *
   * <p>Fix: nothing here ever creates a {@link User}. A fresh email is recorded only as a {@link
   * PendingRegistration} (no password) and a verification link is emailed; the account — and its
   * password — is created only in {@link #verifyEmail}, where the password is supplied by whoever
   * is completing the request at that moment, i.e. whoever clicked the link. This also closes the
   * specific hijack this review called out: an attacker who registers a victim's email can choose a
   * display name, but can never choose the account's password, because registration no longer
   * accepts one. If the victim later clicks any link for that address — including one the attacker
   * triggered — they are the one typing a password into the form that calls {@link #verifyEmail},
   * so they are the one who ends up controlling the account.
   */
  @PostMapping("/register")
  public ResponseEntity<AuthDtos.RegistrationAccepted> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
    String rateLimitKey = "register:" + httpRequest.getRemoteAddr();
    if (!rateLimiter.tryAcquire(rateLimitKey, 8, Duration.ofMinutes(15))) {
      throw ApiException.rateLimited("Too many registration attempts, try again later");
    }

    String email = request.email().trim().toLowerCase();
    if (!userRepository.existsByEmail(email)) {
      // A second, independent limit on the target address (not the caller's IP): this is what
      // actually bounds how many verification emails a victim's inbox can be made to receive,
      // regardless of how many different IPs an attacker spreads requests across. It only ever
      // affects whether an email gets sent, never the response below, so it adds no new
      // enumeration signal (see the "both branches identical" note below).
      boolean withinSendQuota =
          rateLimiter.tryAcquire("register-email:" + email, 5, Duration.ofHours(1));

      String rawToken = SecretTokens.newOpaqueToken();
      String tokenHash = SecretTokens.sha256Hex(rawToken);
      Instant expiresAt = Instant.now().plus(Duration.ofHours(24));
      pendingRegistrationRepository
          .findByEmail(email)
          .ifPresentOrElse(
              existing -> {
                // A second request for the same still-pending address replaces the claim: fresh
                // token, any earlier outstanding link for this address stops working. This is
                // safe precisely because no password lives here yet — overwriting only changes
                // who gets to type a password in next, never hands anyone else's password to a
                // new claimant (contrast with a verified User, whose password this endpoint
                // never touches, per the class comment below on that invariant).
                existing.reissue(request.displayName(), tokenHash, expiresAt);
                pendingRegistrationRepository.save(existing);
              },
              () ->
                  pendingRegistrationRepository.save(
                      new PendingRegistration(email, request.displayName(), tokenHash, expiresAt)));

      if (withinSendQuota) {
        mailService.sendVerificationEmail(email, webBaseUrl + "/verify-email?token=" + rawToken);
      }
    }
    // If a verified account already exists, do nothing: registration must never overwrite an
    // existing account's password, and this endpoint has no password to overwrite it with
    // anyway. Falling through to the identical response below is what keeps this branch
    // unobservable from the outside.

    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            new AuthDtos.RegistrationAccepted(
                email,
                "If this email can be registered, check your inbox for a verification link."));
  }

  /**
   * Confirms mailbox control and creates the account in one step, with the password supplied here —
   * not at {@link #register} — by whoever is submitting this request. See the class comment on
   * {@link #register} for why. Logs the caller in immediately: completing this request already
   * proves both mailbox control (the token) and authorship of the password (it was typed into this
   * exact call), which is everything {@code /auth/login} would otherwise check.
   */
  @PostMapping("/email/verify")
  public ResponseEntity<UserView> verifyEmail(
      @Valid @RequestBody VerifyEmailRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    String rateLimitKey = "verify-email:" + httpRequest.getRemoteAddr();
    if (!rateLimiter.tryAcquire(rateLimitKey, 10, Duration.ofMinutes(15))) {
      throw ApiException.rateLimited("Too many verification attempts, try again later");
    }

    String hash = SecretTokens.sha256Hex(request.token());
    PendingRegistration pending =
        pendingRegistrationRepository
            .findByTokenHash(hash)
            .filter(p -> !p.isExpired(Instant.now()))
            .orElseThrow(
                () ->
                    ApiException.badRequest(
                        "TOKEN_INVALID", "Verification link is invalid or expired"));

    if (userRepository.existsByEmail(pending.getEmail())) {
      // Someone else already completed verification for this address (e.g. two tabs racing on
      // the same link) between this token being issued and now. Never overwrite the account
      // that won that race; this token is spent either way.
      pendingRegistrationRepository.delete(pending);
      throw ApiException.badRequest("TOKEN_INVALID", "Verification link is invalid or expired");
    }

    User user =
        userRepository.save(
            new User(
                pending.getEmail(),
                passwordEncoder.encode(request.password()),
                pending.getDisplayName()));
    user.markEmailVerified();
    user = userRepository.save(user);
    pendingRegistrationRepository.delete(pending);

    // Completing this request already proved mailbox control (the token) and authorship of the
    // password (typed into this exact call) — exactly what login's AuthenticationManager would
    // otherwise check — so build the authenticated context directly rather than looping back
    // through a second credential check.
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(new AppUserPrincipal(user), null, List.of());
    establishSession(authentication, httpRequest, httpResponse);

    return ResponseEntity.ok(UserView.of(user));
  }

  private void establishSession(
      Authentication authentication,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, httpRequest, httpResponse);
  }

  @PostMapping("/login")
  public ResponseEntity<UserView> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    String rateLimitKey = "login:" + request.email().trim().toLowerCase();
    if (!rateLimiter.tryAcquire(rateLimitKey, 10, Duration.ofMinutes(15))) {
      throw ApiException.rateLimited("Too many login attempts, try again later");
    }

    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(
                  request.email().trim().toLowerCase(), request.password()));
    } catch (BadCredentialsException e) {
      // Deliberately generic message/status regardless of whether the email exists.
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
    }

    establishSession(authentication, httpRequest, httpResponse);

    AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
    User user =
        userRepository
            .findById(principal.getUserId())
            .orElseThrow(() -> ApiException.notFound("User not found"));
    return ResponseEntity.ok(UserView.of(user));
  }
}

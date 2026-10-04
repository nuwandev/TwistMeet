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
  private final EmailVerificationTokenRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final MailService mailService;
  private final SimpleRateLimiter rateLimiter;
  private final String webBaseUrl;
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  public AuthController(
      UserRepository userRepository,
      EmailVerificationTokenRepository tokenRepository,
      PasswordEncoder passwordEncoder,
      AuthenticationManager authenticationManager,
      MailService mailService,
      SimpleRateLimiter rateLimiter,
      Environment env) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.authenticationManager = authenticationManager;
    this.mailService = mailService;
    this.rateLimiter = rateLimiter;
    this.webBaseUrl = env.getProperty("twistmeet.web.base-url", "http://localhost:3000");
  }

  @PostMapping("/register")
  public ResponseEntity<AuthDtos.RegistrationAccepted> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
    String rateLimitKey = "register:" + httpRequest.getRemoteAddr();
    if (!rateLimiter.tryAcquire(rateLimitKey, 8, Duration.ofMinutes(15))) {
      throw ApiException.rateLimited("Too many registration attempts, try again later");
    }

    String email = request.email().trim().toLowerCase();
    // Review finding: registration previously returned 409 EMAIL_IN_USE for an existing email
    // and 201 with the new account otherwise — two different statuses and bodies that let a
    // caller enumerate which emails are already registered (12 SP04 "generic errors avoid
    // account enumeration"). Both branches below now produce the exact same status and body.
    //
    // This always hashes the submitted password regardless of which branch runs below, so an
    // attacker can't distinguish the branches by the single cheapest tell (skipping bcrypt
    // entirely on the existing-email path). It does NOT make the two branches equal-time: the
    // new-account path also inserts two rows and calls MailService, so overall response time
    // still differs and could in principle be used to infer existence. Closing that gap for
    // real needs a constant-time design (e.g. always doing an equivalent amount of DB/mail work,
    // or queuing the email send asynchronously so it can't affect the response at all) that is
    // not implemented or measured here — do not describe this endpoint as constant-time.
    String passwordHash = passwordEncoder.encode(request.password());
    if (!userRepository.existsByEmail(email)) {
      User user = userRepository.save(new User(email, passwordHash, request.displayName()));
      String rawToken = SecretTokens.newOpaqueToken();
      tokenRepository.save(
          new EmailVerificationToken(
              user.getId(),
              SecretTokens.sha256Hex(rawToken),
              Instant.now().plus(Duration.ofDays(1))));
      mailService.sendVerificationEmail(
          user.getEmail(), webBaseUrl + "/verify-email?token=" + rawToken);
    }

    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            new AuthDtos.RegistrationAccepted(
                email,
                "If this email can be registered, check your inbox for a verification link."));
  }

  @PostMapping("/email/verify")
  public ResponseEntity<Void> verifyEmail(
      @Valid @RequestBody VerifyEmailRequest request, HttpServletRequest httpRequest) {
    String rateLimitKey = "verify-email:" + httpRequest.getRemoteAddr();
    if (!rateLimiter.tryAcquire(rateLimitKey, 10, Duration.ofMinutes(15))) {
      throw ApiException.rateLimited("Too many verification attempts, try again later");
    }

    String hash = SecretTokens.sha256Hex(request.token());
    EmailVerificationToken token =
        tokenRepository
            .findByTokenHash(hash)
            .filter(t -> t.isUsable(Instant.now()))
            .orElseThrow(
                () ->
                    ApiException.badRequest(
                        "TOKEN_INVALID", "Verification link is invalid or expired"));
    User user =
        userRepository
            .findById(token.getUserId())
            .orElseThrow(() -> ApiException.notFound("User not found"));
    user.markEmailVerified();
    userRepository.save(user);
    token.markUsed();
    tokenRepository.save(token);
    return ResponseEntity.noContent().build();
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

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, httpRequest, httpResponse);

    AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
    User user =
        userRepository
            .findById(principal.getUserId())
            .orElseThrow(() -> ApiException.notFound("User not found"));
    return ResponseEntity.ok(UserView.of(user));
  }
}

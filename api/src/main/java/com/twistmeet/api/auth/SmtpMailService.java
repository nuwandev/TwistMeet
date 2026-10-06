package com.twistmeet.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional email via whatever {@link JavaMailSender} is configured.
 *
 * <p>Per DECISIONS.md OD04: local/dev and CI point this at a local mail catcher (see
 * docker-compose.yml's {@code mailcatcher} service); no production email provider has been chosen
 * yet, and this class is deliberately provider-agnostic so swapping one in later does not require
 * touching calling code.
 */
@Service
@Profile("!test")
public class SmtpMailService implements MailService {

  private static final Logger log = LoggerFactory.getLogger(SmtpMailService.class);

  private final JavaMailSender mailSender;
  private final String fromAddress;

  public SmtpMailService(JavaMailSender mailSender, Environment env) {
    this.mailSender = mailSender;
    this.fromAddress = env.getProperty("twistmeet.mail.from", "no-reply@twistmeet.local");
  }

  @Override
  public void sendVerificationEmail(String toEmail, String verifyUrl) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(toEmail);
    message.setSubject("Confirm your email and set your password");
    message.setText(
        "Someone requested an account at this address. If this was you, confirm your email and"
            + " choose your password by visiting:\n\n"
            + verifyUrl
            + "\n\nIf this wasn't you, ignore this message — no account is created until this"
            + " link is used, and nobody can see or set your password without clicking it.");
    // DEBUG-only, deliberately: this is the one place the raw (pre-hash) link is ever visible
    // outside the recipient's inbox, so it must stay off by default. It exists so a developer
    // without the docker-compose mail catcher running can still complete the flow locally.
    log.debug("Verification link for {}: {}", toEmail, verifyUrl);
    try {
      mailSender.send(message);
    } catch (Exception e) {
      // Local dev mail catchers are best-effort; never fail registration because the mail
      // catcher container is not running. A real provider choice (OD04) will revisit this.
      log.warn("Failed to send verification email to {}: {}", toEmail, e.getMessage());
    }
  }
}

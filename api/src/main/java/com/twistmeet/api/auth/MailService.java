package com.twistmeet.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class MailService {

  private static final Logger log = LoggerFactory.getLogger(MailService.class);

  private final JavaMailSender mailSender;
  private final String fromAddress;

  public MailService(JavaMailSender mailSender, org.springframework.core.env.Environment env) {
    this.mailSender = mailSender;
    this.fromAddress = env.getProperty("twistmeet.mail.from", "no-reply@twistmeet.local");
  }

  public void sendVerificationEmail(String toEmail, String verifyUrl) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromAddress);
    message.setTo(toEmail);
    message.setSubject("Verify your email");
    message.setText(
        "Welcome. Verify your email by visiting:\n\n"
            + verifyUrl
            + "\n\nIf you did not request this, ignore this message.");
    try {
      mailSender.send(message);
    } catch (Exception e) {
      // Local dev mail catchers are best-effort; never fail registration because the mail
      // catcher container is not running. A real provider choice (OD04) will revisit this.
      log.warn("Failed to send verification email to {}: {}", toEmail, e.getMessage());
    }
  }
}

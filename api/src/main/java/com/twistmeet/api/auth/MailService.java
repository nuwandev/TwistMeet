package com.twistmeet.api.auth;

/**
 * Sends transactional email. Implemented by {@link SmtpMailService} in every profile except {@code
 * test}, where {@code CapturingMailService} (test source tree) captures the message instead of
 * attempting real SMTP delivery, so tests can read back the verification link.
 */
public interface MailService {

  void sendVerificationEmail(String toEmail, String verifyUrl);
}

package com.twistmeet.api.support;

import com.twistmeet.api.auth.MailService;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test-only {@link MailService}: captures the last verification URL sent to each address instead of
 * attempting real SMTP delivery, so tests can complete the register-then-verify flow without
 * intercepting a real mailbox. Replaces {@code SmtpMailService} under the {@code test} profile (see
 * that class's {@code @Profile("!test")}).
 */
@Component
@Profile("test")
public class CapturingMailService implements MailService {

  private final ConcurrentHashMap<String, String> lastUrlByEmail = new ConcurrentHashMap<>();

  @Override
  public void sendVerificationEmail(String toEmail, String verifyUrl) {
    lastUrlByEmail.put(toEmail, verifyUrl);
  }

  /** The raw verification token from the most recent email sent to this address, or null. */
  public String latestTokenFor(String email) {
    String url = lastUrlByEmail.get(email);
    if (url == null) {
      return null;
    }
    int idx = url.indexOf("token=");
    return idx < 0 ? null : url.substring(idx + "token=".length());
  }

  public void clearAll() {
    lastUrlByEmail.clear();
  }
}

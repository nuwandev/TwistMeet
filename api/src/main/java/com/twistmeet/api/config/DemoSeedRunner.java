package com.twistmeet.api.config;

import com.twistmeet.api.auth.User;
import com.twistmeet.api.auth.UserRepository;
import com.twistmeet.api.common.SecretTokens;
import com.twistmeet.api.event.Event;
import com.twistmeet.api.event.EventRepository;
import com.twistmeet.api.event.EventState;
import com.twistmeet.api.event.EventVisibility;
import com.twistmeet.api.org.OrgRole;
import com.twistmeet.api.org.Organization;
import com.twistmeet.api.org.OrganizationMembership;
import com.twistmeet.api.org.OrganizationMembershipRepository;
import com.twistmeet.api.org.OrganizationRepository;
import com.twistmeet.api.registration.EventEntrant;
import com.twistmeet.api.registration.EventEntrantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates one demo organization, owner account, and a joinable demo event, satisfying the M1
 * "seeded demo event" exit criterion. Only runs under the {@code seed} profile (never in
 * production) and only if the demo data does not already exist, so it is safe to run repeatedly
 * against the same local/dev database.
 *
 * <p>The demo password is intentionally fixed and documented (not a secret) — see README.
 */
@Component
@Profile("seed")
public class DemoSeedRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);
  private static final String DEMO_EMAIL = "demo-organizer@twistmeet.local";
  private static final String DEMO_PASSWORD = "demo-password-123";
  private static final String DEMO_JOIN_CODE = "DEMO2026";

  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationMembershipRepository membershipRepository;
  private final EventRepository eventRepository;
  private final EventEntrantRepository entrantRepository;
  private final PasswordEncoder passwordEncoder;

  public DemoSeedRunner(
      UserRepository userRepository,
      OrganizationRepository organizationRepository,
      OrganizationMembershipRepository membershipRepository,
      EventRepository eventRepository,
      EventEntrantRepository entrantRepository,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.organizationRepository = organizationRepository;
    this.membershipRepository = membershipRepository;
    this.eventRepository = eventRepository;
    this.entrantRepository = entrantRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(String... args) {
    if (userRepository.existsByEmail(DEMO_EMAIL)) {
      log.info("Demo data already present, skipping seed.");
      return;
    }

    User owner =
        userRepository.save(
            new User(DEMO_EMAIL, passwordEncoder.encode(DEMO_PASSWORD), "Demo Organizer"));
    owner.markEmailVerified();
    userRepository.save(owner);

    Organization org =
        organizationRepository.save(
            new Organization("Demo Cubing Club", "demo-cubing-club", "America/Los_Angeles"));
    membershipRepository.save(
        new OrganizationMembership(org.getId(), owner.getId(), OrgRole.OWNER));

    Event event =
        new Event(
            org.getId(),
            "Demo Saturday Meetup",
            "Seeded demo event for local development and pilots.",
            Instant.now().plus(7, ChronoUnit.DAYS),
            "America/Los_Angeles",
            "Community Center Room B",
            EventVisibility.PRIVATE,
            DEMO_JOIN_CODE,
            SecretTokens.sha256Hex(DEMO_JOIN_CODE));
    event.setState(EventState.REGISTRATION_OPEN);
    event = eventRepository.save(event);

    entrantRepository.save(new EventEntrant(event.getId(), "Alex"));
    entrantRepository.save(new EventEntrant(event.getId(), "Priya"));

    log.info(
        "Seeded demo organization '{}' with event '{}' (join code {}). Demo login: {} / {}",
        org.getName(),
        event.getName(),
        DEMO_JOIN_CODE,
        DEMO_EMAIL,
        DEMO_PASSWORD);
  }
}

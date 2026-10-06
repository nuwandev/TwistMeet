package com.twistmeet.api.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, UUID> {
  Optional<PendingRegistration> findByEmail(String email);

  Optional<PendingRegistration> findByTokenHash(String tokenHash);
}

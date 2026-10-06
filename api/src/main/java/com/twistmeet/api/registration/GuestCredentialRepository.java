package com.twistmeet.api.registration;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestCredentialRepository extends JpaRepository<GuestCredential, UUID> {
  Optional<GuestCredential> findByTokenHash(String tokenHash);
}

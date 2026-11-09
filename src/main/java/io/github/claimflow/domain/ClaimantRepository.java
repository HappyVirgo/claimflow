package io.github.claimflow.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for claimants. */
public interface ClaimantRepository extends JpaRepository<Claimant, Long> {

    /** Finds a claimant by (case-sensitive, already normalised) email. */
    Optional<Claimant> findByEmail(String email);
}

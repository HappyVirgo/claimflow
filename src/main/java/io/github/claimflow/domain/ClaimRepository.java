package io.github.claimflow.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence and lookups for claims. */
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    /** Finds a claim by its public claim number. */
    Optional<Claim> findByClaimNumber(String claimNumber);

    /** Claims on a policy whose loss date falls in the window (used for duplicate detection). */
    List<Claim> findByPolicyIdAndLossDateBetween(Long policyId, LocalDate from, LocalDate to);

    /** Number of claims a claimant filed with a loss date in the window (risk feature). */
    long countByClaimantIdAndLossDateBetween(Long claimantId, LocalDate from, LocalDate to);

    /** Lookup by status, newest first. */
    List<Claim> findByStatusOrderByIdDesc(ClaimStatus status);

    /** Lookup by policy number, newest first. */
    List<Claim> findByPolicyPolicyNumberOrderByIdDesc(String policyNumber);
}

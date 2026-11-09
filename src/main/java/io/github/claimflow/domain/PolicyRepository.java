package io.github.claimflow.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read access to policies. */
public interface PolicyRepository extends JpaRepository<Policy, Long> {

    /** Looks up a policy by its business key. */
    Optional<Policy> findByPolicyNumber(String policyNumber);
}

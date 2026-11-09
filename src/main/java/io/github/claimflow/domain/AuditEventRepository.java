package io.github.claimflow.domain;

import java.util.List;
import org.springframework.data.repository.Repository;

/** Append-only access to the audit trail: insert and read, never update or delete. */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    /** Appends an event. */
    AuditEvent save(AuditEvent event);

    /** The trail for one claim in insertion order. */
    List<AuditEvent> findByClaimIdOrderByIdAsc(Long claimId);
}

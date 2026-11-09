package io.github.claimflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * One entry in a claim's append-only audit trail. The entity is {@link Immutable} (Hibernate never
 * issues UPDATEs for it) and its repository exposes no update or delete methods.
 */
@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false, updatable = false)
    private Long claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private ClaimStatus toStatus;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(nullable = false, updatable = false)
    private String actor;

    @Column(length = 2000, updatable = false)
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    /** Creates an audit entry; {@code fromStatus} is null for the initial submission. */
    public AuditEvent(Long claimId, ClaimStatus fromStatus, ClaimStatus toStatus, String action, String actor,
                      String detail, Instant occurredAt) {
        this.claimId = claimId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.action = action;
        this.actor = actor;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public ClaimStatus getFromStatus() { return fromStatus; }
    public ClaimStatus getToStatus() { return toStatus; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public String getDetail() { return detail; }
    public Instant getOccurredAt() { return occurredAt; }
}

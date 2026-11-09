package io.github.claimflow.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate root for a claim. Status is only changed through {@link #moveTo(ClaimStatus)}, which the
 * service layer calls after the state machine has validated the transition. {@link Version} provides
 * optimistic locking so concurrent adjuster actions cannot silently overwrite each other.
 */
@Entity
@Table(name = "claim")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", nullable = false, unique = true)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "policy_id")
    private Policy policy;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "claimant_id")
    private Claimant claimant;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false)
    private ClaimType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.SUBMITTED;

    @Column(name = "loss_date", nullable = false)
    private LocalDate lossDate;

    @Column(name = "reported_date", nullable = false)
    private LocalDate reportedDate;

    @Column(nullable = false, length = 4000)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "claim_line_item", joinColumns = @JoinColumn(name = "claim_id"))
    @OrderColumn(name = "seq")
    private List<LineItem> lineItems = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "claim_document", joinColumns = @JoinColumn(name = "claim_id"))
    @OrderColumn(name = "seq")
    private List<ClaimDocument> documents = new ArrayList<>();

    @Column(name = "risk_score")
    private Double riskScore;

    /** JSON snapshot of the triage report (rule findings + risk explanation). */
    @Column(name = "triage_report", length = 8000)
    private String triageReport;

    @Column(name = "payable_amount")
    private BigDecimal payableAmount;

    @Column(name = "approved_amount")
    private BigDecimal approvedAmount;

    @Column(name = "paid_amount")
    private BigDecimal paidAmount;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Claim() {
    }

    /** Creates a new claim in {@link ClaimStatus#SUBMITTED}. */
    public Claim(String claimNumber, Policy policy, Claimant claimant, ClaimType type, LocalDate lossDate,
                 LocalDate reportedDate, String description, List<LineItem> lineItems,
                 List<ClaimDocument> documents, Instant now) {
        this.claimNumber = claimNumber;
        this.policy = policy;
        this.claimant = claimant;
        this.type = type;
        this.lossDate = lossDate;
        this.reportedDate = reportedDate;
        this.description = description;
        this.lineItems = new ArrayList<>(lineItems);
        this.documents = new ArrayList<>(documents);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Sum of all line item amounts. */
    public BigDecimal totalClaimed() {
        return lineItems.stream().map(LineItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Sets the new status. Callers must validate the transition with the state machine first. */
    public void moveTo(ClaimStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    /** Records the outcome of automated triage. */
    public void recordTriage(double score, BigDecimal payable, String reportJson) {
        this.riskScore = score;
        this.payableAmount = payable;
        this.triageReport = reportJson;
    }

    /** Records an approval decision and the amount approved for payment. */
    public void recordApproval(BigDecimal amount, String reason) {
        this.approvedAmount = amount;
        this.decisionReason = reason;
    }

    /** Records a denial or information request reason. */
    public void recordReason(String reason) {
        this.decisionReason = reason;
    }

    /** Records the payment of the approved amount. */
    public void recordPayment() {
        this.paidAmount = approvedAmount;
    }

    public Long getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public Policy getPolicy() { return policy; }
    public Claimant getClaimant() { return claimant; }
    public ClaimType getType() { return type; }
    public ClaimStatus getStatus() { return status; }
    public LocalDate getLossDate() { return lossDate; }
    public LocalDate getReportedDate() { return reportedDate; }
    public String getDescription() { return description; }
    public List<LineItem> getLineItems() { return List.copyOf(lineItems); }
    public List<ClaimDocument> getDocuments() { return List.copyOf(documents); }
    public Double getRiskScore() { return riskScore; }
    public String getTriageReport() { return triageReport; }
    public BigDecimal getPayableAmount() { return payableAmount; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public String getDecisionReason() { return decisionReason; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

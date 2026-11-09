package io.github.claimflow.api;

import io.github.claimflow.domain.AuditEvent;
import io.github.claimflow.domain.Claim;
import io.github.claimflow.domain.ClaimDocument;
import io.github.claimflow.domain.ClaimStatus;
import io.github.claimflow.domain.ClaimType;
import io.github.claimflow.domain.LineItem;
import io.github.claimflow.service.TriageReport;
import io.github.claimflow.workflow.ClaimAction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** API view of a claim, including triage output and the actions currently available. */
public record ClaimResponse(
        String claimNumber, ClaimStatus status, ClaimType type, String policyNumber, String claimantName,
        String claimantEmail, LocalDate lossDate, LocalDate reportedDate, String description,
        BigDecimal totalClaimed, BigDecimal payableAmount, BigDecimal approvedAmount, BigDecimal paidAmount,
        String decisionReason, long version, List<LineItemView> lineItems, List<DocumentView> documents,
        TriageReport triage, Set<ClaimAction> availableActions) {

    /** Line item view. */
    public record LineItemView(String coverage, String description, BigDecimal amount) {
    }

    /** Document metadata view. */
    public record DocumentView(String type, String fileName, String contentType, long sizeBytes) {
    }

    /** One audit trail entry. */
    public record AuditView(ClaimStatus fromStatus, ClaimStatus toStatus, String action, String actor,
                            String detail, Instant occurredAt) {

        /** Maps an audit event. */
        public static AuditView of(AuditEvent e) {
            return new AuditView(e.getFromStatus(), e.getToStatus(), e.getAction(), e.getActor(), e.getDetail(),
                    e.getOccurredAt());
        }
    }

    /** Maps a claim and its parsed triage report. */
    public static ClaimResponse of(Claim c, TriageReport triage, Set<ClaimAction> actions) {
        List<LineItemView> items = c.getLineItems().stream()
                .map((LineItem li) -> new LineItemView(li.getCoverage().name(), li.getDescription(), li.getAmount()))
                .toList();
        List<DocumentView> docs = c.getDocuments().stream()
                .map((ClaimDocument d) -> new DocumentView(d.getType().name(), d.getFileName(), d.getContentType(),
                        d.getSizeBytes()))
                .toList();
        return new ClaimResponse(c.getClaimNumber(), c.getStatus(), c.getType(), c.getPolicy().getPolicyNumber(),
                c.getClaimant().getFirstName() + " " + c.getClaimant().getLastName(), c.getClaimant().getEmail(),
                c.getLossDate(), c.getReportedDate(), c.getDescription(), c.totalClaimed(), c.getPayableAmount(),
                c.getApprovedAmount(), c.getPaidAmount(), c.getDecisionReason(), c.getVersion(), items, docs, triage,
                actions);
    }
}

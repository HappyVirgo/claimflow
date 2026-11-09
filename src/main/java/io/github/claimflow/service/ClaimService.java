package io.github.claimflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.claimflow.api.ActionRequest;
import io.github.claimflow.api.ClaimResponse;
import io.github.claimflow.api.FnolRequest;
import io.github.claimflow.domain.AuditEvent;
import io.github.claimflow.domain.AuditEventRepository;
import io.github.claimflow.domain.Claim;
import io.github.claimflow.domain.ClaimDocument;
import io.github.claimflow.domain.ClaimRepository;
import io.github.claimflow.domain.ClaimStatus;
import io.github.claimflow.domain.Claimant;
import io.github.claimflow.domain.ClaimantRepository;
import io.github.claimflow.domain.LineItem;
import io.github.claimflow.domain.Policy;
import io.github.claimflow.domain.PolicyRepository;
import io.github.claimflow.risk.RiskFeatureExtractor;
import io.github.claimflow.risk.RiskFeatures;
import io.github.claimflow.rules.RuleContext;
import io.github.claimflow.rules.RuleEngine;
import io.github.claimflow.workflow.ClaimAction;
import io.github.claimflow.workflow.ClaimStateMachine;
import io.github.claimflow.workflow.TransitionContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for FNOL intake, automated triage, lookups and adjuster actions. */
@Service
public class ClaimService {

    /** Actions only the system may take; adjusters cannot trigger them through the API. */
    static final Set<ClaimAction> SYSTEM_ACTIONS = EnumSet.of(ClaimAction.TRIAGE, ClaimAction.FAST_TRACK,
            ClaimAction.ESCALATE);
    private static final String SYSTEM_ACTOR = "system:triage";

    private final ClaimRepository claims;
    private final PolicyRepository policies;
    private final ClaimantRepository claimants;
    private final AuditEventRepository audit;
    private final ClaimStateMachine stateMachine;
    private final TriageService triage;
    private final ObjectMapper mapper;
    private final Clock clock;

    /** Creates the service. */
    public ClaimService(ClaimRepository claims, PolicyRepository policies, ClaimantRepository claimants,
                        AuditEventRepository audit, ClaimStateMachine stateMachine, TriageService triage,
                        ObjectMapper mapper, Clock clock) {
        this.claims = claims;
        this.policies = policies;
        this.claimants = claimants;
        this.audit = audit;
        this.stateMachine = stateMachine;
        this.triage = triage;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Accepts a first notice of loss, persists it, and runs triage in the same transaction. */
    @Transactional
    public ClaimResponse submit(FnolRequest req) {
        Policy policy = policies.findByPolicyNumber(req.policyNumber())
                .orElseThrow(() -> new NotFoundException("Unknown policy " + req.policyNumber()));
        Claimant claimant = findOrCreateClaimant(req.claimant());
        LocalDate today = LocalDate.now(clock);
        List<LineItem> items = req.lineItems().stream()
                .map(li -> new LineItem(li.coverage(), li.description(), li.amount())).toList();
        List<ClaimDocument> docs = req.documentsOrEmpty().stream()
                .map(d -> new ClaimDocument(d.type(), d.fileName(), d.contentType(), d.sizeBytes())).toList();

        List<RuleContext.PriorClaim> nearby = claims.findByPolicyIdAndLossDateBetween(policy.getId(),
                        req.lossDate().minusDays(RuleEngine.DUPLICATE_WINDOW_DAYS),
                        req.lossDate().plusDays(RuleEngine.DUPLICATE_WINDOW_DAYS)).stream()
                .map(c -> new RuleContext.PriorClaim(c.getClaimNumber(), c.getLossDate(), c.totalClaimed()))
                .toList();
        long prior = claimant.getId() == null ? 0 : claims.countByClaimantIdAndLossDateBetween(claimant.getId(),
                req.lossDate().minusDays(365), req.lossDate());
        RuleContext ctx = new RuleContext(policy, req.type(), req.lossDate(), today, items, nearby);
        RiskFeatures features = RiskFeatureExtractor.extract(policy, req.type(), req.lossDate(), today, items,
                docs, prior);

        Instant now = clock.instant();
        Claim claim = claims.save(new Claim(newClaimNumber(), policy, claimant, req.type(), req.lossDate(), today,
                req.description(), items, docs, now));
        append(claim, null, "SUBMIT", "claimant:" + claimant.getEmail(), "FNOL received");

        TriageReport report = triage.triage(ctx, features);
        claim.recordTriage(report.risk().score(), report.payableAmount(), toJson(report));
        move(claim, ClaimAction.TRIAGE, TransitionContext.empty(), SYSTEM_ACTOR,
                "risk=" + report.risk().score() + " band=" + report.risk().band());
        if (report.fastTrack()) {
            claim.recordApproval(report.payableAmount(), "Fast-track approval");
            move(claim, ClaimAction.FAST_TRACK, new TransitionContext(true, null, null, null), SYSTEM_ACTOR,
                    "approved " + report.payableAmount());
        } else {
            move(claim, ClaimAction.ESCALATE, TransitionContext.empty(), SYSTEM_ACTOR,
                    String.join("; ", report.routingReasons()));
        }
        return view(claims.saveAndFlush(claim));
    }

    /** Applies an adjuster action, enforcing the expected version and the state machine guards. */
    @Transactional
    public ClaimResponse act(String claimNumber, ActionRequest req) {
        if (SYSTEM_ACTIONS.contains(req.action())) {
            throw new IllegalArgumentException("Action " + req.action() + " is reserved for the system");
        }
        Claim claim = load(claimNumber);
        if (claim.getVersion() != req.expectedVersion()) {
            throw new StaleVersionException(claimNumber, req.expectedVersion(), claim.getVersion());
        }
        TransitionContext ctx = new TransitionContext(false, req.reason(), req.amount(), claim.getPayableAmount());
        String detail;
        switch (req.action()) {
            case APPROVE -> {
                claim.recordApproval(req.amount(), req.reason());
                detail = "approved " + req.amount();
            }
            case DENY, REQUEST_INFO -> {
                claim.recordReason(req.reason());
                detail = req.reason();
            }
            case PAY -> {
                claim.recordPayment();
                detail = "paid " + claim.getApprovedAmount();
            }
            default -> detail = req.reason();
        }
        move(claim, req.action(), ctx, req.actor(), detail);
        return view(claims.saveAndFlush(claim));
    }

    /** Looks up one claim. */
    @Transactional(readOnly = true)
    public ClaimResponse get(String claimNumber) {
        return view(load(claimNumber));
    }

    /** Lists claims filtered by status or policy number (exactly one filter must be given). */
    @Transactional(readOnly = true)
    public List<ClaimResponse> search(ClaimStatus status, String policyNumber) {
        if ((status == null) == (policyNumber == null)) {
            throw new IllegalArgumentException("Provide exactly one of 'status' or 'policyNumber'");
        }
        List<Claim> found = status != null ? claims.findByStatusOrderByIdDesc(status)
                : claims.findByPolicyPolicyNumberOrderByIdDesc(policyNumber);
        return found.stream().map(this::view).toList();
    }

    /** The full audit trail for a claim, oldest first. */
    @Transactional(readOnly = true)
    public List<ClaimResponse.AuditView> auditTrail(String claimNumber) {
        return audit.findByClaimIdOrderByIdAsc(load(claimNumber).getId()).stream()
                .map(ClaimResponse.AuditView::of).toList();
    }

    /** Loads a claim entity or throws {@link NotFoundException}. */
    public Claim load(String claimNumber) {
        return claims.findByClaimNumber(claimNumber)
                .orElseThrow(() -> new NotFoundException("Unknown claim " + claimNumber));
    }

    private void move(Claim claim, ClaimAction action, TransitionContext ctx, String actor, String detail) {
        ClaimStatus from = claim.getStatus();
        ClaimStatus to = stateMachine.transition(from, action, ctx);
        claim.moveTo(to);
        audit.save(new AuditEvent(claim.getId(), from, to, action.name(), actor, detail, clock.instant()));
    }

    private void append(Claim claim, ClaimStatus from, String action, String actor, String detail) {
        audit.save(new AuditEvent(claim.getId(), from, claim.getStatus(), action, actor, detail, clock.instant()));
    }

    private Claimant findOrCreateClaimant(FnolRequest.ClaimantInfo info) {
        String email = info.email().trim().toLowerCase(Locale.ROOT);
        return claimants.findByEmail(email)
                .orElseGet(() -> claimants.save(new Claimant(info.firstName(), info.lastName(), email, info.phone())));
    }

    private ClaimResponse view(Claim claim) {
        TriageReport report = null;
        if (claim.getTriageReport() != null) {
            try {
                report = mapper.readValue(claim.getTriageReport(), TriageReport.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Corrupt triage report on " + claim.getClaimNumber(), e);
            }
        }
        Set<ClaimAction> actions = EnumSet.noneOf(ClaimAction.class);
        actions.addAll(stateMachine.availableActions(claim.getStatus()));
        actions.removeAll(SYSTEM_ACTIONS);
        return ClaimResponse.of(claim, report, actions);
    }

    private String toJson(TriageReport report) {
        try {
            return mapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise triage report", e);
        }
    }

    private static String newClaimNumber() {
        return "CLM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }
}

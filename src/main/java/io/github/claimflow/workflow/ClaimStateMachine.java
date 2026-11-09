package io.github.claimflow.workflow;

import static io.github.claimflow.domain.ClaimStatus.APPROVED;
import static io.github.claimflow.domain.ClaimStatus.CLOSED;
import static io.github.claimflow.domain.ClaimStatus.DENIED;
import static io.github.claimflow.domain.ClaimStatus.FAST_TRACK_APPROVED;
import static io.github.claimflow.domain.ClaimStatus.IN_REVIEW;
import static io.github.claimflow.domain.ClaimStatus.NEEDS_INFO;
import static io.github.claimflow.domain.ClaimStatus.PAID;
import static io.github.claimflow.domain.ClaimStatus.SUBMITTED;
import static io.github.claimflow.domain.ClaimStatus.TRIAGED;

import io.github.claimflow.domain.ClaimStatus;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Explicit, table-driven claim workflow. Every legal (state, action) pair maps to exactly one target
 * state and a guard; anything not in the table is rejected. The class is pure (no I/O), so the whole
 * workflow is unit-testable without Spring.
 */
@Component
public class ClaimStateMachine {

    private record Edge(ClaimStatus to, Predicate<TransitionContext> guard, String guardDescription) {
    }

    private static final Predicate<TransitionContext> ALWAYS = ctx -> true;

    private final Map<ClaimStatus, Map<ClaimAction, Edge>> table = new EnumMap<>(ClaimStatus.class);

    /** Builds the transition table. */
    public ClaimStateMachine() {
        add(SUBMITTED, ClaimAction.TRIAGE, TRIAGED, ALWAYS, "");
        add(TRIAGED, ClaimAction.FAST_TRACK, FAST_TRACK_APPROVED, TransitionContext::fastTrackEligible,
                "claim must be fast-track eligible (rules clean, low risk, under amount cap)");
        add(TRIAGED, ClaimAction.ESCALATE, IN_REVIEW, ALWAYS, "");
        add(IN_REVIEW, ClaimAction.APPROVE, APPROVED, ClaimStateMachine::validApprovalAmount,
                "approved amount must be positive and not exceed the payable amount");
        add(IN_REVIEW, ClaimAction.DENY, DENIED, ClaimStateMachine::hasReason, "a reason is required");
        add(IN_REVIEW, ClaimAction.REQUEST_INFO, NEEDS_INFO, ClaimStateMachine::hasReason,
                "a reason is required");
        add(NEEDS_INFO, ClaimAction.PROVIDE_INFO, IN_REVIEW, ALWAYS, "");
        add(FAST_TRACK_APPROVED, ClaimAction.PAY, PAID, ALWAYS, "");
        add(APPROVED, ClaimAction.PAY, PAID, ALWAYS, "");
        add(PAID, ClaimAction.CLOSE, CLOSED, ALWAYS, "");
        add(DENIED, ClaimAction.CLOSE, CLOSED, ALWAYS, "");
    }

    private void add(ClaimStatus from, ClaimAction action, ClaimStatus to, Predicate<TransitionContext> guard,
                     String description) {
        table.computeIfAbsent(from, k -> new EnumMap<>(ClaimAction.class))
                .put(action, new Edge(to, guard, description));
    }

    /**
     * Validates and resolves a transition.
     *
     * @return the target state
     * @throws IllegalTransitionException if the action is not allowed from {@code from} or its guard fails
     */
    public ClaimStatus transition(ClaimStatus from, ClaimAction action, TransitionContext ctx) {
        Edge edge = table.getOrDefault(from, Map.of()).get(action);
        if (edge == null) {
            throw new IllegalTransitionException(from, action,
                    "Action " + action + " is not allowed from state " + from);
        }
        if (!edge.guard().test(ctx)) {
            throw new IllegalTransitionException(from, action,
                    "Guard failed for " + action + " from " + from + ": " + edge.guardDescription());
        }
        return edge.to();
    }

    /** Actions that are structurally allowed from a state (guards not evaluated). */
    public Set<ClaimAction> availableActions(ClaimStatus from) {
        return Set.copyOf(table.getOrDefault(from, Map.of()).keySet());
    }

    /** True if no action leads out of the state. */
    public boolean isTerminal(ClaimStatus status) {
        return availableActions(status).isEmpty();
    }

    private static boolean hasReason(TransitionContext ctx) {
        return ctx.reason() != null && !ctx.reason().isBlank();
    }

    private static boolean validApprovalAmount(TransitionContext ctx) {
        if (ctx.amount() == null || ctx.amount().signum() <= 0) {
            return false;
        }
        BigDecimal cap = ctx.payableAmount();
        return cap == null || ctx.amount().compareTo(cap) <= 0;
    }
}

package io.github.claimflow.workflow;

import java.math.BigDecimal;

/**
 * Facts the transition guards are evaluated against.
 *
 * @param fastTrackEligible triage verdict: rules clean and risk below threshold
 * @param reason            free-text reason supplied with the action (required for DENY / REQUEST_INFO)
 * @param amount            amount supplied with APPROVE
 * @param payableAmount     maximum payable amount computed by the rules engine (null if not triaged)
 */
public record TransitionContext(boolean fastTrackEligible, String reason, BigDecimal amount,
                                BigDecimal payableAmount) {

    /** Context with no extra facts, for actions whose guards need none. */
    public static TransitionContext empty() {
        return new TransitionContext(false, null, null, null);
    }
}

package io.github.claimflow.workflow;

import io.github.claimflow.domain.ClaimStatus;

/** Raised when an action is not allowed from the current state or its guard rejects it. */
public class IllegalTransitionException extends RuntimeException {

    private final ClaimStatus from;
    private final ClaimAction action;

    /** Creates the exception with a human-readable explanation. */
    public IllegalTransitionException(ClaimStatus from, ClaimAction action, String message) {
        super(message);
        this.from = from;
        this.action = action;
    }

    public ClaimStatus getFrom() { return from; }
    public ClaimAction getAction() { return action; }
}

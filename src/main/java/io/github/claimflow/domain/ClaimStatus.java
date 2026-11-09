package io.github.claimflow.domain;

/** Workflow states of a claim. Legal transitions are defined in {@code ClaimStateMachine}. */
public enum ClaimStatus {
    SUBMITTED,
    TRIAGED,
    FAST_TRACK_APPROVED,
    IN_REVIEW,
    NEEDS_INFO,
    APPROVED,
    DENIED,
    PAID,
    CLOSED
}

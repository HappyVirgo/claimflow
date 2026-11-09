package io.github.claimflow.workflow;

/** Events that move a claim through the workflow. */
public enum ClaimAction {
    /** System: run rules and risk scoring on a submitted claim. */
    TRIAGE,
    /** System: route a low-risk, rule-clean claim straight to approval. */
    FAST_TRACK,
    /** System: route a claim to a human adjuster. */
    ESCALATE,
    /** Adjuster: approve an amount for payment. */
    APPROVE,
    /** Adjuster: deny the claim with a reason. */
    DENY,
    /** Adjuster: pause the claim until the claimant supplies more information. */
    REQUEST_INFO,
    /** Adjuster: information received, resume review. */
    PROVIDE_INFO,
    /** Adjuster/payments: disburse the approved amount. */
    PAY,
    /** Adjuster: close a paid or denied claim. */
    CLOSE
}

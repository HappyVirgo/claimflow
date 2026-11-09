package io.github.claimflow.rules;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated rule results for one claim.
 *
 * @param results       one result per rule, in evaluation order
 * @param payableAmount claimed total minus deductible, capped at the policy limit, never negative
 */
public record RuleEvaluation(List<RuleResult> results, BigDecimal payableAmount) {

    /** True if any rule failed. */
    public boolean hasFailures() {
        return results.stream().anyMatch(r -> r.outcome() == RuleResult.Outcome.FAIL);
    }

    /** True if every rule passed outright (no warnings or failures). */
    public boolean allPassed() {
        return results.stream().allMatch(r -> r.outcome() == RuleResult.Outcome.PASS);
    }
}

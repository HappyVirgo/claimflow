package io.github.claimflow.rules;

/**
 * Outcome of one rule.
 *
 * @param code    stable rule identifier, e.g. {@code POLICY_ACTIVE}
 * @param outcome PASS, WARN (needs a human look) or FAIL (blocks fast-track; adjuster decides)
 * @param message human-readable explanation
 */
public record RuleResult(String code, Outcome outcome, String message) {

    /** Severity of a rule outcome. */
    public enum Outcome { PASS, WARN, FAIL }

    static RuleResult pass(String code, String message) {
        return new RuleResult(code, Outcome.PASS, message);
    }

    static RuleResult warn(String code, String message) {
        return new RuleResult(code, Outcome.WARN, message);
    }

    static RuleResult fail(String code, String message) {
        return new RuleResult(code, Outcome.FAIL, message);
    }
}

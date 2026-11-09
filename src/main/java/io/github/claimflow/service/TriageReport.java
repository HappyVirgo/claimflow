package io.github.claimflow.service;

import io.github.claimflow.risk.RiskAssessment;
import io.github.claimflow.rules.RuleResult;
import java.math.BigDecimal;
import java.util.List;

/**
 * Snapshot of automated triage, stored with the claim and returned by the API.
 *
 * @param rules             every rule result
 * @param risk              model score, band and top contributing features
 * @param payableAmount     amount payable under the policy terms
 * @param fastTrack         whether the claim was routed to fast-track approval
 * @param routingReasons    why the claim was (not) fast-tracked
 */
public record TriageReport(List<RuleResult> rules, RiskAssessment risk, BigDecimal payableAmount,
                           boolean fastTrack, List<String> routingReasons) {
}

package io.github.claimflow.service;

import io.github.claimflow.risk.RiskAssessment;
import io.github.claimflow.risk.RiskFeatures;
import io.github.claimflow.risk.RiskScorer;
import io.github.claimflow.rules.RuleContext;
import io.github.claimflow.rules.RuleEngine;
import io.github.claimflow.rules.RuleEvaluation;
import io.github.claimflow.rules.RuleResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Combines the rules engine and the risk model into a routing decision. A claim is fast-tracked only if
 * every rule passes, the model band is LOW and the total is under the configured cap.
 */
@Service
public class TriageService {

    private final RuleEngine rules;
    private final RiskScorer scorer;
    private final TriageProperties properties;

    /** Creates the service. */
    public TriageService(RuleEngine rules, RiskScorer scorer, TriageProperties properties) {
        this.rules = rules;
        this.scorer = scorer;
        this.properties = properties;
    }

    /** Evaluates rules and risk and decides the route. */
    public TriageReport triage(RuleContext ctx, RiskFeatures features) {
        RuleEvaluation evaluation = rules.evaluate(ctx);
        RiskAssessment risk = scorer.assess(features);
        List<String> reasons = new ArrayList<>();
        for (RuleResult r : evaluation.results()) {
            if (r.outcome() != RuleResult.Outcome.PASS) {
                reasons.add(r.outcome() + " " + r.code() + ": " + r.message());
            }
        }
        if (risk.band() != RiskAssessment.Band.LOW) {
            reasons.add("Risk score " + risk.score() + " is at or above threshold " + risk.threshold());
        }
        if (ctx.totalClaimed().compareTo(properties.fastTrackMaxAmount()) > 0) {
            reasons.add("Total " + ctx.totalClaimed() + " exceeds fast-track cap " + properties.fastTrackMaxAmount());
        }
        boolean fastTrack = reasons.isEmpty();
        if (fastTrack) {
            reasons.add("All rules passed, low risk, under fast-track cap");
        }
        return new TriageReport(evaluation.results(), risk, evaluation.payableAmount(), fastTrack, List.copyOf(reasons));
    }
}

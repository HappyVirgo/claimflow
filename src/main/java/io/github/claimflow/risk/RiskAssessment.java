package io.github.claimflow.risk;

import java.util.List;

/**
 * Model output plus explanation.
 *
 * @param score      probability of the risky class in [0, 1]
 * @param band       LOW (below threshold), MEDIUM, or HIGH (at or above {@link RiskScorer#HIGH_BAND})
 * @param threshold  the model's fast-track threshold
 * @param topFactors features with the largest absolute contribution to the logit, largest first
 */
public record RiskAssessment(double score, Band band, double threshold, List<Factor> topFactors) {

    /** Coarse risk band. */
    public enum Band { LOW, MEDIUM, HIGH }

    /**
     * One explanatory factor.
     *
     * @param feature      model feature name
     * @param value        raw (pre-standardisation) input value
     * @param contribution signed contribution to the logit; positive raises risk
     */
    public record Factor(String feature, double value, double contribution) {
    }
}

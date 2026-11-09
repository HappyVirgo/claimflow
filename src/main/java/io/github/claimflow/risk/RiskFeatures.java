package io.github.claimflow.risk;

import java.util.List;

/**
 * Raw risk features for one claim. The same record is produced by the synthetic data generator
 * (offline training) and by {@link RiskFeatureExtractor} (runtime), so train and serve share one
 * feature definition.
 *
 * @param amountToLimit   total claimed divided by the policy coverage limit
 * @param daysToReport    days between loss date and report date
 * @param policyAgeDays   days between policy effective date and loss date
 * @param priorClaims12m  claims by the same claimant with a loss in the preceding 365 days
 * @param property        1 for a property claim, 0 for auto
 * @param documentCount   number of supporting documents attached at intake
 * @param policeReport    1 if a police report is attached
 * @param roundAmount     1 if the total is at least 1,000 and a multiple of 500
 * @param lineItemCount   number of line items
 */
public record RiskFeatures(double amountToLimit, int daysToReport, int policyAgeDays, int priorClaims12m,
                           int property, int documentCount, int policeReport, int roundAmount,
                           int lineItemCount) {

    /** Model input names, aligned with {@link #toVector()}. */
    public static final List<String> NAMES = List.of(
            "amount_to_limit", "log_days_to_report", "log_policy_age_days", "prior_claims_12m",
            "is_property", "document_count", "has_police_report", "round_amount", "line_item_count");

    /** Model input vector: day counts are log1p-transformed, the amount ratio is capped at 1.5. */
    public double[] toVector() {
        return new double[] {
            Math.min(amountToLimit, 1.5),
            Math.log1p(Math.max(0, daysToReport)),
            Math.log1p(Math.max(0, policyAgeDays)),
            priorClaims12m,
            property,
            documentCount,
            policeReport,
            roundAmount,
            lineItemCount
        };
    }
}

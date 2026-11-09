package io.github.claimflow.risk;

import java.util.List;

/**
 * A standardised logistic-regression model as serialised to {@code risk-model.json}.
 *
 * @param features  feature names, must equal {@link RiskFeatures#NAMES}
 * @param mean      per-feature training mean used for standardisation
 * @param std       per-feature training standard deviation
 * @param weights   coefficients on standardised features
 * @param intercept bias term
 * @param threshold score at or above which a claim is considered risky (not fast-track eligible)
 * @param trainedOn provenance string describing the training data
 */
public record LogisticModel(List<String> features, double[] mean, double[] std, double[] weights,
                            double intercept, double threshold, String trainedOn) {

    /** Validates dimensions against the runtime feature definition. */
    public LogisticModel {
        int n = features.size();
        if (mean.length != n || std.length != n || weights.length != n) {
            throw new IllegalArgumentException("Model arrays must all have length " + n);
        }
        if (!features.equals(RiskFeatures.NAMES)) {
            throw new IllegalArgumentException("Model features " + features + " != " + RiskFeatures.NAMES);
        }
    }

    /** Standardises a raw vector with the training statistics. */
    public double[] standardize(double[] x) {
        double[] z = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            z[i] = (x[i] - mean[i]) / std[i];
        }
        return z;
    }

    /** Per-feature contribution to the logit ({@code weight * standardised value}). */
    public double[] contributions(double[] x) {
        double[] z = standardize(x);
        for (int i = 0; i < z.length; i++) {
            z[i] *= weights[i];
        }
        return z;
    }

    /** Probability of the positive (risky) class. */
    public double predict(double[] x) {
        double logit = intercept;
        for (double c : contributions(x)) {
            logit += c;
        }
        return 1.0 / (1.0 + Math.exp(-logit));
    }
}

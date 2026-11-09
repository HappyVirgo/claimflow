package io.github.claimflow.ml;

import io.github.claimflow.risk.LogisticModel;
import io.github.claimflow.risk.RiskFeatures;
import java.util.List;

/** Full-batch gradient descent for L2-regularised logistic regression on standardised features. */
public final class LogisticRegressionTrainer {

    private final double learningRate;
    private final int epochs;
    private final double l2;

    /** Creates a trainer with the given hyper-parameters. */
    public LogisticRegressionTrainer(double learningRate, int epochs, double l2) {
        this.learningRate = learningRate;
        this.epochs = epochs;
        this.l2 = l2;
    }

    /**
     * Fits a model. The returned model's threshold is a placeholder (0.5); callers choose the operating
     * threshold with {@link ClassificationMetrics#thresholdForRecall}.
     */
    public LogisticModel fit(double[][] x, int[] y, String provenance) {
        int n = x.length;
        int d = x[0].length;
        double[] mean = new double[d];
        double[] std = new double[d];
        for (double[] row : x) {
            for (int j = 0; j < d; j++) {
                mean[j] += row[j] / n;
            }
        }
        for (double[] row : x) {
            for (int j = 0; j < d; j++) {
                std[j] += (row[j] - mean[j]) * (row[j] - mean[j]) / n;
            }
        }
        for (int j = 0; j < d; j++) {
            std[j] = std[j] > 0 ? Math.sqrt(std[j]) : 1.0;
        }
        double[][] z = new double[n][d];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                z[i][j] = (x[i][j] - mean[j]) / std[j];
            }
        }
        double[] w = new double[d];
        double b = 0;
        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] grad = new double[d];
            double gradB = 0;
            for (int i = 0; i < n; i++) {
                double logit = b;
                for (int j = 0; j < d; j++) {
                    logit += w[j] * z[i][j];
                }
                double err = 1.0 / (1.0 + Math.exp(-logit)) - y[i];
                for (int j = 0; j < d; j++) {
                    grad[j] += err * z[i][j];
                }
                gradB += err;
            }
            for (int j = 0; j < d; j++) {
                w[j] -= learningRate * (grad[j] / n + l2 * w[j]);
            }
            b -= learningRate * gradB / n;
        }
        return new LogisticModel(List.copyOf(RiskFeatures.NAMES), mean, std, w, b, 0.5, provenance);
    }
}

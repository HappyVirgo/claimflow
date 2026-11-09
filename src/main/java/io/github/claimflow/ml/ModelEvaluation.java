package io.github.claimflow.ml;

import io.github.claimflow.risk.LogisticModel;
import java.util.Arrays;
import java.util.List;

/**
 * Held-out evaluation of a model on rows of the synthetic dataset.
 *
 * @param rows          number of evaluated rows
 * @param positiveRate  share of rows with label 1
 * @param auc           ROC AUC
 * @param atThreshold   precision / recall of the "risky" class at the model threshold
 * @param fastTrackRate share of rows scoring below the threshold (eligible for fast-track by the model)
 * @param missedPositiveRate share of fast-track-eligible rows that are actually positive
 */
public record ModelEvaluation(int rows, double positiveRate, double auc, ClassificationMetrics.AtThreshold atThreshold,
                              double fastTrackRate, double missedPositiveRate) {

    /** Evaluates {@code model} on the given rows at the model's own threshold. */
    public static ModelEvaluation of(LogisticModel model, List<SyntheticDataset.Row> rows) {
        double[] scores = rows.stream().mapToDouble(r -> model.predict(r.features().toVector())).toArray();
        int[] labels = rows.stream().mapToInt(SyntheticDataset.Row::label).toArray();
        ClassificationMetrics.AtThreshold at = ClassificationMetrics.at(scores, labels, model.threshold());
        int below = 0;
        int belowPositive = 0;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] < model.threshold()) {
                below++;
                belowPositive += labels[i];
            }
        }
        double positives = Arrays.stream(labels).sum();
        return new ModelEvaluation(rows.size(), positives / rows.size(), ClassificationMetrics.auc(scores, labels), at,
                below / (double) rows.size(), below == 0 ? 0 : belowPositive / (double) below);
    }
}

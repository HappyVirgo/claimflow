package io.github.claimflow.ml;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/** Binary-classification metrics used by training and evaluation. */
public final class ClassificationMetrics {

    /** Confusion-matrix summary at one threshold (positive = score at or above threshold). */
    public record AtThreshold(double threshold, double precision, double recall, double flaggedRate) {
    }

    private ClassificationMetrics() {
    }

    /** ROC AUC via the rank-sum (Mann-Whitney U) formulation, with ties given average rank. */
    public static double auc(double[] scores, int[] labels) {
        int n = scores.length;
        Integer[] idx = IntStream.range(0, n).boxed().toArray(Integer[]::new);
        Arrays.sort(idx, Comparator.comparingDouble(i -> scores[i]));
        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && scores[idx[j + 1]] == scores[idx[i]]) {
                j++;
            }
            double avg = (i + j) / 2.0 + 1;
            for (int k = i; k <= j; k++) {
                ranks[idx[k]] = avg;
            }
            i = j + 1;
        }
        long pos = Arrays.stream(labels).filter(l -> l == 1).count();
        long neg = n - pos;
        if (pos == 0 || neg == 0) {
            throw new IllegalArgumentException("AUC needs both classes");
        }
        double rankSum = 0;
        for (int k = 0; k < n; k++) {
            if (labels[k] == 1) {
                rankSum += ranks[k];
            }
        }
        return (rankSum - pos * (pos + 1) / 2.0) / (pos * (double) neg);
    }

    /** Precision, recall and flagged fraction when scores at or above {@code threshold} are flagged. */
    public static AtThreshold at(double[] scores, int[] labels, double threshold) {
        int tp = 0;
        int fp = 0;
        int fn = 0;
        for (int i = 0; i < scores.length; i++) {
            boolean flagged = scores[i] >= threshold;
            if (flagged && labels[i] == 1) {
                tp++;
            } else if (flagged) {
                fp++;
            } else if (labels[i] == 1) {
                fn++;
            }
        }
        double precision = tp + fp == 0 ? 0 : tp / (double) (tp + fp);
        double recall = tp + fn == 0 ? 0 : tp / (double) (tp + fn);
        return new AtThreshold(threshold, precision, recall, (tp + fp) / (double) scores.length);
    }

    /** Highest threshold whose recall is at least {@code targetRecall} (maximises the unflagged share). */
    public static double thresholdForRecall(double[] scores, int[] labels, double targetRecall) {
        double[] sorted = Arrays.stream(scores).distinct().sorted().toArray();
        for (int k = sorted.length - 1; k >= 0; k--) {
            if (at(scores, labels, sorted[k]).recall() >= targetRecall) {
                return sorted[k];
            }
        }
        return sorted[0];
    }
}

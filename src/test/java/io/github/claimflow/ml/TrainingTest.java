package io.github.claimflow.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.claimflow.risk.LogisticModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrainingTest {

    @Test
    void aucOfPerfectRandomAndInvertedRankings() {
        int[] labels = {0, 0, 1, 1};
        assertThat(ClassificationMetrics.auc(new double[] {0.1, 0.2, 0.8, 0.9}, labels)).isEqualTo(1.0);
        assertThat(ClassificationMetrics.auc(new double[] {0.9, 0.8, 0.2, 0.1}, labels)).isEqualTo(0.0);
        assertThat(ClassificationMetrics.auc(new double[] {0.5, 0.5, 0.5, 0.5}, labels)).isEqualTo(0.5);
    }

    @Test
    void precisionRecallAtThreshold() {
        double[] scores = {0.1, 0.4, 0.6, 0.9};
        int[] labels = {0, 1, 0, 1};
        ClassificationMetrics.AtThreshold at = ClassificationMetrics.at(scores, labels, 0.5);
        assertThat(at.precision()).isEqualTo(0.5);
        assertThat(at.recall()).isEqualTo(0.5);
        assertThat(at.flaggedRate()).isEqualTo(0.5);
        assertThat(ClassificationMetrics.thresholdForRecall(scores, labels, 1.0)).isEqualTo(0.4);
    }

    @Test
    void generatorIsDeterministicAndRoundTripsThroughCsv(@TempDir Path dir) throws Exception {
        List<SyntheticDataset.Row> a = SyntheticDataset.generate(500, 7L, 0.2);
        assertThat(SyntheticDataset.generate(500, 7L, 0.2)).isEqualTo(a);
        Path csv = dir.resolve("d.csv");
        SyntheticDataset.write(a, csv);
        List<SyntheticDataset.Row> back = SyntheticDataset.read(csv);
        assertThat(back).hasSize(500);
        assertThat(back.get(0).label()).isEqualTo(a.get(0).label());
        assertThat(back.get(0).features().amountToLimit()).isCloseTo(a.get(0).features().amountToLimit(),
                within(1e-5));
        assertThat(Files.readAllLines(csv).get(0)).startsWith("amount_to_limit,");
    }

    @Test
    void trainerLearnsGeneratingDirectionsAndBeatsChance() {
        List<SyntheticDataset.Row> rows = SyntheticDataset.generate(4000, 11L, 0.25);
        List<SyntheticDataset.Row> train = rows.stream().filter(r -> !r.test()).toList();
        double[][] x = train.stream().map(r -> r.features().toVector()).toArray(double[][]::new);
        int[] y = train.stream().mapToInt(SyntheticDataset.Row::label).toArray();
        LogisticModel m = new LogisticRegressionTrainer(0.2, 800, 1e-4).fit(x, y, "test");
        assertThat(m.weights()[1]).as("days to report raises risk").isPositive();
        assertThat(m.weights()[2]).as("policy age lowers risk").isNegative();
        assertThat(m.weights()[6]).as("police report lowers risk").isNegative();
        ModelEvaluation eval = ModelEvaluation.of(m, rows.stream().filter(SyntheticDataset.Row::test).toList());
        assertThat(eval.auc()).isGreaterThan(0.7);
    }
}

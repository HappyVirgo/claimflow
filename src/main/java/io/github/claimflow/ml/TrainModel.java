package io.github.claimflow.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.claimflow.risk.LogisticModel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Offline training step. Generates the seeded synthetic dataset, fits the model on the train split,
 * picks the threshold that keeps recall of the risky class at {@link #TARGET_RECALL} on the train split,
 * and writes the weights JSON that the service loads at runtime.
 *
 * <p>Run: {@code ./mvnw -q compile exec:java -Dexec.mainClass=io.github.claimflow.ml.TrainModel}
 */
public final class TrainModel {

    /** Dataset size. */
    public static final int ROWS = 12_000;
    /** Generator seed. */
    public static final long SEED = 20_261_106L;
    /** Share of rows held out for evaluation. */
    public static final double TEST_FRACTION = 0.2;
    /** Recall of the risky class the operating threshold is tuned to (on the train split). */
    public static final double TARGET_RECALL = 0.80;
    /** Where the dataset CSV is written. */
    public static final Path DATA_PATH = Path.of("data", "synthetic_claims.csv");
    /** Where the model JSON is written. */
    public static final Path MODEL_PATH = Path.of("src", "main", "resources", "model", "risk-model.json");

    private TrainModel() {
    }

    /** Entry point. */
    public static void main(String[] args) throws IOException {
        List<SyntheticDataset.Row> rows = SyntheticDataset.generate(ROWS, SEED, TEST_FRACTION);
        SyntheticDataset.write(rows, DATA_PATH);
        List<SyntheticDataset.Row> train = rows.stream().filter(r -> !r.test()).toList();
        List<SyntheticDataset.Row> test = rows.stream().filter(SyntheticDataset.Row::test).toList();

        double[][] x = train.stream().map(r -> r.features().toVector()).toArray(double[][]::new);
        int[] y = train.stream().mapToInt(SyntheticDataset.Row::label).toArray();
        String provenance = String.format(Locale.ROOT,
                "SYNTHETIC data: SyntheticDataset seed=%d rows=%d train=%d", SEED, ROWS, train.size());
        LogisticModel raw = new LogisticRegressionTrainer(0.2, 3000, 1e-4).fit(x, y, provenance);

        double[] trainScores = Arrays.stream(x).mapToDouble(raw::predict).toArray();
        double threshold = ClassificationMetrics.thresholdForRecall(trainScores, y, TARGET_RECALL);
        LogisticModel model = new LogisticModel(raw.features(), raw.mean(), raw.std(), raw.weights(),
                raw.intercept(), Math.round(threshold * 1e6) / 1e6, raw.trainedOn());

        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(MODEL_PATH.toFile(), model);
        ModelEvaluation eval = ModelEvaluation.of(model, test);
        System.out.printf(Locale.ROOT, "train=%d test=%d threshold=%.4f AUC=%.4f precision=%.3f recall=%.3f "
                        + "fastTrack=%.3f%nwrote %s and %s%n", train.size(), test.size(), model.threshold(), eval.auc(),
                eval.atThreshold().precision(), eval.atThreshold().recall(), eval.fastTrackRate(), DATA_PATH,
                MODEL_PATH);
    }
}

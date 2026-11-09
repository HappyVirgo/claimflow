package io.github.claimflow.ml;

import io.github.claimflow.risk.RiskFeatures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Seeded generator for a SYNTHETIC labelled claims dataset. No real claims data is involved.
 *
 * <p>Generating process: each feature is drawn independently from a hand-picked distribution
 * (see {@link #sample}); a latent logit is a hand-written linear function of the transformed features
 * plus one non-linear interaction (new policy AND large claim) and Gaussian noise; the label is a
 * Bernoulli draw from the sigmoid of that logit. "Label = 1" stands for "would be referred to a special
 * investigations unit" under this made-up process. Because the model family nearly matches the
 * generating process, metrics on this data are optimistic compared with real claims.
 */
public final class SyntheticDataset {

    /** One labelled example. */
    public record Row(RiskFeatures features, int label, boolean test) {
    }

    /** Intercept of the latent logit, tuned so that roughly 8% of rows are positive. */
    static final double INTERCEPT = -1.2;
    private static final String HEADER = "amount_to_limit,days_to_report,policy_age_days,prior_claims_12m,"
            + "is_property,document_count,has_police_report,round_amount,line_item_count,label,split";

    private SyntheticDataset() {
    }

    /** Generates {@code n} rows; {@code testFraction} of them (at random) are marked as the held-out split. */
    public static List<Row> generate(int n, long seed, double testFraction) {
        SplittableRandom rng = new SplittableRandom(seed);
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            RiskFeatures f = sample(rng);
            int label = rng.nextDouble() < sigmoid(latentLogit(f, rng)) ? 1 : 0;
            rows.add(new Row(f, label, rng.nextDouble() < testFraction));
        }
        return rows;
    }

    static RiskFeatures sample(RandomGenerator rng) {
        int property = rng.nextDouble() < 0.4 ? 1 : 0;
        int policyAge = rng.nextDouble() < 0.15 ? rng.nextInt(60) : rng.nextInt(1500);
        int daysToReport = (int) (rng.nextDouble() < 0.1 ? rng.nextExponential() * 45 : rng.nextExponential() * 4);
        double ratio = Math.min(1.5, Math.max(0.002, Math.exp(Math.log(0.08) + 0.9 * rng.nextGaussian())));
        int police = rng.nextDouble() < (property == 1 ? 0.15 : 0.5) ? 1 : 0;
        int round = rng.nextDouble() < 0.15 ? 1 : 0;
        return new RiskFeatures(ratio, daysToReport, policyAge, poisson(rng, 0.3), property,
                poisson(rng, 2.0), police, round, 1 + poisson(rng, 1.2));
    }

    static double latentLogit(RiskFeatures f, RandomGenerator rng) {
        double[] x = f.toVector();
        double logit = INTERCEPT
                + 1.8 * x[0]            // amount relative to limit
                + 0.5 * x[1]            // log days to report
                - 0.45 * x[2]           // log policy age (new policies are riskier)
                + 0.6 * x[3]            // prior claims
                + 0.3 * x[4]            // property
                - 0.25 * x[5]           // documents reduce risk
                - 0.7 * x[6]            // police report reduces risk
                + 0.9 * x[7]            // suspiciously round amount
                + 0.1 * x[8];           // more line items
        if (f.policyAgeDays() < 60 && f.amountToLimit() > 0.3) {
            logit += 1.0;               // interaction the linear model cannot represent exactly
        }
        return logit + 0.5 * rng.nextGaussian();
    }

    private static int poisson(RandomGenerator rng, double lambda) {
        double l = Math.exp(-lambda);
        double p = 1.0;
        int k = 0;
        do {
            k++;
            p *= rng.nextDouble();
        } while (p > l);
        return k - 1;
    }

    static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    /** Writes rows as CSV with a header. */
    public static void write(List<Row> rows, Path path) throws IOException {
        List<String> lines = new ArrayList<>(rows.size() + 1);
        lines.add(HEADER);
        for (Row r : rows) {
            RiskFeatures f = r.features();
            lines.add(String.format(Locale.ROOT, "%.5f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s", f.amountToLimit(),
                    f.daysToReport(), f.policyAgeDays(), f.priorClaims12m(), f.property(), f.documentCount(),
                    f.policeReport(), f.roundAmount(), f.lineItemCount(), r.label(), r.test() ? "test" : "train"));
        }
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.write(path, lines);
    }

    /** Reads rows written by {@link #write}. */
    public static List<Row> read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty() || !lines.get(0).equals(HEADER)) {
            throw new IOException("Unexpected CSV header in " + path);
        }
        List<Row> rows = new ArrayList<>(lines.size() - 1);
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            RiskFeatures f = new RiskFeatures(Double.parseDouble(c[0]), Integer.parseInt(c[1]),
                    Integer.parseInt(c[2]), Integer.parseInt(c[3]), Integer.parseInt(c[4]), Integer.parseInt(c[5]),
                    Integer.parseInt(c[6]), Integer.parseInt(c[7]), Integer.parseInt(c[8]));
            rows.add(new Row(f, Integer.parseInt(c[9]), "test".equals(c[10])));
        }
        return rows;
    }
}

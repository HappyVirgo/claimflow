package io.github.claimflow.bench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.claimflow.ClaimflowApplication;
import io.github.claimflow.ml.ModelEvaluation;
import io.github.claimflow.ml.SyntheticDataset;
import io.github.claimflow.ml.TrainModel;
import io.github.claimflow.risk.LogisticModel;
import io.github.claimflow.risk.RiskFeatures;
import io.github.claimflow.risk.RiskScorer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reproducible benchmark: (a) held-out metrics of the bundled risk model on the synthetic test split and
 * (b) in-process HTTP load test of FNOL intake + triage against an H2 in-memory database.
 * Writes {@code bench/RESULTS.md}.
 *
 * <p>Run: {@code ./mvnw -q test-compile exec:java -Dexec.classpathScope=test
 * -Dexec.mainClass=io.github.claimflow.bench.BenchmarkMain}
 */
public final class BenchmarkMain {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private static final int WARMUP = 1_500;
    private static final int MEASURED = 5_000;
    private static final int[] CONCURRENCY = {1, 8};
    private static final int BENCH_POLICIES = 2_000;

    private record LoadResult(int concurrency, int requests, double seconds, double[] latenciesMs,
                              Map<String, Integer> statuses) {
    }

    private BenchmarkMain() {
    }

    /** Entry point. */
    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LogisticModel model = mapper.readValue(TrainModel.MODEL_PATH.toFile(), LogisticModel.class);
        List<SyntheticDataset.Row> test = SyntheticDataset.read(TrainModel.DATA_PATH).stream()
                .filter(SyntheticDataset.Row::test).toList();
        ModelEvaluation eval = ModelEvaluation.of(model, test);
        double scoresPerSec = scoringThroughput(new RiskScorer(model), test);

        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ClaimflowApplication.class)
                .properties("server.port=0", "claimflow.clock.fixed-date=" + TODAY,
                        "spring.datasource.url=jdbc:h2:mem:bench;DB_CLOSE_DELAY=-1", "logging.level.root=WARN",
                        "spring.main.banner-mode=off",
                        // the Tomcat acceptor logs a spurious AsynchronousCloseException when exec:java stops it
                        "logging.level.org.apache.tomcat.util.net.Acceptor=OFF")
                .run();
        List<LoadResult> results = new ArrayList<>();
        try {
            int port = ((ServletWebServerApplicationContext) ctx).getWebServer().getPort();
            HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            URI uri = URI.create("http://localhost:" + port + "/api/claims");
            SplittableRandom rng = new SplittableRandom(42);
            insertPolicies(ctx.getBean(JdbcTemplate.class), rng);
            load(http, uri, fnolBodies(rng, WARMUP, "warm"), 8);
            for (int c : CONCURRENCY) {
                results.add(load(http, uri, fnolBodies(rng, MEASURED, "c" + c), c));
            }
        } finally {
            ctx.close();
        }
        String md = render(eval, model, scoresPerSec, results);
        Files.createDirectories(Path.of("bench"));
        Files.writeString(Path.of("bench", "RESULTS.md"), md);
        System.out.println(md);
    }

    private static double scoringThroughput(RiskScorer scorer, List<SyntheticDataset.Row> rows) {
        RiskFeatures[] fs = rows.stream().map(SyntheticDataset.Row::features).toArray(RiskFeatures[]::new);
        double sink = 0;
        for (int i = 0; i < 200_000; i++) {
            sink += scorer.assess(fs[i % fs.length]).score();
        }
        int n = 1_000_000;
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            sink += scorer.assess(fs[i % fs.length]).score();
        }
        double secs = (System.nanoTime() - t0) / 1e9;
        if (sink == Double.MIN_VALUE) {
            System.out.println(sink);
        }
        return n / secs;
    }

    /** Inserts SYNTHETIC policies (ids 1000+) so claims spread over a realistic number of policies. */
    private static void insertPolicies(JdbcTemplate jdbc, SplittableRandom rng) {
        List<Object[]> rows = new ArrayList<>(BENCH_POLICIES);
        for (int i = 0; i < BENCH_POLICIES; i++) {
            boolean auto = i % 2 == 0;
            LocalDate effective = LocalDate.of(2024, 12, 1).plusDays(rng.nextInt(670));
            rows.add(new Object[] {1000 + i, policyNumber(i), auto ? "AUTO" : "PROPERTY", effective,
                effective.plusYears(2), false, auto ? 50_000 : 300_000, auto ? 500 : 1000,
                auto ? "AUTO_LIABILITY,COLLISION,COMPREHENSIVE" : "CONTENTS,DWELLING,LOSS_OF_USE"});
        }
        jdbc.batchUpdate("INSERT INTO policy (id, policy_number, line_of_business, effective_date, expiration_date, "
                + "cancelled, coverage_limit, deductible, coverages) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
    }

    private static String policyNumber(int i) {
        return String.format(Locale.ROOT, "POL-BENCH-%05d", i);
    }

    /** Synthetic FNOL payloads with randomised policy, amount, dates and documents. */
    private static List<String> fnolBodies(SplittableRandom rng, int n, String tag) {
        List<String> bodies = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int p = rng.nextInt(BENCH_POLICIES);
            boolean auto = p % 2 == 0;
            String coverage = auto ? (rng.nextBoolean() ? "COLLISION" : "COMPREHENSIVE")
                    : (rng.nextBoolean() ? "DWELLING" : "CONTENTS");
            double amount = Math.round(Math.exp(Math.log(3000) + 0.9 * rng.nextGaussian()) * 100) / 100.0 + 600;
            LocalDate loss = TODAY.minusDays(rng.nextDouble() < 0.1 ? 31 + rng.nextInt(60) : rng.nextInt(10));
            StringBuilder docs = new StringBuilder("[");
            int docCount = rng.nextInt(4);
            for (int d = 0; d < docCount; d++) {
                String type = d == 0 && auto && rng.nextBoolean() ? "POLICE_REPORT" : "PHOTO";
                docs.append(d > 0 ? "," : "").append("{\"type\":\"").append(type)
                        .append("\",\"fileName\":\"f").append(d).append(".jpg\",\"contentType\":\"image/jpeg\","
                                + "\"sizeBytes\":2048}");
            }
            docs.append(']');
            bodies.add(String.format(Locale.ROOT, """
                    {"policyNumber":"%s","type":"%s","lossDate":"%s","description":"Synthetic benchmark loss",
                     "claimant":{"firstName":"Bench","lastName":"User%d","email":"bench-%s-%d@example.test"},
                     "lineItems":[{"coverage":"%s","description":"repair","amount":%.2f}],"documents":%s}""",
                    policyNumber(p), auto ? "AUTO" : "PROPERTY", loss, i, tag, i, coverage, amount, docs));
        }
        return bodies;
    }

    private static LoadResult load(HttpClient http, URI uri, List<String> bodies, int concurrency) throws Exception {
        double[] latencies = new double[bodies.size()];
        Map<String, Integer> statuses = new ConcurrentHashMap<>();
        AtomicInteger next = new AtomicInteger();
        ObjectMapper mapper = new ObjectMapper();
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        long t0 = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        for (int w = 0; w < concurrency; w++) {
            futures.add(pool.submit(() -> {
                for (int i = next.getAndIncrement(); i < bodies.size(); i = next.getAndIncrement()) {
                    HttpRequest req = HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(bodies.get(i))).build();
                    long s = System.nanoTime();
                    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                    latencies[i] = (System.nanoTime() - s) / 1e6;
                    String key = resp.statusCode() == 201
                            ? mapper.readTree(resp.body()).path("status").asText() : "HTTP_" + resp.statusCode();
                    statuses.merge(key, 1, Integer::sum);
                }
                return null;
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        double seconds = (System.nanoTime() - t0) / 1e9;
        pool.shutdown();
        return new LoadResult(concurrency, bodies.size(), seconds, latencies, new TreeMap<>(statuses));
    }

    private static double percentile(double[] values, double p) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[(int) Math.ceil(p / 100.0 * sorted.length) - 1];
    }

    private static String render(ModelEvaluation e, LogisticModel m, double scoresPerSec, List<LoadResult> loads) {
        StringBuilder sb = new StringBuilder();
        sb.append("# claimflow benchmark results\n\n");
        sb.append("Generated by `BenchmarkMain` on ").append(LocalDate.now()).append(".\n\n");
        sb.append("- Machine: ").append(cpu()).append(", ").append(Runtime.getRuntime().availableProcessors())
                .append(" logical CPUs, ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.version")).append('\n');
        sb.append("- Runtime: Java ").append(System.getProperty("java.version")).append(" (")
                .append(System.getProperty("java.vm.name")).append(")\n");
        sb.append("- Data: (a) `data/synthetic_claims.csv` test split; (b) seeded random FNOL payloads against ")
                .append(BENCH_POLICIES).append(" generated policies. All data is SYNTHETIC.\n\n");
        sb.append("## (a) Risk model on held-out synthetic split\n\n");
        sb.append("| metric | value |\n|---|---|\n");
        row(sb, "test rows", String.valueOf(e.rows()));
        row(sb, "positive (risky) rate", pct(e.positiveRate()));
        row(sb, "ROC AUC", fmt(e.auc(), 4));
        row(sb, "threshold (chosen on train split for recall >= 0.80)", fmt(m.threshold(), 4));
        row(sb, "precision @ threshold", fmt(e.atThreshold().precision(), 3));
        row(sb, "recall @ threshold", fmt(e.atThreshold().recall(), 3));
        row(sb, "model fast-track rate (score < threshold)", pct(e.fastTrackRate()));
        row(sb, "risky rate among model-fast-tracked rows", pct(e.missedPositiveRate()));
        row(sb, "scoring throughput (in-process, 1 thread)", String.format(Locale.ROOT, "%,.0f scores/s", scoresPerSec));
        sb.append("\n## (b) FNOL intake + triage over HTTP (in-process Tomcat, H2 in-memory)\n\n");
        sb.append(String.format(Locale.ROOT, "Each request: validate, persist claim + claimant, run 6 rules, score, "
                + "2-3 state transitions with audit rows, return JSON. Warm-up: %d requests. "
                + "Client: java.net.http on the same machine.%n%n", WARMUP));
        sb.append("| concurrency | requests | claims/s | p50 ms | p95 ms | p99 ms | outcomes |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for (LoadResult r : loads) {
            sb.append(String.format(Locale.ROOT, "| %d | %d | %.0f | %.2f | %.2f | %.2f | %s |%n", r.concurrency(),
                    r.requests(), r.requests() / r.seconds(), percentile(r.latenciesMs(), 50),
                    percentile(r.latenciesMs(), 95), percentile(r.latenciesMs(), 99), r.statuses()));
        }
        return sb.toString();
    }

    private static String cpu() {
        try {
            Path cpuinfo = Path.of("/proc/cpuinfo");
            if (Files.exists(cpuinfo)) {
                return Files.readAllLines(cpuinfo).stream().filter(l -> l.startsWith("model name"))
                        .map(l -> l.substring(l.indexOf(':') + 1).trim()).findFirst().orElse("unknown CPU");
            }
        } catch (java.io.IOException ignored) {
            return "unknown CPU";
        }
        return System.getProperty("bench.cpu", String.valueOf(System.getenv("PROCESSOR_IDENTIFIER")));
    }

    private static void row(StringBuilder sb, String k, String v) {
        sb.append("| ").append(k).append(" | ").append(v).append(" |\n");
    }

    private static String fmt(double v, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", v);
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v * 100);
    }
}

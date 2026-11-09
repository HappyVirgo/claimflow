package io.github.claimflow.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Scores claims with the logistic-regression model loaded from a JSON weights file at startup. */
@Component
public class RiskScorer {

    /** Scores at or above this value are banded HIGH. */
    public static final double HIGH_BAND = 0.5;
    private static final int TOP_FACTORS = 3;

    private final LogisticModel model;

    /** Loads the model from the configured resource ({@code claimflow.risk.model-path}). */
    @Autowired
    public RiskScorer(@Value("${claimflow.risk.model-path:classpath:model/risk-model.json}") Resource modelResource,
                      ObjectMapper mapper) {
        try (InputStream in = modelResource.getInputStream()) {
            this.model = mapper.readValue(in, LogisticModel.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load risk model from " + modelResource, e);
        }
    }

    /** Creates a scorer around an in-memory model (tests, offline evaluation). */
    public RiskScorer(LogisticModel model) {
        this.model = model;
    }

    /** The loaded model. */
    public LogisticModel model() {
        return model;
    }

    /** Scores one claim and explains the result with its top contributing features. */
    public RiskAssessment assess(RiskFeatures features) {
        double[] x = features.toVector();
        double score = model.predict(x);
        double[] contrib = model.contributions(x);
        List<RiskAssessment.Factor> factors = new ArrayList<>(x.length);
        for (int i = 0; i < x.length; i++) {
            factors.add(new RiskAssessment.Factor(model.features().get(i), x[i], round(contrib[i])));
        }
        factors.sort(Comparator.comparingDouble((RiskAssessment.Factor f) -> Math.abs(f.contribution())).reversed());
        RiskAssessment.Band band = score < model.threshold() ? RiskAssessment.Band.LOW
                : score < HIGH_BAND ? RiskAssessment.Band.MEDIUM : RiskAssessment.Band.HIGH;
        return new RiskAssessment(round(score), band, model.threshold(), List.copyOf(factors.subList(0, TOP_FACTORS)));
    }

    private static double round(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }
}

package io.github.claimflow.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.claimflow.domain.ClaimDocument;
import io.github.claimflow.domain.ClaimType;
import io.github.claimflow.domain.CoverageType;
import io.github.claimflow.domain.DocumentType;
import io.github.claimflow.domain.LineItem;
import io.github.claimflow.domain.Policy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class RiskScoringTest {

    private final RiskScorer bundled = new RiskScorer(new ClassPathResource("model/risk-model.json"), new ObjectMapper());

    private static RiskFeatures features(double ratio, int days, int age, int prior, int docs, int police, int round) {
        return new RiskFeatures(ratio, days, age, prior, 0, docs, police, round, 1);
    }

    @Test
    void bundledModelLoadsAndMatchesFeatureDefinition() {
        LogisticModel m = bundled.model();
        assertThat(m.features()).isEqualTo(RiskFeatures.NAMES);
        assertThat(m.threshold()).isBetween(0.0, 1.0);
        assertThat(m.trainedOn()).startsWith("SYNTHETIC");
    }

    @Test
    void riskierProfileScoresHigherAndIsExplained() {
        RiskAssessment low = bundled.assess(features(0.02, 0, 1200, 0, 4, 1, 0));
        RiskAssessment high = bundled.assess(features(0.9, 60, 10, 3, 0, 0, 1));
        assertThat(high.score()).isGreaterThan(low.score());
        assertThat(low.band()).isEqualTo(RiskAssessment.Band.LOW);
        assertThat(high.band()).isEqualTo(RiskAssessment.Band.HIGH);
        assertThat(high.topFactors()).hasSize(3);
        assertThat(Math.abs(high.topFactors().get(0).contribution()))
                .isGreaterThanOrEqualTo(Math.abs(high.topFactors().get(2).contribution()));
    }

    @Test
    void predictIsSigmoidOfStandardisedLinearTerm() {
        LogisticModel m = new LogisticModel(RiskFeatures.NAMES, new double[9], ones(), weightOnFirst(2.0), -1.0,
                0.3, "unit");
        assertThat(m.predict(new double[] {0.5, 0, 0, 0, 0, 0, 0, 0, 0})).isCloseTo(0.5, within(1e-12));
        assertThat(m.contributions(new double[] {1.5, 0, 0, 0, 0, 0, 0, 0, 0})[0]).isCloseTo(3.0, within(1e-12));
    }

    @Test
    void modelRejectsMismatchedDimensions() {
        assertThatThrownBy(() -> new LogisticModel(RiskFeatures.NAMES, new double[3], ones(), ones(), 0, 0.5, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extractorComputesFeaturesFromIntakeData() {
        Policy p = new Policy(1L, "P", ClaimType.AUTO, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), false,
                new BigDecimal("10000"), new BigDecimal("500"), EnumSet.of(CoverageType.COLLISION));
        RiskFeatures f = RiskFeatureExtractor.extract(p, ClaimType.AUTO, LocalDate.of(2026, 1, 11),
                LocalDate.of(2026, 1, 14),
                List.of(new LineItem(CoverageType.COLLISION, "bumper", new BigDecimal("1500.00")),
                        new LineItem(CoverageType.COLLISION, "paint", new BigDecimal("500.00"))),
                List.of(new ClaimDocument(DocumentType.POLICE_REPORT, "r.pdf", "application/pdf", 10)), 2);
        assertThat(f).isEqualTo(new RiskFeatures(0.2, 3, 10, 2, 0, 1, 1, 1, 2));
    }

    private static double[] ones() {
        double[] a = new double[9];
        java.util.Arrays.fill(a, 1.0);
        return a;
    }

    private static double[] weightOnFirst(double w) {
        double[] a = new double[9];
        a[0] = w;
        return a;
    }
}

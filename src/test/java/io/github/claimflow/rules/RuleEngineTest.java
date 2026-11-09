package io.github.claimflow.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.claimflow.domain.ClaimType;
import io.github.claimflow.domain.CoverageType;
import io.github.claimflow.domain.LineItem;
import io.github.claimflow.domain.Policy;
import io.github.claimflow.rules.RuleResult.Outcome;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    private static final LocalDate LOSS = LocalDate.of(2026, 6, 10);
    private final RuleEngine engine = new RuleEngine();

    private static Policy autoPolicy(boolean cancelled) {
        return new Policy(1L, "POL-T-1", ClaimType.AUTO, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                cancelled, new BigDecimal("10000.00"), new BigDecimal("500.00"),
                EnumSet.of(CoverageType.COLLISION, CoverageType.AUTO_LIABILITY));
    }

    private static RuleContext ctx(Policy p, ClaimType type, LocalDate loss, LocalDate reported, String amount,
                                   CoverageType coverage, List<RuleContext.PriorClaim> nearby) {
        return new RuleContext(p, type, loss, reported,
                List.of(new LineItem(coverage, "item", new BigDecimal(amount))), nearby);
    }

    private static RuleContext clean(String amount) {
        return ctx(autoPolicy(false), ClaimType.AUTO, LOSS, LOSS.plusDays(2), amount, CoverageType.COLLISION, List.of());
    }

    private static Outcome outcome(RuleEvaluation e, String code) {
        return e.results().stream().filter(r -> r.code().equals(code)).findFirst().orElseThrow().outcome();
    }

    @Test
    void cleanClaimPassesEverythingAndNetsDeductible() {
        RuleEvaluation e = engine.evaluate(clean("2500.00"));
        assertThat(e.allPassed()).isTrue();
        assertThat(e.results()).hasSize(6);
        assertThat(e.payableAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void uncoveredLineItemFailsCoverage() {
        RuleEvaluation e = engine.evaluate(ctx(autoPolicy(false), ClaimType.AUTO, LOSS, LOSS, "900",
                CoverageType.COMPREHENSIVE, List.of()));
        assertThat(outcome(e, "COVERAGE")).isEqualTo(Outcome.FAIL);
    }

    @Test
    void claimTypeMismatchFailsCoverage() {
        RuleEvaluation e = engine.evaluate(ctx(autoPolicy(false), ClaimType.PROPERTY, LOSS, LOSS, "900",
                CoverageType.DWELLING, List.of()));
        assertThat(outcome(e, "COVERAGE")).isEqualTo(Outcome.FAIL);
    }

    @Test
    void lossOutsideTermOrCancelledPolicyFails() {
        LocalDate before = LocalDate.of(2025, 12, 31);
        assertThat(outcome(engine.evaluate(ctx(autoPolicy(false), ClaimType.AUTO, before, LOSS, "900",
                CoverageType.COLLISION, List.of())), "POLICY_ACTIVE")).isEqualTo(Outcome.FAIL);
        assertThat(outcome(engine.evaluate(ctx(autoPolicy(true), ClaimType.AUTO, LOSS, LOSS, "900",
                CoverageType.COLLISION, List.of())), "POLICY_ACTIVE")).isEqualTo(Outcome.FAIL);
        LocalDate lastDay = LocalDate.of(2026, 12, 31);
        assertThat(outcome(engine.evaluate(ctx(autoPolicy(false), ClaimType.AUTO, lastDay, lastDay, "900",
                CoverageType.COLLISION, List.of())), "POLICY_ACTIVE")).isEqualTo(Outcome.PASS);
    }

    @Test
    void amountAtOrBelowDeductibleFailsAndPaysZero() {
        RuleEvaluation e = engine.evaluate(clean("500.00"));
        assertThat(outcome(e, "DEDUCTIBLE")).isEqualTo(Outcome.FAIL);
        assertThat(e.payableAmount()).isEqualByComparingTo("0");
    }

    @Test
    void nearbySimilarClaimIsDuplicate() {
        var prior = new RuleContext.PriorClaim("CLM-OLD", LOSS.minusDays(2), new BigDecimal("2600"));
        RuleEvaluation e = engine.evaluate(ctx(autoPolicy(false), ClaimType.AUTO, LOSS, LOSS, "2500",
                CoverageType.COLLISION, List.of(prior)));
        assertThat(outcome(e, "DUPLICATE")).isEqualTo(Outcome.FAIL);
    }

    @Test
    void nearbyClaimWithDifferentAmountIsNotDuplicate() {
        var prior = new RuleContext.PriorClaim("CLM-OLD", LOSS.minusDays(1), new BigDecimal("9000"));
        RuleEvaluation e = engine.evaluate(ctx(autoPolicy(false), ClaimType.AUTO, LOSS, LOSS, "2500",
                CoverageType.COLLISION, List.of(prior)));
        assertThat(outcome(e, "DUPLICATE")).isEqualTo(Outcome.PASS);
    }

    @Test
    void lateReportWarnsOnlyPastThreshold() {
        Policy p = autoPolicy(false);
        assertThat(outcome(engine.evaluate(ctx(p, ClaimType.AUTO, LOSS, LOSS.plusDays(30), "900",
                CoverageType.COLLISION, List.of())), "LATE_REPORTING")).isEqualTo(Outcome.PASS);
        assertThat(outcome(engine.evaluate(ctx(p, ClaimType.AUTO, LOSS, LOSS.plusDays(31), "900",
                CoverageType.COLLISION, List.of())), "LATE_REPORTING")).isEqualTo(Outcome.WARN);
    }

    @Test
    void overLimitWarnsAndCapsPayable() {
        RuleEvaluation e = engine.evaluate(clean("15000.00"));
        assertThat(outcome(e, "POLICY_LIMIT")).isEqualTo(Outcome.WARN);
        assertThat(e.hasFailures()).isFalse();
        assertThat(e.payableAmount()).isEqualByComparingTo("10000.00");
    }
}

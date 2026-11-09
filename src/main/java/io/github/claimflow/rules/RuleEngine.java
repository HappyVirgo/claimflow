package io.github.claimflow.rules;

import io.github.claimflow.domain.CoverageType;
import io.github.claimflow.domain.LineItem;
import io.github.claimflow.domain.Policy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Deterministic underwriting/coverage rules. Each rule is a pure function of {@link RuleContext};
 * the engine runs all of them (no short-circuit) so adjusters see every finding at once.
 */
@Component
public class RuleEngine {

    /** Days after the loss beyond which a report is considered late. */
    public static final int LATE_REPORT_DAYS = 30;
    /** Loss dates within this many days of another claim on the same policy are compared. */
    public static final int DUPLICATE_WINDOW_DAYS = 3;
    /** Relative amount difference under which two nearby claims are treated as duplicates. */
    public static final BigDecimal DUPLICATE_AMOUNT_TOLERANCE = new BigDecimal("0.10");

    private final List<Function<RuleContext, RuleResult>> rules = List.of(
            RuleEngine::coverage,
            RuleEngine::policyActive,
            RuleEngine::deductible,
            RuleEngine::duplicate,
            RuleEngine::lateReporting,
            RuleEngine::policyLimit);

    /** Runs every rule and computes the payable amount. */
    public RuleEvaluation evaluate(RuleContext ctx) {
        List<RuleResult> results = new ArrayList<>(rules.size());
        for (Function<RuleContext, RuleResult> rule : rules) {
            results.add(rule.apply(ctx));
        }
        return new RuleEvaluation(List.copyOf(results), payable(ctx));
    }

    /** Claimed total minus deductible, floored at zero and capped at the coverage limit. */
    static BigDecimal payable(RuleContext ctx) {
        Policy p = ctx.policy();
        BigDecimal net = ctx.totalClaimed().subtract(p.getDeductible()).max(BigDecimal.ZERO);
        return net.min(p.getCoverageLimit()).setScale(2, RoundingMode.HALF_UP);
    }

    static RuleResult coverage(RuleContext ctx) {
        String code = "COVERAGE";
        if (ctx.policy().getLineOfBusiness() != ctx.type()) {
            return RuleResult.fail(code, "Claim type " + ctx.type() + " does not match policy line "
                    + ctx.policy().getLineOfBusiness());
        }
        Set<CoverageType> covered = ctx.policy().coverageSet();
        String missing = ctx.lineItems().stream().map(LineItem::getCoverage).filter(c -> !covered.contains(c))
                .map(Enum::name).distinct().sorted().collect(Collectors.joining(", "));
        return missing.isEmpty()
                ? RuleResult.pass(code, "All line items fall under policy coverages")
                : RuleResult.fail(code, "Policy does not include coverage: " + missing);
    }

    static RuleResult policyActive(RuleContext ctx) {
        String code = "POLICY_ACTIVE";
        Policy p = ctx.policy();
        if (p.isActiveOn(ctx.lossDate())) {
            return RuleResult.pass(code, "Policy in force on loss date " + ctx.lossDate());
        }
        String why = p.isCancelled() ? "policy is cancelled"
                : "loss date outside term " + p.getEffectiveDate() + " to " + p.getExpirationDate();
        return RuleResult.fail(code, "Policy not in force: " + why);
    }

    static RuleResult deductible(RuleContext ctx) {
        String code = "DEDUCTIBLE";
        BigDecimal ded = ctx.policy().getDeductible();
        if (ctx.totalClaimed().compareTo(ded) <= 0) {
            return RuleResult.fail(code, "Claimed " + ctx.totalClaimed() + " does not exceed deductible " + ded);
        }
        return RuleResult.pass(code, "Deductible " + ded + " applied");
    }

    static RuleResult duplicate(RuleContext ctx) {
        String code = "DUPLICATE";
        BigDecimal total = ctx.totalClaimed();
        for (RuleContext.PriorClaim prior : ctx.nearbyClaims()) {
            long days = Math.abs(ChronoUnit.DAYS.between(prior.lossDate(), ctx.lossDate()));
            BigDecimal larger = total.max(prior.amount());
            boolean similarAmount = larger.signum() == 0 || total.subtract(prior.amount()).abs()
                    .divide(larger, 4, RoundingMode.HALF_UP).compareTo(DUPLICATE_AMOUNT_TOLERANCE) <= 0;
            if (days <= DUPLICATE_WINDOW_DAYS && similarAmount) {
                return RuleResult.fail(code, "Possible duplicate of " + prior.claimNumber()
                        + " (loss " + days + " day(s) apart, similar amount)");
            }
        }
        return RuleResult.pass(code, "No similar claim on this policy");
    }

    static RuleResult lateReporting(RuleContext ctx) {
        String code = "LATE_REPORTING";
        long days = ChronoUnit.DAYS.between(ctx.lossDate(), ctx.reportedDate());
        return days > LATE_REPORT_DAYS
                ? RuleResult.warn(code, "Reported " + days + " days after loss (limit " + LATE_REPORT_DAYS + ")")
                : RuleResult.pass(code, "Reported " + days + " day(s) after loss");
    }

    static RuleResult policyLimit(RuleContext ctx) {
        String code = "POLICY_LIMIT";
        BigDecimal limit = ctx.policy().getCoverageLimit();
        return ctx.totalClaimed().compareTo(limit) > 0
                ? RuleResult.warn(code, "Claimed " + ctx.totalClaimed() + " exceeds limit " + limit
                        + "; payable capped")
                : RuleResult.pass(code, "Within policy limit " + limit);
    }
}

package io.github.claimflow.risk;

import io.github.claimflow.domain.ClaimDocument;
import io.github.claimflow.domain.ClaimType;
import io.github.claimflow.domain.DocumentType;
import io.github.claimflow.domain.LineItem;
import io.github.claimflow.domain.Policy;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Builds {@link RiskFeatures} from intake data at runtime. Pure and static. */
public final class RiskFeatureExtractor {

    private static final BigDecimal ROUND_MIN = new BigDecimal("1000");
    private static final BigDecimal ROUND_STEP = new BigDecimal("500");

    private RiskFeatureExtractor() {
    }

    /**
     * Extracts features for a claim.
     *
     * @param priorClaims12m claims by the same claimant with a loss in the 365 days before this loss
     */
    public static RiskFeatures extract(Policy policy, ClaimType type, LocalDate lossDate, LocalDate reportedDate,
                                       List<LineItem> lineItems, List<ClaimDocument> documents,
                                       long priorClaims12m) {
        BigDecimal total = lineItems.stream().map(LineItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        double ratio = total.divide(policy.getCoverageLimit(), MathContext.DECIMAL64).doubleValue();
        boolean police = documents.stream().anyMatch(d -> d.getType() == DocumentType.POLICE_REPORT);
        boolean round = total.compareTo(ROUND_MIN) >= 0 && total.remainder(ROUND_STEP).signum() == 0;
        return new RiskFeatures(
                ratio,
                (int) Math.max(0, ChronoUnit.DAYS.between(lossDate, reportedDate)),
                (int) Math.max(0, ChronoUnit.DAYS.between(policy.getEffectiveDate(), lossDate)),
                (int) priorClaims12m,
                type == ClaimType.PROPERTY ? 1 : 0,
                documents.size(),
                police ? 1 : 0,
                round ? 1 : 0,
                lineItems.size());
    }
}

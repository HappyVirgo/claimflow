package io.github.claimflow.rules;

import io.github.claimflow.domain.ClaimType;
import io.github.claimflow.domain.LineItem;
import io.github.claimflow.domain.Policy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the rules need to evaluate a claim, gathered up front so rules stay pure.
 *
 * @param nearbyClaims other claims on the same policy with a loss date close to this one
 */
public record RuleContext(Policy policy, ClaimType type, LocalDate lossDate, LocalDate reportedDate,
                          List<LineItem> lineItems, List<PriorClaim> nearbyClaims) {

    /** A previously filed claim, reduced to the fields duplicate detection needs. */
    public record PriorClaim(String claimNumber, LocalDate lossDate, BigDecimal amount) {
    }

    /** Sum of the line item amounts. */
    public BigDecimal totalClaimed() {
        return lineItems.stream().map(LineItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

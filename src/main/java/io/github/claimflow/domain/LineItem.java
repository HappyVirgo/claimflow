package io.github.claimflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;

/** A single amount claimed against one coverage. */
@Embeddable
public class LineItem {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoverageType coverage;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    protected LineItem() {
    }

    /** Creates a line item. */
    public LineItem(CoverageType coverage, String description, BigDecimal amount) {
        this.coverage = coverage;
        this.description = description;
        this.amount = amount;
    }

    public CoverageType getCoverage() { return coverage; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
}

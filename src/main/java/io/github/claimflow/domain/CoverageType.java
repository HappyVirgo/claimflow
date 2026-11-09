package io.github.claimflow.domain;

/** Coverage a line item is billed against; each belongs to exactly one line of business. */
public enum CoverageType {
    COLLISION(ClaimType.AUTO),
    COMPREHENSIVE(ClaimType.AUTO),
    AUTO_LIABILITY(ClaimType.AUTO),
    DWELLING(ClaimType.PROPERTY),
    CONTENTS(ClaimType.PROPERTY),
    LOSS_OF_USE(ClaimType.PROPERTY);

    private final ClaimType line;

    CoverageType(ClaimType line) {
        this.line = line;
    }

    /** The line of business this coverage belongs to. */
    public ClaimType line() {
        return line;
    }
}

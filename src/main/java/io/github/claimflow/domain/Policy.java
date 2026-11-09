package io.github.claimflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** An insurance policy. Policies are reference data seeded by Flyway and read-only in this service. */
@Entity
@Table(name = "policy")
public class Policy {

    @Id
    private Long id;

    @Column(name = "policy_number", nullable = false, unique = true)
    private String policyNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_of_business", nullable = false)
    private ClaimType lineOfBusiness;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Column(nullable = false)
    private boolean cancelled;

    @Column(name = "coverage_limit", nullable = false)
    private BigDecimal coverageLimit;

    @Column(nullable = false)
    private BigDecimal deductible;

    /** Comma-separated {@link CoverageType} names. */
    @Column(nullable = false)
    private String coverages;

    protected Policy() {
    }

    /** Creates a policy; used by unit tests (production policies come from Flyway seed data). */
    public Policy(Long id, String policyNumber, ClaimType lineOfBusiness, LocalDate effectiveDate,
                  LocalDate expirationDate, boolean cancelled, BigDecimal coverageLimit, BigDecimal deductible,
                  Set<CoverageType> coverages) {
        this.id = id;
        this.policyNumber = policyNumber;
        this.lineOfBusiness = lineOfBusiness;
        this.effectiveDate = effectiveDate;
        this.expirationDate = expirationDate;
        this.cancelled = cancelled;
        this.coverageLimit = coverageLimit;
        this.deductible = deductible;
        this.coverages = coverages.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    /** True when the policy was in force (not cancelled, within its term) on the given date. */
    public boolean isActiveOn(LocalDate date) {
        return !cancelled && !date.isBefore(effectiveDate) && !date.isAfter(expirationDate);
    }

    /** Parsed set of coverages the policy includes. */
    public Set<CoverageType> coverageSet() {
        Set<CoverageType> set = EnumSet.noneOf(CoverageType.class);
        Arrays.stream(coverages.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(CoverageType::valueOf).forEach(set::add);
        return set;
    }

    public Long getId() { return id; }
    public String getPolicyNumber() { return policyNumber; }
    public ClaimType getLineOfBusiness() { return lineOfBusiness; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public boolean isCancelled() { return cancelled; }
    public BigDecimal getCoverageLimit() { return coverageLimit; }
    public BigDecimal getDeductible() { return deductible; }
}

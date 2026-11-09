package io.github.claimflow.api;

import io.github.claimflow.workflow.ClaimAction;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * An adjuster action on a claim.
 *
 * @param expectedVersion the claim version the adjuster saw; a mismatch yields 409 Conflict
 * @param amount          approved amount (APPROVE only)
 * @param reason          required for DENY and REQUEST_INFO
 */
public record ActionRequest(
        @NotNull ClaimAction action,
        @NotNull Long expectedVersion,
        @NotBlank @Size(max = 100) String actor,
        @Size(max = 1000) String reason,
        @Digits(integer = 12, fraction = 2) BigDecimal amount) {
}

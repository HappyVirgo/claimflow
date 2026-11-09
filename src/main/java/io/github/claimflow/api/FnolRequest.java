package io.github.claimflow.api;

import io.github.claimflow.domain.ClaimType;
import io.github.claimflow.domain.CoverageType;
import io.github.claimflow.domain.DocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** First notice of loss: everything a claimant submits when reporting a claim. */
public record FnolRequest(
        @NotBlank @Size(max = 32) String policyNumber,
        @NotNull @Valid ClaimantInfo claimant,
        @NotNull ClaimType type,
        @NotNull @PastOrPresent LocalDate lossDate,
        @NotBlank @Size(max = 4000) String description,
        @NotEmpty @Size(max = 50) List<@Valid @NotNull LineItemInput> lineItems,
        @Size(max = 50) List<@Valid @NotNull DocumentInput> documents) {

    /** Claimant contact details. */
    public record ClaimantInfo(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 32) String phone) {
    }

    /** One claimed amount. */
    public record LineItemInput(
            @NotNull CoverageType coverage,
            @NotBlank @Size(max = 500) String description,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }

    /** Supporting document metadata. */
    public record DocumentInput(
            @NotNull DocumentType type,
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = 100) String contentType,
            @PositiveOrZero long sizeBytes) {
    }

    /** Documents, or an empty list when none were supplied. */
    public List<DocumentInput> documentsOrEmpty() {
        return documents == null ? List.of() : documents;
    }
}

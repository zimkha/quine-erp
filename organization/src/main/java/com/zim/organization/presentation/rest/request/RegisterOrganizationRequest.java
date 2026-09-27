package com.zim.organization.presentation.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mirrors the format constraints of the domain value objects so that
 * malformed input is rejected with a 400 before reaching the domain.
 * Domain-level rules (e.g. supported currencies) are still enforced by the
 * value objects themselves.
 */
public record RegisterOrganizationRequest(

    @NotBlank
    @Size(min = 2, max = 120)
    String organizationName,

    @NotBlank
    @Size(min = 2, max = 160)
    String legalName,

    @NotBlank
    @Size(min = 3, max = 3)
    String currencyCode,

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,19}$")
    String headquartersCode,

    @NotBlank
    @Size(min = 2, max = 120)
    String headquartersName
) {
}

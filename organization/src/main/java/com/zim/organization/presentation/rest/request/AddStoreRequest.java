package com.zim.organization.presentation.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mirrors the format constraints of the store value objects so that
 * malformed input is rejected with a 400 before reaching the domain. The
 * tenant is never part of the request: it comes from the
 * {@code CurrentTenantProvider}.
 */
public record AddStoreRequest(

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,19}$")
    String storeCode,

    @NotBlank
    @Size(min = 2, max = 120)
    String storeName
) {
}

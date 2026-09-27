package com.zim.organization.presentation.rest.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * The store that becomes the headquarters. A value that is not a UUID is
 * rejected by the JSON reader with a 400. The tenant is never part of the
 * request: it comes from the {@code CurrentTenantProvider}.
 */
public record ChangeHeadquartersRequest(

    @NotNull
    UUID storeId
) {
}

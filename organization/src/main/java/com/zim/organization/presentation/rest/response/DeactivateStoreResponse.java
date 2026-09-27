package com.zim.organization.presentation.rest.response;

import java.time.Instant;
import java.util.UUID;

public record DeactivateStoreResponse(
    UUID organizationId,
    UUID storeId,
    boolean active,
    Instant deactivatedAt
) {
}

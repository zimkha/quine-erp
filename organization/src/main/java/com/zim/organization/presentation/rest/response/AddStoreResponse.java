package com.zim.organization.presentation.rest.response;

import java.time.Instant;
import java.util.UUID;

public record AddStoreResponse(
    UUID organizationId,
    UUID storeId,
    String storeCode,
    String storeName,
    boolean headquarters,
    boolean active,
    Instant addedAt
) {
}

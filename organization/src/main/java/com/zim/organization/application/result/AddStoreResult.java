package com.zim.organization.application.result;

import java.time.Instant;
import java.util.UUID;

public record AddStoreResult(
        UUID organizationId,
        UUID storeId,
        String storeCode,
        String storeName,
        boolean headquarters,
        boolean active,
        Instant addedAt
) {
}
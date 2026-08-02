package com.zim.organization.application.result;

import java.time.Instant;
import java.util.UUID;

public record DeactivateStoreResult(
        UUID organizationId,
        UUID storeId,
        boolean active,
        Instant deactivatedAt
) {
}
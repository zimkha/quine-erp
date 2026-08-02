package com.zim.organization.application.result;

import java.time.Instant;
import java.util.UUID;

public record ActivateOrganizationResult(
        UUID organizationId,
        UUID tenantId,
        String status,
        Instant activatedAt
) {
}

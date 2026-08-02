package com.zim.organization.application.result;

import java.time.Instant;
import java.util.UUID;

public record RegisterOrganizationResult(
        UUID organizationId,
        UUID tenantId,
        String organizationName,
        String status,
        UUID headquartersId,
        Instant createdAt
) {
}

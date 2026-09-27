package com.zim.organization.presentation.rest.response;

import java.time.Instant;
import java.util.UUID;

public record RegisterOrganizationResponse(
        UUID organizationId,
        UUID tenantId,
        String organizationName,
        String status,
        UUID headquartersId,
        Instant createdAt
) {
}

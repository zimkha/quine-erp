package com.zim.organization.presentation.rest.response;

import java.time.Instant;
import java.util.UUID;

public record ActivateOrganizationResponse(
    UUID organizationId,
    String status,
    Instant activatedAt
) {
}

package com.zim.organization.presentation.rest.response;

import java.time.Instant;
import java.util.UUID;

public record ChangeHeadquartersResponse(
    UUID organizationId,
    UUID headquartersId,
    Instant changedAt
) {
}

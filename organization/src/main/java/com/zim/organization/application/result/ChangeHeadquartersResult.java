package com.zim.organization.application.result;

import java.time.Instant;
import java.util.UUID;

public record ChangeHeadquartersResult(
        UUID organizationId,
        UUID headquartersId,
        Instant changedAt
) {
}
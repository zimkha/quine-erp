package com.zim.identity.application.result;

import java.time.Instant;
import java.util.UUID;

/** Never carries the password or its hash. */
public record CreateOwnerResult(
    UUID userId,
    UUID tenantId,
    String email,
    Instant createdAt
) {
}

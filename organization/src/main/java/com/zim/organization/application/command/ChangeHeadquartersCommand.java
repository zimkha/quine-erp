package com.zim.organization.application.command;

import com.zim.shared.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

public record ChangeHeadquartersCommand(
    TenantId tenantId,
    UUID organizationId,
    UUID newHeadquartersId
) {

  public ChangeHeadquartersCommand {
    Objects.requireNonNull(
        tenantId,
        "Tenant id cannot be null"
    );
    Objects.requireNonNull(
        organizationId,
        "Organization id cannot be null"
    );
    Objects.requireNonNull(
        newHeadquartersId,
        "New headquarters id cannot be null"
    );
  }
}

package com.zim.organization.application.command;

import com.zim.shared.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

public record DeactivateStoreCommand(
    TenantId tenantId,
    UUID organizationId,
    UUID storeId
) {

  public DeactivateStoreCommand {
    Objects.requireNonNull(
        tenantId,
        "Tenant id cannot be null"
    );
    Objects.requireNonNull(
        organizationId,
        "Organization id cannot be null"
    );
    Objects.requireNonNull(
        storeId,
        "Store id cannot be null"
    );
  }
}

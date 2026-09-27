package com.zim.organization.application.command;

import com.zim.shared.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

public record AddStoreCommand(
    TenantId tenantId,
    UUID organizationId,
    String storeCode,
    String storeName
) {

  public AddStoreCommand {
    Objects.requireNonNull(
        tenantId,
        "Tenant id cannot be null"
    );
    Objects.requireNonNull(
        organizationId,
        "Organization id cannot be null"
    );
    Objects.requireNonNull(
        storeCode,
        "Store code cannot be null"
    );
    Objects.requireNonNull(
        storeName,
        "Store name cannot be null"
    );
  }
}

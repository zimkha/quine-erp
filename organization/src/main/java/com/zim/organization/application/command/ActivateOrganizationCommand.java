package com.zim.organization.application.command;

import com.zim.shared.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

public record ActivateOrganizationCommand(
    TenantId tenantId,
    UUID organizationId
) {

  public ActivateOrganizationCommand {
    Objects.requireNonNull(
        tenantId,
        "Tenant id cannot be null"
    );
    Objects.requireNonNull(
        organizationId,
        "Organization id cannot be null"
    );
  }
}

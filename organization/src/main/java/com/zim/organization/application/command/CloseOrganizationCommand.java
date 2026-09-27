package com.zim.organization.application.command;

import com.zim.shared.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

public record CloseOrganizationCommand(
    TenantId tenantId,
    UUID organizationId
) {

  public CloseOrganizationCommand {
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

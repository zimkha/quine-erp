package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.shared.domain.BusinessRule;

import java.util.Objects;

public record OrganizationMustBeSuspendedToBeReinstatedRule(
    OrganizationStatus currentStatus
) implements BusinessRule {

  public OrganizationMustBeSuspendedToBeReinstatedRule {
    Objects.requireNonNull(
        currentStatus,
        "Current status cannot be null"
    );
  }

  @Override
  public boolean isBroken() {
    return currentStatus != OrganizationStatus.SUSPENDED;
  }

  @Override
  public String code() {
    return switch (currentStatus) {
      case ACTIVE -> "ORGANIZATION_ALREADY_ACTIVE";
      default -> "ORGANIZATION_CANNOT_BE_REINSTATED";
    };
  }

  @Override
  public String message() {
    return switch (currentStatus) {
      case ACTIVE -> "Organization is already active";
      default -> "Organization cannot be reinstated from status '%s'"
          .formatted(currentStatus);
    };
  }
}

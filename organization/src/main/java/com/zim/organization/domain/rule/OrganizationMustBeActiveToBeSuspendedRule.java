package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.shared.domain.BusinessRule;

import java.util.Objects;

public record OrganizationMustBeActiveToBeSuspendedRule(
    OrganizationStatus currentStatus
) implements BusinessRule {

  public OrganizationMustBeActiveToBeSuspendedRule {
    Objects.requireNonNull(
        currentStatus,
        "Current status cannot be null"
    );
  }

  @Override
  public boolean isBroken() {
    return currentStatus != OrganizationStatus.ACTIVE;
  }

  @Override
  public String code() {
    return "ORGANIZATION_CANNOT_BE_SUSPENDED";
  }

  @Override
  public String message() {
    return "Organization cannot be suspended from status '%s'"
        .formatted(currentStatus);
  }
}

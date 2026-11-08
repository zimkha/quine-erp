package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.shared.domain.BusinessRule;

import java.util.Objects;

/**
 * Shared guard evaluated first by every tenant action (add store, change
 * headquarters, deactivate store, close). It is broken while the platform
 * has not validated the organization or has suspended it, and answers a
 * dedicated, status-specific code. {@code CLOSED} is not caught here: the
 * action-specific rules keep their generic codes for it.
 *
 * <p>The codes of this family ({@code ORGANIZATION_<STATE>}) are refusals
 * tied to the current state, never "target state reached"; they must not
 * appear in the "already" code list of {@code endpoints-T5.md}.
 */
public record OrganizationMustAcceptTenantActionsRule(
    OrganizationStatus currentStatus
) implements BusinessRule {

  public OrganizationMustAcceptTenantActionsRule {
    Objects.requireNonNull(
        currentStatus,
        "Current status cannot be null"
    );
  }

  @Override
  public boolean isBroken() {
    return switch (currentStatus) {
      case PENDING_ACTIVATION, SUSPENDED -> true;
      default -> false;
    };
  }

  @Override
  public String code() {
    return switch (currentStatus) {
      case PENDING_ACTIVATION -> "ORGANIZATION_PENDING_VALIDATION";
      case SUSPENDED -> "ORGANIZATION_SUSPENDED";
      default -> "ORGANIZATION_ACCEPTS_TENANT_ACTIONS";
    };
  }

  @Override
  public String message() {
    return switch (currentStatus) {
      case PENDING_ACTIVATION ->
          "Organization is waiting for validation by the platform";
      case SUSPENDED -> "Organization is suspended by the platform";
      default -> "Organization accepts tenant actions from status '%s'"
          .formatted(currentStatus);
    };
  }
}

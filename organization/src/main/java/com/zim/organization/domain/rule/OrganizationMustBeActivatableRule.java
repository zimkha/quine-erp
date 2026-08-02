package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.shared.domain.BusinessRule;

import java.util.Objects;
import java.util.Set;

public record OrganizationMustBeActivatableRule(
        OrganizationStatus currentStatus
) implements BusinessRule {

    private static final Set<OrganizationStatus> ALLOWED_STATUSES =
            Set.of(
                    OrganizationStatus.PENDING_ACTIVATION,
                    OrganizationStatus.SUSPENDED
            );

    public OrganizationMustBeActivatableRule {
        Objects.requireNonNull(
                currentStatus,
                "Current status cannot be null"
        );
    }

    @Override
    public boolean isBroken() {
        return !ALLOWED_STATUSES.contains(currentStatus);
    }

    @Override
    public String code() {
        return "ORGANIZATION_CANNOT_BE_ACTIVATED";
    }

    @Override
    public String message() {
        return "Organization cannot be activated from status '%s'"
                .formatted(currentStatus);
    }
}

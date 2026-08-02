package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.shared.domain.BusinessRule;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record OrganizationMustBeClosableRule(
        OrganizationStatus currentStatus
) implements BusinessRule {

    private static final Set<OrganizationStatus> CLOSABLE_STATUSES =
            EnumSet.of(
                    OrganizationStatus.ACTIVE,
                    OrganizationStatus.SUSPENDED
            );

    public OrganizationMustBeClosableRule {
        Objects.requireNonNull(
                currentStatus,
                "Current status cannot be null"
        );
    }

    @Override
    public boolean isBroken() {
        return !CLOSABLE_STATUSES.contains(currentStatus);
    }

    @Override
    public String code() {
        return switch (currentStatus) {
            case CLOSED -> "ORGANIZATION_ALREADY_CLOSED";
            default -> "ORGANIZATION_CANNOT_BE_CLOSED";
        };
    }

    @Override
    public String message() {
        return switch (currentStatus) {
            case CLOSED -> "Organization is already closed";
            default -> "Organization cannot be closed from status '%s'"
                    .formatted(currentStatus);
        };
    }
}
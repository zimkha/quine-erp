package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.shared.domain.BusinessRule;

import java.util.Objects;

public record OrganizationCannotBeClosedTwiceRule(
        OrganizationStatus currentStatus
) implements BusinessRule {

    public OrganizationCannotBeClosedTwiceRule {
        Objects.requireNonNull(
                currentStatus,
                "Current status cannot be null"
        );
    }

    @Override
    public boolean isBroken() {
        return currentStatus == OrganizationStatus.CLOSED;
    }

    @Override
    public String code() {
        return "ORGANIZATION_ALREADY_CLOSED";
    }

    @Override
    public String message() {
        return "Organization is already closed";
    }
}

package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.shared.domain.BusinessRule;

import java.util.Objects;

public record OrganizationMustBeActiveToChangeHeadquartersRule(
        OrganizationStatus currentStatus
) implements BusinessRule {

    public OrganizationMustBeActiveToChangeHeadquartersRule {
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
        return "ORGANIZATION_MUST_BE_ACTIVE_TO_CHANGE_HEADQUARTERS";
    }

    @Override
    public String message() {
        return "Headquarters cannot be changed when organization status is '%s'"
                .formatted(currentStatus);
    }
}
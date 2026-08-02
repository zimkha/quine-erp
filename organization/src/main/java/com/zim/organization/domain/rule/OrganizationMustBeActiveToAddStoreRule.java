package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.shared.domain.BusinessRule;

import java.util.Objects;

public record OrganizationMustBeActiveToAddStoreRule(
        OrganizationStatus currentStatus
) implements BusinessRule {

    public OrganizationMustBeActiveToAddStoreRule {
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
        return "ORGANIZATION_MUST_BE_ACTIVE_TO_ADD_STORE";
    }

    @Override
    public String message() {
        return "A store cannot be added when organization status is '%s'"
                .formatted(currentStatus);
    }
}
package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.Store;
import com.zim.shared.domain.BusinessRule;

import java.util.Objects;

public record StoreMustBeActiveToBeDeactivatedRule(
        Store store
) implements BusinessRule {

    public StoreMustBeActiveToBeDeactivatedRule {
        Objects.requireNonNull(store, "Store cannot be null");
    }

    @Override
    public boolean isBroken() {
        return !store.isActive();
    }

    @Override
    public String code() {
        return "STORE_ALREADY_INACTIVE";
    }

    @Override
    public String message() {
        return "Store '%s' is already inactive"
                .formatted(store.id());
    }
}
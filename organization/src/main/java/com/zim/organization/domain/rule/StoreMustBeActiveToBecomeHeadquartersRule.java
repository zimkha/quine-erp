package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.Store;
import com.zim.shared.domain.BusinessRule;

import java.util.Objects;

public record StoreMustBeActiveToBecomeHeadquartersRule(
        Store store
) implements BusinessRule {

    public StoreMustBeActiveToBecomeHeadquartersRule {
        Objects.requireNonNull(store, "Store cannot be null");
    }

    @Override
    public boolean isBroken() {
        return !store.isActive();
    }

    @Override
    public String code() {
        return "INACTIVE_STORE_CANNOT_BECOME_HEADQUARTERS";
    }

    @Override
    public String message() {
        return "Inactive store '%s' cannot become headquarters"
                .formatted(store.id());
    }
}
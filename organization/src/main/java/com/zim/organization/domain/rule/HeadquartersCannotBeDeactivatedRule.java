package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.Store;
import com.zim.shared.domain.BusinessRule;

import java.util.Objects;

public record HeadquartersCannotBeDeactivatedRule(
        Store store
) implements BusinessRule {

    public HeadquartersCannotBeDeactivatedRule {
        Objects.requireNonNull(store, "Store cannot be null");
    }

    @Override
    public boolean isBroken() {
        return store.isHeadquarters();
    }

    @Override
    public String code() {
        return "HEADQUARTERS_CANNOT_BE_DEACTIVATED";
    }

    @Override
    public String message() {
        return "Headquarters store '%s' cannot be deactivated"
                .formatted(store.id());
    }
}
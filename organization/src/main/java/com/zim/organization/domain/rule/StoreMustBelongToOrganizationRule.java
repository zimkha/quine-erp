package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.Store;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.shared.domain.BusinessRule;

import java.util.List;
import java.util.Objects;

public record StoreMustBelongToOrganizationRule(
        List<Store> stores,
        StoreId storeId
) implements BusinessRule {

    public StoreMustBelongToOrganizationRule {
        stores = List.copyOf(
                Objects.requireNonNull(stores, "Stores cannot be null")
        );

        Objects.requireNonNull(storeId, "Store id cannot be null");
    }

    @Override
    public boolean isBroken() {
        return stores.stream()
                .noneMatch(store -> store.id().equals(storeId));
    }

    @Override
    public String code() {
        return "STORE_DOES_NOT_BELONG_TO_ORGANIZATION";
    }

    @Override
    public String message() {
        return "Store '%s' does not belong to the organization"
                .formatted(storeId);
    }
}
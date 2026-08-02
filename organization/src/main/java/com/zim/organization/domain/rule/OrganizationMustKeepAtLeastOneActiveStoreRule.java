package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.Store;
import com.zim.shared.domain.BusinessRule;

import java.util.List;
import java.util.Objects;

public record OrganizationMustKeepAtLeastOneActiveStoreRule(
        List<Store> stores,
        Store storeToDeactivate
) implements BusinessRule {

    public OrganizationMustKeepAtLeastOneActiveStoreRule {
        stores = List.copyOf(
                Objects.requireNonNull(stores, "Stores cannot be null")
        );

        Objects.requireNonNull(
                storeToDeactivate,
                "Store to deactivate cannot be null"
        );
    }

    @Override
    public boolean isBroken() {
        long remainingActiveStores = stores.stream()
                .filter(Store::isActive)
                .filter(store ->
                        !store.id().equals(storeToDeactivate.id()))
                .count();

        return remainingActiveStores == 0;
    }

    @Override
    public String code() {
        return "ORGANIZATION_MUST_KEEP_ONE_ACTIVE_STORE";
    }

    @Override
    public String message() {
        return "Organization must keep at least one active store";
    }
}
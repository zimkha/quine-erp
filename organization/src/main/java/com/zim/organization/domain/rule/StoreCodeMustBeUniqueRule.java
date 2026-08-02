package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.Store;
import com.zim.organization.domain.valueobject.StoreCode;
import com.zim.shared.domain.BusinessRule;

import java.util.List;
import java.util.Objects;

public record StoreCodeMustBeUniqueRule(
        List<Store> stores,
        StoreCode candidateCode
) implements BusinessRule {

    public StoreCodeMustBeUniqueRule {
        stores = List.copyOf(
                Objects.requireNonNull(stores, "Stores cannot be null")
        );

        Objects.requireNonNull(
                candidateCode,
                "Candidate store code cannot be null"
        );
    }

    @Override
    public boolean isBroken() {
        return stores.stream()
                .map(Store::code)
                .anyMatch(candidateCode::equals);
    }

    @Override
    public String code() {
        return "STORE_CODE_ALREADY_EXISTS";
    }

    @Override
    public String message() {
        return "A store with code '%s' already exists"
                .formatted(candidateCode.value());
    }
}

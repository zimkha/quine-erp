package com.zim.organization.domain.rule;

import com.zim.organization.domain.valueobject.StoreId;
import com.zim.shared.domain.BusinessRule;

import java.util.Objects;

public record NewHeadquartersMustBeDifferentRule(
        StoreId currentHeadquartersId,
        StoreId newHeadquartersId
) implements BusinessRule {

    public NewHeadquartersMustBeDifferentRule {
        Objects.requireNonNull(
                currentHeadquartersId,
                "Current headquarters id cannot be null"
        );
        Objects.requireNonNull(
                newHeadquartersId,
                "New headquarters id cannot be null"
        );
    }

    @Override
    public boolean isBroken() {
        return currentHeadquartersId.equals(newHeadquartersId);
    }

    @Override
    public String code() {
        return "STORE_IS_ALREADY_HEADQUARTERS";
    }

    @Override
    public String message() {
        return "Store '%s' is already the headquarters"
                .formatted(newHeadquartersId);
    }
}
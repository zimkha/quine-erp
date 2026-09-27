package com.zim.organization.domain.rule;

import com.zim.organization.domain.model.Store;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.shared.domain.BusinessRule;

import java.util.List;
import java.util.Objects;

public record StoreIdMustBeUniqueRule(
    List<Store> stores,
    StoreId candidateId
) implements BusinessRule {

  public StoreIdMustBeUniqueRule {
    stores = List.copyOf(
        Objects.requireNonNull(stores, "Stores cannot be null")
    );

    Objects.requireNonNull(
        candidateId,
        "Candidate store id cannot be null"
    );
  }

  @Override
  public boolean isBroken() {
    return stores.stream()
        .map(Store::id)
        .anyMatch(candidateId::equals);
  }

  @Override
  public String code() {
    return "STORE_ID_ALREADY_EXISTS";
  }

  @Override
  public String message() {
    return "A store with id '%s' already exists"
        .formatted(candidateId.value());
  }
}

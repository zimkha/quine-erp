package com.zim.organization.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record StoreId(UUID value) {

    public StoreId{
        Objects.requireNonNull(value, "Store id cannot be null");
    }

    public static StoreId generate(){
        return new StoreId(UUID.randomUUID());
    }
    public  static StoreId from(String value){
        Objects.requireNonNull(value, "Store id cannot be null");
        return new StoreId(UUID.fromString(value));
    }

    @Override
    public String toString(){
        return value.toString();
    }
}

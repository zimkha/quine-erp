package com.zim.organization.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record OrganizationId(UUID value) {

    public OrganizationId {
        Objects.requireNonNull(value, "Organization id cannot be null");
    }
    public static OrganizationId generate(){
        return  new OrganizationId(UUID.randomUUID());
    }
    public static OrganizationId from(String value) {
        Objects.requireNonNull(value, "Organization id cannot be null");
        return new OrganizationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

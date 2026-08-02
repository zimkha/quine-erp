package com.zim.organization.application.exception;

import java.util.Objects;
import java.util.UUID;

public final class OrganizationNotFoundException
        extends RuntimeException {

    public static final String CODE = "ORGANIZATION_NOT_FOUND";

    private final UUID organizationId;

    public OrganizationNotFoundException(UUID organizationId) {
        super(
                "Organization '%s' was not found"
                        .formatted(
                                Objects.requireNonNull(
                                        organizationId,
                                        "Organization id cannot be null"
                                )
                        )
        );

        this.organizationId = organizationId;
    }

    public String code() {
        return CODE;
    }

    public UUID organizationId() {
        return organizationId;
    }
}

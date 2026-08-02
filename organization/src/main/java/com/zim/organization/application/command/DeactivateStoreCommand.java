package com.zim.organization.application.command;

import java.util.Objects;
import java.util.UUID;

public record DeactivateStoreCommand(
        UUID organizationId,
        UUID storeId
) {

    public DeactivateStoreCommand {
        Objects.requireNonNull(
                organizationId,
                "Organization id cannot be null"
        );
        Objects.requireNonNull(
                storeId,
                "Store id cannot be null"
        );
    }
}
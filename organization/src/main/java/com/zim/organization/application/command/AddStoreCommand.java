package com.zim.organization.application.command;

import java.util.Objects;
import java.util.UUID;

public record AddStoreCommand(
        UUID organizationId,
        String storeCode,
        String storeName
) {

    public AddStoreCommand {
        Objects.requireNonNull(
                organizationId,
                "Organization id cannot be null"
        );
        Objects.requireNonNull(
                storeCode,
                "Store code cannot be null"
        );
        Objects.requireNonNull(
                storeName,
                "Store name cannot be null"
        );
    }
}

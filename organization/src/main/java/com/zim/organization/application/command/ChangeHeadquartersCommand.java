package com.zim.organization.application.command;

import java.util.Objects;
import java.util.UUID;

public record ChangeHeadquartersCommand(
        UUID organizationId,
        UUID newHeadquartersId
) {

    public ChangeHeadquartersCommand {
        Objects.requireNonNull(
                organizationId,
                "Organization id cannot be null"
        );
        Objects.requireNonNull(
                newHeadquartersId,
                "New headquarters id cannot be null"
        );
    }
}
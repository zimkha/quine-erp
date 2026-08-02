package com.zim.organization.application.command;

import java.util.Objects;
import java.util.UUID;

public record CloseOrganizationCommand(
        UUID organizationId
) {

    public CloseOrganizationCommand {
        Objects.requireNonNull(
                organizationId,
                "Organization id cannot be null"
        );
    }
}
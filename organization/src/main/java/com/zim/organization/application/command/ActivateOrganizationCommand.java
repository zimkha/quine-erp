package com.zim.organization.application.command;

import java.util.Objects;
import java.util.UUID;

public record ActivateOrganizationCommand(
        UUID organizationId
) {

    public ActivateOrganizationCommand {
        Objects.requireNonNull(
                organizationId,
                "Organization id cannot be null"
        );
    }
}

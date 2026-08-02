package com.zim.organization.application.command;

import java.util.Objects;

public record RegisterOrganizationCommand(
        String organizationName,
        String legalName,
        String currencyCode,
        String headquartersCode,
        String headquartersName
) {
    public RegisterOrganizationCommand {
        Objects.requireNonNull(
                organizationName,
                "Organization name cannot be null"
        );
        Objects.requireNonNull(
                legalName,
                "Legal name cannot be null"
        );
        Objects.requireNonNull(
                currencyCode,
                "Currency code cannot be null"
        );
        Objects.requireNonNull(
                headquartersCode,
                "Headquarters code cannot be null"
        );
        Objects.requireNonNull(
                headquartersName,
                "Headquarters name cannot be null"
        );
    }
}

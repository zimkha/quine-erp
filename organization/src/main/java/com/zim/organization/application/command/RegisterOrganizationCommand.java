package com.zim.organization.application.command;

import java.util.Objects;

public record RegisterOrganizationCommand(
        String organizationName,
        String legalName,
        String currencyCode,
        String headquartersCode,
        String headquartersName,
        String ownerEmail,
        String ownerPassword
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
        Objects.requireNonNull(
                ownerEmail,
                "Owner e-mail cannot be null"
        );
        Objects.requireNonNull(
                ownerPassword,
                "Owner password cannot be null"
        );
    }

    /** The password is a raw string here, so it is masked. */
    @Override
    public String toString() {
        return "RegisterOrganizationCommand[organizationName="
                + organizationName + ", legalName=" + legalName
                + ", currencyCode=" + currencyCode
                + ", headquartersCode=" + headquartersCode
                + ", headquartersName=" + headquartersName
                + ", ownerEmail=" + ownerEmail
                + ", ownerPassword=[PROTECTED]]";
    }
}

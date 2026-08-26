package com.zim.organization.presentation.rest.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterOrganizationRequest(

        @NotBlank
        @Size(max = 120)
        String organizationName,

        @NotBlank
        @Size(max = 160)
        String legalName,

        @NotBlank
        @Size(min = 3, max = 3)
        String currencyCode,

        @NotBlank
        @Size(max = 20)
        String headquartersCode,

        @NotBlank
        @Size(max = 120)
        String headquartersName
) {
}
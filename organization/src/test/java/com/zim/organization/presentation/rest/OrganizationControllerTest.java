package com.zim.organization.presentation.rest;

import com.zim.organization.application.exception.OrganizationAlreadyExistsException;
import com.zim.organization.application.handler.RegisterOrganizationHandler;
import com.zim.organization.application.result.RegisterOrganizationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrganizationController.class)
class OrganizationControllerTest {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString(
                    "5c80d578-83f7-4b44-b5f7-598530067a09"
            );

    private static final UUID TENANT_ID =
            UUID.fromString(
                    "2d3a7d37-ef2c-4794-b248-b08acf42eb38"
            );

    private static final UUID HEADQUARTERS_ID =
            UUID.fromString(
                    "4ee0d038-4617-435c-b7c8-48697d4cf909"
            );

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-25T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterOrganizationHandler registerOrganizationHandler;

    @Test
    void shouldRegisterOrganization() throws Exception {
        // Given
        RegisterOrganizationResult result =
                new RegisterOrganizationResult(
                        ORGANIZATION_ID,
                        TENANT_ID,
                        "Quincaillerie Thiès",
                        "PENDING_ACTIVATION",
                        HEADQUARTERS_ID,
                        CREATED_AT
                );

        when(registerOrganizationHandler.handle(any()))
                .thenReturn(result);

        String request = """
                {
                  "organizationName": "Quincaillerie Thiès",
                  "legalName": "Quincaillerie Thiès SARL",
                  "currencyCode": "XOF",
                  "headquartersCode": "THIES-01",
                  "headquartersName": "Magasin principal"
                }
                """;

        // When / Then
        mockMvc.perform(
                        post("/api/organizations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.organizationId")
                                .value(ORGANIZATION_ID.toString())
                )
                .andExpect(
                        jsonPath("$.tenantId")
                                .value(TENANT_ID.toString())
                )
                .andExpect(
                        jsonPath("$.organizationName")
                                .value("Quincaillerie Thiès")
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("PENDING_ACTIVATION")
                )
                .andExpect(
                        jsonPath("$.headquartersId")
                                .value(HEADQUARTERS_ID.toString())
                )
                .andExpect(
                        jsonPath("$.createdAt")
                                .value(CREATED_AT.toString())
                );

        verify(registerOrganizationHandler)
                .handle(any());
    }
    @Test
    void shouldReturnBadRequestWhenRegistrationRequestIsInvalid()
            throws Exception {

        String request = """
            {
              "organizationName": "",
              "legalName": "",
              "currencyCode": "XO",
              "headquartersCode": "",
              "headquartersName": ""
            }
            """;

        mockMvc.perform(
                        post("/api/organizations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest());

        verify(
                registerOrganizationHandler,
                org.mockito.Mockito.never()
        ).handle(any());
    }
    @Test
    void shouldReturnConflictWhenLegalNameAlreadyExists()
            throws Exception {

        when(registerOrganizationHandler.handle(any()))
                .thenThrow(
                        new OrganizationAlreadyExistsException(
                                "Quincaillerie Thiès SARL"
                        )
                );

        String request = """
            {
              "organizationName": "Quincaillerie Thiès",
              "legalName": "Quincaillerie Thiès SARL",
              "currencyCode": "XOF",
              "headquartersCode": "THIES-01",
              "headquartersName": "Magasin principal"
            }
            """;

        mockMvc.perform(
                        post("/api/organizations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.code")
                                .value("ORGANIZATION_ALREADY_EXISTS")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "An organization with legal name "
                                                + "'Quincaillerie Thiès SARL' "
                                                + "already exists"
                                )
                );
    }
}

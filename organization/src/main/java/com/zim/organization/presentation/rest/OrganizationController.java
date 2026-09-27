package com.zim.organization.presentation.rest;

import com.zim.organization.application.command.RegisterOrganizationCommand;
import com.zim.organization.application.handler.RegisterOrganizationHandler;
import com.zim.organization.application.result.RegisterOrganizationResult;
import com.zim.organization.presentation.rest.request.RegisterOrganizationRequest;
import com.zim.organization.presentation.rest.response.RegisterOrganizationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Objects;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final RegisterOrganizationHandler registerOrganizationHandler;

    public OrganizationController(
            RegisterOrganizationHandler registerOrganizationHandler
    ) {
        this.registerOrganizationHandler =
                Objects.requireNonNull(
                        registerOrganizationHandler,
                        "Register organization handler cannot be null"
                );
    }

    @PostMapping
    public ResponseEntity<RegisterOrganizationResponse> register(
            @Valid
            @RequestBody RegisterOrganizationRequest request
    ) {
        RegisterOrganizationCommand command =
                new RegisterOrganizationCommand(
                        request.organizationName(),
                        request.legalName(),
                        request.currencyCode(),
                        request.headquartersCode(),
                        request.headquartersName()
                );

        RegisterOrganizationResult result =
                registerOrganizationHandler.handle(command);

        RegisterOrganizationResponse response =
                new RegisterOrganizationResponse(
                        result.organizationId(),
                        result.tenantId(),
                        result.organizationName(),
                        result.status(),
                        result.headquartersId(),
                        result.createdAt()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
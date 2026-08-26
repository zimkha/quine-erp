package com.zim.organization.presentation.rest.exception;

import com.zim.organization.application.exception.OrganizationAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OrganizationAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleOrganizationAlreadyExists(
            OrganizationAlreadyExistsException exception
    ) {
        ApiErrorResponse response =
                new ApiErrorResponse(
                        "ORGANIZATION_ALREADY_EXISTS",
                        exception.getMessage(),
                        Instant.now()
                );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }
}
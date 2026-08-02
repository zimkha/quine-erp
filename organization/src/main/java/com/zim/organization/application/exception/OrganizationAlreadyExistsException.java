package com.zim.organization.application.exception;

public class OrganizationAlreadyExistsException extends RuntimeException {

    public OrganizationAlreadyExistsException(String legalName) {
        super("An organization with legal name '%s' already exists"
                .formatted(legalName));
    }}

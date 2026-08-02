package com.zim.organization.application.port;

import com.zim.organization.domain.valueobject.OrganizationId;

@FunctionalInterface
public interface OrganizationIdGenerator {

    OrganizationId generate();
}

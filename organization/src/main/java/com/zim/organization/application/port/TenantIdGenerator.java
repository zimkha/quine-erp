package com.zim.organization.application.port;

import com.zim.organization.domain.valueobject.TenantId;

@FunctionalInterface
public interface TenantIdGenerator {

    TenantId generate();
}

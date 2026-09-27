package com.zim.organization.application.port;

import com.zim.shared.domain.TenantId;

@FunctionalInterface
public interface TenantIdGenerator {

    TenantId generate();
}

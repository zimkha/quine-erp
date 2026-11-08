package com.zim.organization.application.port;

import com.zim.shared.domain.TenantId;

/**
 * Creates the owner of a freshly registered tenant. It runs inside the
 * registration transaction: a failure propagates as is and rolls the whole
 * registration back (nothing is created). The password is the raw value from
 * the request; the implementation must hash it and never log it.
 */
@FunctionalInterface
public interface OwnerRegistrar {

  void register(TenantId tenantId, String ownerEmail, String ownerPassword);
}

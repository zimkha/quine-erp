package com.zim.organization.application.port;

import com.zim.shared.domain.TenantId;

/**
 * Creates the first user (the owner) of a freshly registered tenant.
 *
 * <p>Called by the registration handler inside its own transaction. If it
 * throws, the registration fails and nothing is saved. The adapter lives
 * outside this module, so {@code organization} never knows who the user
 * store is. The password is raw: never log it or put it in a message.
 */
@FunctionalInterface
public interface OwnerRegistrar {

  void register(TenantId tenantId, String email, String password);
}

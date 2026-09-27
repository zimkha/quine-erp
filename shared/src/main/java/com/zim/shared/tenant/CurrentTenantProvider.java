package com.zim.shared.tenant;

import com.zim.shared.domain.TenantId;

/**
 * Tells the application which tenant the current request acts for.
 *
 * <p>Implementations must fail closed: when the tenant cannot be
 * established they throw {@link TenantNotResolvedException} instead of
 * guessing or returning {@code null}. The tenant is always established by
 * the platform, never taken from caller-controlled input.
 */
@FunctionalInterface
public interface CurrentTenantProvider {

  TenantId currentTenant();
}

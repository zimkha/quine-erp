package com.zim.quine.tenant;

import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.CurrentTenantProvider;
import com.zim.shared.tenant.TenantNotResolvedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Default provider until identity (T7) exists: no tenant can be established,
 * so every tenant-scoped endpoint answers 401. Removed in T7.
 */
@Component
@Profile("!smoke")
public class FailClosedTenantProvider implements CurrentTenantProvider {

  @Override
  public TenantId currentTenant() {
    throw new TenantNotResolvedException();
  }
}

package com.zim.quine.smoke;

import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.CurrentTenantProvider;
import com.zim.shared.tenant.TenantNotResolvedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Local smoke runs only: until identity (T7) exists, the tenant comes from
 * the X-Smoke-Tenant header. Never active outside the "smoke" profile.
 */
@Component
@Profile("smoke")
class SmokeTenantProvider implements CurrentTenantProvider {

  static final String HEADER = "X-Smoke-Tenant";

  @Override
  public TenantId currentTenant() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes servlet)) {
      throw new TenantNotResolvedException();
    }
    String value = servlet.getRequest().getHeader(HEADER);
    if (value == null || value.isBlank()) {
      throw new TenantNotResolvedException();
    }
    try {
      return new TenantId(UUID.fromString(value.trim()));
    } catch (IllegalArgumentException e) {
      throw new TenantNotResolvedException();
    }
  }
}

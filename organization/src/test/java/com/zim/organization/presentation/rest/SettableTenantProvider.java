package com.zim.organization.presentation.rest;

import com.zim.shared.domain.TenantId;
import com.zim.shared.tenant.CurrentTenantProvider;
import com.zim.shared.tenant.TenantNotResolvedException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Stands in for the identity-backed provider in the end-to-end ITs.
 * Register generates the tenant, so each test sets the tenant it acts for;
 * unset, it fails closed like any real provider. Tests clear it after each
 * run, since the bean lives as long as the cached context.
 */
class SettableTenantProvider implements CurrentTenantProvider {

  private final AtomicReference<TenantId> tenant = new AtomicReference<>();

  void set(TenantId tenantId) {
    tenant.set(tenantId);
  }

  void clear() {
    tenant.set(null);
  }

  @Override
  public TenantId currentTenant() {
    TenantId current = tenant.get();
    if (current == null) {
      throw new TenantNotResolvedException();
    }
    return current;
  }

  @TestConfiguration
  static class ProviderConfiguration {

    @Bean
    SettableTenantProvider currentTenantProvider() {
      return new SettableTenantProvider();
    }
  }
}

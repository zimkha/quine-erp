package com.zim.organization.testing;

import com.zim.organization.application.port.OwnerRegistrar;
import com.zim.shared.domain.TenantId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Explicit stand-in for identity's adapter in tests of the organization
 * module alone (which does not see identity). Production has no default: the
 * real adapter lives in bootstrap. Records each call, and can be told to
 * fail the next one.
 */
public final class TestOwnerRegistrar implements OwnerRegistrar {

  public record Call(TenantId tenantId, String email, String password) {
  }

  private final List<Call> calls = new CopyOnWriteArrayList<>();
  private final AtomicReference<RuntimeException> failure =
      new AtomicReference<>();

  @Override
  public void register(TenantId tenantId, String ownerEmail, String ownerPassword) {
    calls.add(new Call(tenantId, ownerEmail, ownerPassword));
    RuntimeException toThrow = failure.getAndSet(null);
    if (toThrow != null) {
      throw toThrow;
    }
  }

  public void failNextCallWith(RuntimeException exception) {
    failure.set(exception);
  }

  public List<Call> calls() {
    return List.copyOf(calls);
  }

  @TestConfiguration
  public static class Configuration {

    @Bean
    TestOwnerRegistrar ownerRegistrar() {
      return new TestOwnerRegistrar();
    }
  }
}

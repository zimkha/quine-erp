package com.zim.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record TenantId(UUID value) {

  public TenantId {
    Objects.requireNonNull(value, "Tenant id cannot be null");
  }

  public static TenantId generate() {
    return new TenantId(UUID.randomUUID());
  }

  public static TenantId from(String value) {
    Objects.requireNonNull(value, "Tenant id cannot be null");
    return new TenantId(UUID.fromString(value));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}

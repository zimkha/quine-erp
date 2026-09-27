package com.zim.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIdTest {

  @Test
  void shouldRejectNullUuid() {
    assertThatThrownBy(() -> new TenantId(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Tenant id cannot be null");
  }

  @Test
  void shouldRejectNullString() {
    assertThatThrownBy(() -> TenantId.from(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Tenant id cannot be null");
  }

  @Test
  void shouldRejectMalformedString() {
    assertThatThrownBy(() -> TenantId.from("not-a-uuid"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRoundTripValidString() {
    String text = "3f2c1a7e-9b4d-4c8e-a1f0-5d6e7b8c9a01";

    TenantId tenantId = TenantId.from(text);

    assertThat(tenantId.value()).isEqualTo(UUID.fromString(text));
    assertThat(tenantId.toString()).isEqualTo(text);
  }

  @Test
  void shouldGenerateDistinctIds() {
    TenantId first = TenantId.generate();
    TenantId second = TenantId.generate();

    assertThat(first).isNotNull();
    assertThat(first).isNotEqualTo(second);
  }
}

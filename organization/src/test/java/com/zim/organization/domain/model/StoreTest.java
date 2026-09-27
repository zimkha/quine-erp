package com.zim.organization.domain.model;

import com.zim.organization.domain.valueobject.StoreCode;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.organization.domain.valueobject.StoreName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StoreTest {

  private static final Instant CREATED_AT =
      Instant.parse("2026-08-01T10:00:00Z");

  @Test
  void shouldCreateActiveSecondaryStore() {
    Store store = Store.create(
        new StoreId(UUID.randomUUID()),
        new StoreCode("DAKAR-01"),
        new StoreName("Magasin Dakar"),
        CREATED_AT
    );

    assertThat(store.isActive()).isTrue();
    assertThat(store.isHeadquarters()).isFalse();
    assertThat(store.createdAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void shouldCreateActiveHeadquartersStore() {
    Store store = Store.createHeadquarters(
        new StoreId(UUID.randomUUID()),
        new StoreCode("THIES-01"),
        new StoreName("Magasin principal"),
        CREATED_AT
    );

    assertThat(store.isActive()).isTrue();
    assertThat(store.isHeadquarters()).isTrue();
    assertThat(store.createdAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void shouldRestoreInactiveStore() {
    Store store = Store.restore(
        new StoreId(UUID.randomUUID()),
        new StoreCode("DAKAR-01"),
        new StoreName("Magasin Dakar"),
        false,
        false,
        CREATED_AT
    );

    assertThat(store.isActive()).isFalse();
    assertThat(store.isHeadquarters()).isFalse();
    assertThat(store.createdAt()).isEqualTo(CREATED_AT);
  }
}
package com.zim.organization.infrastructure.persistence;

import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.valueobject.StoreCode;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.organization.domain.valueobject.StoreName;
import com.zim.organization.infrastructure.persistence.support.OrganizationIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationOptimisticLockingIT
    extends OrganizationIntegrationTestSupport {

  @Test
  void shouldRejectSaveOfStaleCopyAfterConcurrentClose() {
    // Given two copies of the same organization loaded at the same version
    repositoryAdapter.save(activeOrganization());
    flushAndClear();

    Organization first = reloadOrganization();
    Organization second = reloadOrganization();

    // When the second copy closes the organization
    second.close(UUID.randomUUID(), Instant.parse("2026-08-03T10:00:00Z"));
    repositoryAdapter.save(second);
    flushAndClear();

    // Then saving the stale first copy is rejected
    first.addStore(
        new StoreId(SECONDARY_STORE_UUID),
        new StoreCode("DAKAR-01"),
        new StoreName("Magasin Dakar"),
        UUID.randomUUID(),
        Instant.parse("2026-08-03T11:00:00Z")
    );

    assertThatThrownBy(() -> repositoryAdapter.save(first))
        .isInstanceOf(OptimisticLockingFailureException.class);

    flushAndClear();

    Organization reloaded = reloadOrganization();

    assertThat(reloaded.status()).isEqualTo(OrganizationStatus.CLOSED);
    assertThat(reloaded.stores()).hasSize(1);
  }

  @Test
  void shouldRejectStaleCopyWhenOnlyStoresChanged() {
    // Given two copies of the same organization with two stores
    repositoryAdapter.save(activeOrganizationWithSecondaryStore());
    flushAndClear();

    Organization first = reloadOrganization();
    Organization second = reloadOrganization();

    // When the second copy changes only a store row
    second.deactivateStore(
        new StoreId(SECONDARY_STORE_UUID),
        UUID.randomUUID(),
        Instant.parse("2026-08-03T10:00:00Z")
    );
    repositoryAdapter.save(second);
    flushAndClear();

    // Then the root version was still bumped, and the stale copy is rejected
    first.changeHeadquarters(
        new StoreId(SECONDARY_STORE_UUID),
        UUID.randomUUID(),
        Instant.parse("2026-08-03T11:00:00Z")
    );

    assertThatThrownBy(() -> repositoryAdapter.save(first))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  void shouldIncrementVersionOnEverySave() {
    repositoryAdapter.save(activeOrganizationWithSecondaryStore());
    flushAndClear();

    Organization loaded = reloadOrganization();
    Long initialVersion = loaded.version();

    loaded.deactivateStore(
        new StoreId(SECONDARY_STORE_UUID),
        UUID.randomUUID(),
        Instant.parse("2026-08-03T10:00:00Z")
    );
    repositoryAdapter.save(loaded);
    flushAndClear();

    assertThat(initialVersion).isNotNull();
    assertThat(reloadOrganization().version())
        .isGreaterThan(initialVersion);
  }
}

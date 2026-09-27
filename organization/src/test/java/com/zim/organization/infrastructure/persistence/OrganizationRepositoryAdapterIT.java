package com.zim.organization.infrastructure.persistence;


import com.zim.organization.application.exception.OrganizationAlreadyExistsException;
import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.valueobject.*;
import com.zim.shared.domain.TenantId;
import com.zim.organization.infrastructure.persistence.adapteur.OrganizationRepositoryAdapter;
import com.zim.organization.infrastructure.persistence.entity.OrganizationEntity;
import com.zim.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.zim.organization.infrastructure.persistence.repository.SpringDataOrganizationRepository;
import com.zim.organization.infrastructure.persistence.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationRepositoryAdapterIT
    extends PostgresIntegrationTest {

  @Autowired
  EntityManager entityManager;

  @Autowired
  SpringDataOrganizationRepository springDataRepository;

  OrganizationRepositoryAdapter repositoryAdapter;
  private static final UUID ORGANIZATION_UUID =
      UUID.fromString(
          "5c80d578-83f7-4b44-b5f7-598530067a09"
      );

  private static final UUID TENANT_UUID =
      UUID.fromString(
          "2d3a7d37-ef2c-4794-b248-b08acf42eb38"
      );

  private static final UUID HEADQUARTERS_UUID =
      UUID.fromString(
          "4ee0d038-4617-435c-b7c8-48697d4cf909"
      );

  private static final UUID SECONDARY_STORE_UUID =
      UUID.fromString(
          "86fd6eb4-23f6-4381-842e-e5d57def4a39"
      );

  private static final UUID OTHER_ORGANIZATION_UUID =
      UUID.fromString(
          "9b1f3e5a-7c2d-4e8f-a0b1-c2d3e4f5a6b7"
      );

  private static final UUID OTHER_TENANT_UUID =
      UUID.fromString(
          "3f6a9c2e-1b4d-4e7f-8a0c-5d2e7b9f1a34"
      );

  private static final Instant CREATED_AT =
      Instant.parse("2026-08-01T10:00:00Z");

  @BeforeEach
  void setUp() {
    repositoryAdapter =
        new OrganizationRepositoryAdapter(
            springDataRepository,
            new OrganizationPersistenceMapper(),
            entityManager
        );
  }
  @Test
  void shouldSaveAndReloadOrganization() {
    Organization organization = Organization.register(
        new OrganizationId(ORGANIZATION_UUID),
        new TenantId(TENANT_UUID),
        new OrganizationName("Quincaillerie Thiès"),
        new LegalName("Quincaillerie Thiès SARL"),
        CurrencyCode.xof(),
        new StoreId(HEADQUARTERS_UUID),
        new StoreCode("THIES-01"),
        new StoreName("Magasin principal"),
        UUID.randomUUID(),
        CREATED_AT
    );

    organization.clearDomainEvents();

    repositoryAdapter.save(organization);

    entityManager.flush();
    entityManager.clear();

    Organization reloaded = repositoryAdapter
        .findById(
            new TenantId(TENANT_UUID),
            new OrganizationId(ORGANIZATION_UUID)
        )
        .orElseThrow();

    assertThat(reloaded.id())
        .isEqualTo(organization.id());

    assertThat(reloaded.tenantId())
        .isEqualTo(organization.tenantId());

    assertThat(reloaded.name())
        .isEqualTo(organization.name());

    assertThat(reloaded.legalName())
        .isEqualTo(organization.legalName());

    assertThat(reloaded.currency())
        .isEqualTo(organization.currency());

    assertThat(reloaded.status())
        .isEqualTo(
            OrganizationStatus.PENDING_ACTIVATION
        );

    assertThat(reloaded.createdAt())
        .isEqualTo(CREATED_AT);

    assertThat(reloaded.stores())
        .hasSize(1)
        .singleElement()
        .satisfies(store -> {
          assertThat(store.id().value())
              .isEqualTo(HEADQUARTERS_UUID);

          assertThat(store.code().value())
              .isEqualTo("THIES-01");

          assertThat(store.name().value())
              .isEqualTo("Magasin principal");

          assertThat(store.isHeadquarters())
              .isTrue();

          assertThat(store.isActive())
              .isTrue();

          assertThat(store.createdAt())
              .isEqualTo(CREATED_AT);
        });

    assertThat(reloaded.domainEvents())
        .isEmpty();
  }

  @Test
  void shouldNotLoadOrganizationOwnedByAnotherTenant() {
    // Given
    saveTenantAOrganizationWithSecondaryStore();
    saveTenantBOrganization();

    entityManager.flush();
    entityManager.clear();

    // When
    Optional<Organization> result = repositoryAdapter.findById(
        new TenantId(OTHER_TENANT_UUID),
        new OrganizationId(ORGANIZATION_UUID)
    );

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldLoadOrganizationOwnedByTenantWithItsStores() {
    // Given
    saveTenantAOrganizationWithSecondaryStore();
    saveTenantBOrganization();

    entityManager.flush();
    entityManager.clear();

    // When
    Optional<Organization> result = repositoryAdapter.findById(
        new TenantId(TENANT_UUID),
        new OrganizationId(ORGANIZATION_UUID)
    );

    // Then
    assertThat(result)
        .get()
        .satisfies(found -> {
          assertThat(found.id())
              .isEqualTo(new OrganizationId(ORGANIZATION_UUID));

          assertThat(found.tenantId())
              .isEqualTo(new TenantId(TENANT_UUID));

          assertThat(found.stores())
              .extracting(store -> store.id().value())
              .containsExactlyInAnyOrder(
                  HEADQUARTERS_UUID,
                  SECONDARY_STORE_UUID
              );
        });
  }

  @Test
  void shouldFetchStoresEagerlyWithTenantScopedQuery() {
    // Given
    saveTenantAOrganizationWithSecondaryStore();

    entityManager.flush();
    entityManager.clear();

    // When
    OrganizationEntity entity = springDataRepository
        .findWithStoresByIdAndTenantId(ORGANIZATION_UUID, TENANT_UUID)
        .orElseThrow();

    // Then: the entity graph fetched the stores with the root, so they
    // are loaded without being touched first.
    assertThat(
        entityManager.getEntityManagerFactory()
            .getPersistenceUnitUtil()
            .isLoaded(entity, "stores")
    ).isTrue();

    assertThat(entity.getStores()).hasSize(2);
  }

  /**
   * Simulates two concurrent registrations that both passed the
   * existsByLegalName pre-check: the unique constraint must surface as the
   * application's OrganizationAlreadyExistsException, not a raw DB error.
   */
  @Test
  void shouldTranslateDuplicateLegalNameConstraintViolation() {
    Organization first = Organization.register(
        new OrganizationId(ORGANIZATION_UUID),
        new TenantId(TENANT_UUID),
        new OrganizationName("Quincaillerie Thiès"),
        new LegalName("Quincaillerie Thiès SARL"),
        CurrencyCode.xof(),
        new StoreId(HEADQUARTERS_UUID),
        new StoreCode("THIES-01"),
        new StoreName("Magasin principal"),
        UUID.randomUUID(),
        CREATED_AT
    );
    repositoryAdapter.save(first);
    entityManager.flush();
    entityManager.clear();

    Organization second = Organization.register(
        OrganizationId.generate(),
        TenantId.generate(),
        new OrganizationName("Autre quincaillerie"),
        new LegalName("quincaillerie   thiès sarl"),
        CurrencyCode.xof(),
        StoreId.generate(),
        new StoreCode("THIES-01"),
        new StoreName("Magasin principal"),
        UUID.randomUUID(),
        CREATED_AT
    );

    assertThatThrownBy(() -> repositoryAdapter.save(second))
        .isInstanceOf(OrganizationAlreadyExistsException.class)
        .hasMessageContaining("quincaillerie thiès sarl");
  }

  private void saveTenantAOrganizationWithSecondaryStore() {
    Organization organization = Organization.register(
        new OrganizationId(ORGANIZATION_UUID),
        new TenantId(TENANT_UUID),
        new OrganizationName("Quincaillerie Thiès"),
        new LegalName("Quincaillerie Thiès SARL"),
        CurrencyCode.xof(),
        new StoreId(HEADQUARTERS_UUID),
        new StoreCode("THIES-01"),
        new StoreName("Magasin principal"),
        UUID.randomUUID(),
        CREATED_AT
    );

    organization.activate(
        UUID.randomUUID(),
        Instant.parse("2026-08-01T11:00:00Z")
    );

    organization.addStore(
        new StoreId(SECONDARY_STORE_UUID),
        new StoreCode("DAKAR-01"),
        new StoreName("Magasin Dakar"),
        UUID.randomUUID(),
        Instant.parse("2026-08-01T12:00:00Z")
    );

    organization.clearDomainEvents();

    repositoryAdapter.save(organization);
  }

  private void saveTenantBOrganization() {
    Organization organization = Organization.register(
        new OrganizationId(OTHER_ORGANIZATION_UUID),
        new TenantId(OTHER_TENANT_UUID),
        new OrganizationName("Quincaillerie Dakar"),
        new LegalName("Quincaillerie Dakar SARL"),
        CurrencyCode.xof(),
        StoreId.generate(),
        new StoreCode("DAKAR-01"),
        new StoreName("Magasin principal"),
        UUID.randomUUID(),
        CREATED_AT
    );

    organization.clearDomainEvents();

    repositoryAdapter.save(organization);
  }
}

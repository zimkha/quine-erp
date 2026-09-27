package com.zim.organization.infrastructure.persistence;


import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.valueobject.*;
import com.zim.organization.infrastructure.persistence.adapteur.OrganizationRepositoryAdapter;
import com.zim.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.zim.organization.infrastructure.persistence.repository.SpringDataOrganizationRepository;
import com.zim.organization.infrastructure.persistence.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OrganizationDatabaseConstraintsIT
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
  void shouldRejectDuplicateStoreCodeWithinSameOrganization() {
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

    assertThatThrownBy(() ->
        entityManager.createNativeQuery("""
          INSERT INTO organization.stores (
            id,
            organization_id,
            code,
            name,
            headquarters,
            active,
            created_at
          )
          VALUES (
            CAST(:id AS uuid),
            CAST(:organizationId AS uuid),
            :code,
            :name,
            false,
            true,
            :createdAt
          )
          """)
            .setParameter("id", UUID.randomUUID())
            .setParameter("organizationId", ORGANIZATION_UUID)
            .setParameter("code", "THIES-01")
            .setParameter("name", "Autre magasin")
            .setParameter(
                "createdAt",
                Instant.parse("2026-08-03T10:00:00Z")
            )
            .executeUpdate()
    )
        .isInstanceOf(Exception.class);
  }
  @Test
  void shouldRejectSecondHeadquartersWithinSameOrganization() {
    // Given
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

    // When / Then
    assertThatThrownBy(() ->
        entityManager.createNativeQuery("""
          INSERT INTO organization.stores (
            id,
            organization_id,
            code,
            name,
            headquarters,
            active,
            created_at
          )
          VALUES (
            CAST(:id AS uuid),
            CAST(:organizationId AS uuid),
            :code,
            :name,
            true,
            true,
            :createdAt
          )
          """)
            .setParameter(
                "id",
                UUID.randomUUID()
            )
            .setParameter(
                "organizationId",
                ORGANIZATION_UUID
            )
            .setParameter(
                "code",
                "DAKAR-01"
            )
            .setParameter(
                "name",
                "Magasin Dakar"
            )
            .setParameter(
                "createdAt",
                Instant.parse(
                    "2026-08-03T10:00:00Z"
                )
            )
            .executeUpdate()
    )
        .isInstanceOf(Exception.class)
        .hasMessageContaining(
            "uk_stores_one_headquarters_per_organization"
        );
  }
  @Test
  void shouldRejectDuplicateNormalizedLegalName() {
    // Given
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

    UUID secondOrganizationId = UUID.randomUUID();
    UUID secondTenantId = UUID.randomUUID();

    assertThatThrownBy(() ->
        entityManager.createNativeQuery("""
          INSERT INTO organization.organizations (
            id,
            tenant_id,
            name,
            legal_name,
            normalized_legal_name,
            currency,
            status,
            created_at
          )
          VALUES (
            CAST(:id AS uuid),
            CAST(:tenantId AS uuid),
            :name,
            :legalName,
            :normalizedLegalName,
            :currency,
            :status,
            :createdAt
          )
          """)
            .setParameter(
                "id",
                secondOrganizationId
            )
            .setParameter(
                "tenantId",
                secondTenantId
            )
            .setParameter(
                "name",
                "Autre quincaillerie"
            )
            .setParameter(
                "legalName",
                "quincaillerie thiès sarl"
            )
            .setParameter(
                "normalizedLegalName",
                "QUINCAILLERIE THIÈS SARL"
            )
            .setParameter(
                "currency",
                "XOF"
            )
            .setParameter(
                "status",
                OrganizationStatus.PENDING_ACTIVATION.name()
            )
            .setParameter(
                "createdAt",
                Instant.parse(
                    "2026-08-03T10:00:00Z"
                )
            )
            .executeUpdate()
    )
        .isInstanceOf(Exception.class)
        .hasMessageContaining(
            "uk_organizations_normalized_legal_name"
        );
  }
  @Test
  void shouldRejectInactiveHeadquarters() {
    // Given
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

    // When / Then
    assertThatThrownBy(() ->
        entityManager.createNativeQuery("""
          INSERT INTO organization.stores (
            id,
            organization_id,
            code,
            name,
            headquarters,
            active,
            created_at
          )
          VALUES (
            CAST(:id AS uuid),
            CAST(:organizationId AS uuid),
            :code,
            :name,
            true,
            false,
            :createdAt
          )
          """)
            .setParameter(
                "id",
                UUID.randomUUID()
            )
            .setParameter(
                "organizationId",
                ORGANIZATION_UUID
            )
            .setParameter(
                "code",
                "DAKAR-HQ"
            )
            .setParameter(
                "name",
                "Siège Dakar invalide"
            )
            .setParameter(
                "createdAt",
                Instant.parse(
                    "2026-08-03T10:00:00Z"
                )
            )
            .executeUpdate()
    )
        .isInstanceOf(Exception.class)
        .hasMessageContaining(
            "ck_stores_headquarters_active"
        );
  }
  @Test
  void shouldRejectStoreWithUnknownOrganization() {
    UUID unknownOrganizationId = UUID.randomUUID();

    assertThatThrownBy(() ->
        entityManager.createNativeQuery("""
          INSERT INTO organization.stores (
            id,
            organization_id,
            code,
            name,
            headquarters,
            active,
            created_at
          )
          VALUES (
            CAST(:id AS uuid),
            CAST(:organizationId AS uuid),
            :code,
            :name,
            false,
            true,
            :createdAt
          )
          """)
            .setParameter(
                "id",
                UUID.randomUUID()
            )
            .setParameter(
                "organizationId",
                unknownOrganizationId
            )
            .setParameter(
                "code",
                "DAKAR-01"
            )
            .setParameter(
                "name",
                "Magasin Dakar"
            )
            .setParameter(
                "createdAt",
                Instant.parse("2026-08-03T10:00:00Z")
            )
            .executeUpdate()
    )
        .isInstanceOf(Exception.class)
        .hasMessageContaining(
            "fk_stores_organization"
        );
  }
  @Test
  void shouldRejectOrganizationDeletionWhenStoresStillExist() {
    // Given
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

    // When / Then
    assertThatThrownBy(() ->
        entityManager.createNativeQuery("""
          DELETE
          FROM organization.organizations
          WHERE id = CAST(:organizationId AS uuid)
          """)
            .setParameter(
                "organizationId",
                ORGANIZATION_UUID
            )
            .executeUpdate()
    )
        .isInstanceOf(Exception.class)
        .hasMessageContaining(
            "fk_stores_organization"
        );
  }
  @Test
  void shouldRejectInvalidOrganizationStatus() {
    assertThatThrownBy(() ->
        entityManager.createNativeQuery("""
          INSERT INTO organization.organizations (
            id,
            tenant_id,
            name,
            legal_name,
            normalized_legal_name,
            currency,
            status,
            created_at
          )
          VALUES (
            CAST(:id AS uuid),
            CAST(:tenantId AS uuid),
            :name,
            :legalName,
            :normalizedLegalName,
            :currency,
            :status,
            :createdAt
          )
          """)
            .setParameter(
                "id",
                UUID.randomUUID()
            )
            .setParameter(
                "tenantId",
                UUID.randomUUID()
            )
            .setParameter(
                "name",
                "Organisation invalide"
            )
            .setParameter(
                "legalName",
                "Organisation Invalide SARL"
            )
            .setParameter(
                "normalizedLegalName",
                "ORGANISATION INVALIDE SARL"
            )
            .setParameter(
                "currency",
                "XOF"
            )
            .setParameter(
                "status",
                "DELETED"
            )
            .setParameter(
                "createdAt",
                Instant.parse(
                    "2026-08-03T10:00:00Z"
                )
            )
            .executeUpdate()
    )
        .isInstanceOf(Exception.class)
        .hasMessageContaining(
            "ck_organizations_status"
        );
  }
}

package com.zim.organization.infrastructure.persistence;


import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.valueobject.*;
import com.zim.shared.domain.TenantId;
import com.zim.organization.infrastructure.persistence.adapteur.OrganizationRepositoryAdapter;
import com.zim.organization.infrastructure.persistence.entity.StoreEntity;
import com.zim.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.zim.organization.infrastructure.persistence.repository.SpringDataOrganizationRepository;
import com.zim.organization.infrastructure.persistence.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
            tenant_id,
            code,
            name,
            headquarters,
            active,
            created_at
          )
          VALUES (
            CAST(:id AS uuid),
            CAST(:organizationId AS uuid),
            CAST(:tenantId AS uuid),
            :code,
            :name,
            false,
            true,
            :createdAt
          )
          """)
            .setParameter("id", UUID.randomUUID())
            .setParameter("organizationId", ORGANIZATION_UUID)
            .setParameter("tenantId", TENANT_UUID)
            .setParameter("code", "THIES-01")
            .setParameter("name", "Autre magasin")
            .setParameter(
                "createdAt",
                Instant.parse("2026-08-03T10:00:00Z")
            )
            .executeUpdate()
    )
        .satisfies(violatedConstraint("uk_stores_organization_code"));
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
            tenant_id,
            code,
            name,
            headquarters,
            active,
            created_at
          )
          VALUES (
            CAST(:id AS uuid),
            CAST(:organizationId AS uuid),
            CAST(:tenantId AS uuid),
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
                "tenantId",
                TENANT_UUID
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
        .satisfies(violatedConstraint("uk_stores_one_headquarters_per_organization"));
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
        .satisfies(violatedConstraint("uk_organizations_normalized_legal_name"));
  }
  @Test
  void shouldRejectSecondOrganizationForSameTenant() {
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

    // When / Then: same tenant, different id and legal name, so only the
    // tenant uniqueness constraint can reject the row.
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
            .setParameter("id", UUID.randomUUID())
            .setParameter("tenantId", TENANT_UUID)
            .setParameter("name", "Quincaillerie Dakar")
            .setParameter("legalName", "Quincaillerie Dakar SARL")
            .setParameter(
                "normalizedLegalName",
                "QUINCAILLERIE DAKAR SARL"
            )
            .setParameter("currency", "XOF")
            .setParameter(
                "status",
                OrganizationStatus.PENDING_ACTIVATION.name()
            )
            .setParameter(
                "createdAt",
                Instant.parse("2026-08-03T10:00:00Z")
            )
            .executeUpdate()
    )
        .satisfies(violatedConstraint("uk_organizations_tenant_id"));
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
            tenant_id,
            code,
            name,
            headquarters,
            active,
            created_at
          )
          VALUES (
            CAST(:id AS uuid),
            CAST(:organizationId AS uuid),
            CAST(:tenantId AS uuid),
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
                "tenantId",
                TENANT_UUID
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
        .satisfies(violatedConstraint("ck_stores_headquarters_active"));
  }
  @Test
  void shouldRejectStoreWithUnknownOrganization() {
    UUID unknownOrganizationId = UUID.randomUUID();

    assertThatThrownBy(() ->
        entityManager.createNativeQuery("""
          INSERT INTO organization.stores (
            id,
            organization_id,
            tenant_id,
            code,
            name,
            headquarters,
            active,
            created_at
          )
          VALUES (
            CAST(:id AS uuid),
            CAST(:organizationId AS uuid),
            CAST(:tenantId AS uuid),
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
                "tenantId",
                UUID.randomUUID()
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
        .satisfies(violatedConstraint("fk_stores_organization_tenant"));
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
        .satisfies(violatedConstraint("fk_stores_organization_tenant"));
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
        .satisfies(violatedConstraint("ck_organizations_status"));
  }

  @Test
  void shouldRejectStoreWhoseTenantDiffersFromItsOrganization() {
    // Given: organization A (tenant A) and organization B (tenant B)
    saveOrganization(
        ORGANIZATION_UUID,
        TENANT_UUID,
        "Quincaillerie Thiès SARL",
        HEADQUARTERS_UUID
    );
    saveOrganization(
        OTHER_ORGANIZATION_UUID,
        OTHER_TENANT_UUID,
        "Quincaillerie Dakar SARL",
        UUID.randomUUID()
    );

    entityManager.flush();
    entityManager.clear();

    // When / Then: A's organization with B's tenant
    assertThatThrownBy(() ->
        insertStore(
            UUID.randomUUID(),
            ORGANIZATION_UUID,
            OTHER_TENANT_UUID,
            "DAKAR-01"
        )
    )
        .satisfies(violatedConstraint("fk_stores_organization_tenant"));
  }

  @Test
  void shouldRejectMovingStoreToAnotherTenant() {
    // Given: a store of organization A, and organization B (tenant B)
    saveOrganization(
        ORGANIZATION_UUID,
        TENANT_UUID,
        "Quincaillerie Thiès SARL",
        HEADQUARTERS_UUID
    );
    saveOrganization(
        OTHER_ORGANIZATION_UUID,
        OTHER_TENANT_UUID,
        "Quincaillerie Dakar SARL",
        UUID.randomUUID()
    );

    entityManager.flush();
    entityManager.clear();

    // When / Then
    assertThatThrownBy(() ->
        entityManager.createNativeQuery("""
          UPDATE organization.stores
          SET tenant_id = CAST(:tenantId AS uuid)
          WHERE id = CAST(:id AS uuid)
          """)
            .setParameter("tenantId", OTHER_TENANT_UUID)
            .setParameter("id", HEADQUARTERS_UUID)
            .executeUpdate()
    )
        .satisfies(violatedConstraint("fk_stores_organization_tenant"));
  }

  @Test
  void shouldRejectStoreWithoutTenant() {
    // Given
    saveOrganization(
        ORGANIZATION_UUID,
        TENANT_UUID,
        "Quincaillerie Thiès SARL",
        HEADQUARTERS_UUID
    );

    entityManager.flush();
    entityManager.clear();

    // When / Then
    assertThatThrownBy(() ->
        insertStore(
            UUID.randomUUID(),
            ORGANIZATION_UUID,
            null,
            "DAKAR-01"
        )
    )
        .satisfies(thrown -> {
          ServerErrorMessage error = serverError(thrown);

          assertThat(error.getSQLState())
              .isEqualTo("23502");

          assertThat(error.getTable())
              .isEqualTo("stores");

          assertThat(error.getColumn())
              .isEqualTo("tenant_id");
        });
  }

  /**
   * The context under test starts with {@code ddl-auto: validate}, so it
   * would not load if the StoreEntity mapping, tenant_id included, did not
   * match the migrated schema.
   */
  @Test
  void shouldValidateStoreTenantMappingAgainstMigratedSchema(
      @Value("${spring.jpa.hibernate.ddl-auto}") String ddlAuto
  ) {
    assertThat(ddlAuto)
        .isEqualTo("validate");

    assertThat(
        entityManager.getMetamodel()
            .entity(StoreEntity.class)
            .getAttribute("tenantId")
            .getJavaType()
    ).isEqualTo(UUID.class);
  }

  private void saveOrganization(
      UUID organizationId,
      UUID tenantId,
      String legalName,
      UUID headquartersId
  ) {
    Organization organization = Organization.register(
        new OrganizationId(organizationId),
        new TenantId(tenantId),
        new OrganizationName("Quincaillerie"),
        new LegalName(legalName),
        CurrencyCode.xof(),
        new StoreId(headquartersId),
        new StoreCode("HQ-01"),
        new StoreName("Magasin principal"),
        UUID.randomUUID(),
        CREATED_AT
    );

    organization.clearDomainEvents();

    repositoryAdapter.save(organization);
  }

  private int insertStore(
      UUID id,
      UUID organizationId,
      UUID tenantId,
      String code
  ) {
    return entityManager.createNativeQuery("""
          INSERT INTO organization.stores (
            id,
            organization_id,
            tenant_id,
            code,
            name,
            headquarters,
            active,
            created_at
          )
          VALUES (
            CAST(:id AS uuid),
            CAST(:organizationId AS uuid),
            CAST(:tenantId AS uuid),
            :code,
            :name,
            false,
            true,
            :createdAt
          )
          """)
        .setParameter("id", id)
        .setParameter("organizationId", organizationId)
        .setParameter("tenantId", tenantId)
        .setParameter("code", code)
        .setParameter("name", "Magasin secondaire")
        .setParameter(
            "createdAt",
            Instant.parse("2026-08-03T10:00:00Z")
        )
        .executeUpdate();
  }

  /**
   * Asserts that the statement was rejected by exactly this constraint, so a
   * test cannot pass because another rule (NOT NULL, a different key) fired.
   */
  private static ThrowingConsumer<Throwable> violatedConstraint(
      String constraintName
  ) {
    return thrown -> assertThat(serverError(thrown).getConstraint())
        .isEqualTo(constraintName);
  }

  private static ServerErrorMessage serverError(Throwable thrown) {
    for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
      if (cause instanceof PSQLException exception
          && exception.getServerErrorMessage() != null) {
        return exception.getServerErrorMessage();
      }
    }
    throw new AssertionError(
        "Expected a PostgreSQL server error, got " + thrown,
        thrown
    );
  }
}

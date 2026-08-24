package com.zim.organization.infrastructure.persistence;


import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.Store;
import com.zim.organization.domain.valueobject.*;
import com.zim.organization.infrastructure.persistence.adapteur.OrganizationRepositoryAdapter;
import com.zim.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.zim.organization.infrastructure.persistence.repository.SpringDataOrganizationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
public class OrganizationAggregatePersistenceIT {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("quine")
                    .withUsername("quine")
                    .withPassword("quine");

    @DynamicPropertySource
    static void configurePostgres(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                POSTGRES::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                POSTGRES::getUsername
        );

        registry.add(
                "spring.datasource.password",
                POSTGRES::getPassword
        );
    }

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
                        new OrganizationPersistenceMapper()
                );
    }

    @Test
    void shouldSaveAndReloadStores() {
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

        StoreId secondaryStoreId =
                new StoreId(
                        UUID.fromString(
                                "86fd6eb4-23f6-4381-842e-e5d57def4a39"
                        )
                );

        Instant secondaryStoreCreatedAt =
                Instant.parse("2026-08-02T10:00:00Z");

        organization.activate(
                UUID.randomUUID(),
                Instant.parse("2026-08-01T11:00:00Z")
        );

        organization.clearDomainEvents();

        organization.addStore(
                secondaryStoreId,
                new StoreCode("DAKAR-01"),
                new StoreName("Magasin Dakar"),
                UUID.randomUUID(),
                secondaryStoreCreatedAt
        );

        organization.clearDomainEvents();

        repositoryAdapter.save(organization);

        entityManager.flush();
        entityManager.clear();

        Organization reloaded = repositoryAdapter
                .findById(
                        new OrganizationId(ORGANIZATION_UUID)
                )
                .orElseThrow();

        assertThat(reloaded.stores())
                .hasSize(2);

        Store headquarters = reloaded.stores()
                .stream()
                .filter(Store::isHeadquarters)
                .findFirst()
                .orElseThrow();

        assertThat(headquarters.id().value())
                .isEqualTo(HEADQUARTERS_UUID);

        assertThat(headquarters.code().value())
                .isEqualTo("THIES-01");

        assertThat(headquarters.name().value())
                .isEqualTo("Magasin principal");

        assertThat(headquarters.isActive())
                .isTrue();

        assertThat(headquarters.createdAt())
                .isEqualTo(CREATED_AT);

        Store secondaryStore = reloaded.stores()
                .stream()
                .filter(store ->
                        store.id().equals(secondaryStoreId)
                )
                .findFirst()
                .orElseThrow();

        assertThat(secondaryStore.code().value())
                .isEqualTo("DAKAR-01");

        assertThat(secondaryStore.name().value())
                .isEqualTo("Magasin Dakar");

        assertThat(secondaryStore.isHeadquarters())
                .isFalse();

        assertThat(secondaryStore.isActive())
                .isTrue();

        assertThat(secondaryStore.createdAt())
                .isEqualTo(secondaryStoreCreatedAt);

        assertThat(reloaded.domainEvents())
                .isEmpty();
    }

    @Test
    void shouldPersistNewStoreWhenUpdatingExistingOrganization() {
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

        Organization persistedOrganization =
                repositoryAdapter.findById(
                        new OrganizationId(ORGANIZATION_UUID)
                ).orElseThrow();

        persistedOrganization.activate(
                UUID.randomUUID(),
                Instant.parse("2026-08-02T09:00:00Z")
        );

        persistedOrganization.clearDomainEvents();

        StoreId secondaryStoreId =
                new StoreId(
                        UUID.fromString(
                                "86fd6eb4-23f6-4381-842e-e5d57def4a39"
                        )
                );

        Instant storeCreatedAt =
                Instant.parse("2026-08-02T10:00:00Z");

        persistedOrganization.addStore(
                secondaryStoreId,
                new StoreCode("DAKAR-01"),
                new StoreName("Magasin Dakar"),
                UUID.randomUUID(),
                storeCreatedAt
        );

        persistedOrganization.clearDomainEvents();

        // When
        repositoryAdapter.save(persistedOrganization);

        entityManager.flush();
        entityManager.clear();

        // Then
        Organization reloaded =
                repositoryAdapter.findById(
                        new OrganizationId(ORGANIZATION_UUID)
                ).orElseThrow();

        assertThat(reloaded.stores())
                .hasSize(2);

        Store addedStore = reloaded.stores()
                .stream()
                .filter(store ->
                        store.id().equals(secondaryStoreId)
                )
                .findFirst()
                .orElseThrow();

        assertThat(addedStore.code().value())
                .isEqualTo("DAKAR-01");

        assertThat(addedStore.name().value())
                .isEqualTo("Magasin Dakar");

        assertThat(addedStore.isHeadquarters())
                .isFalse();

        assertThat(addedStore.isActive())
                .isTrue();

        assertThat(addedStore.createdAt())
                .isEqualTo(storeCreatedAt);
    }
    @Test
    void shouldPersistHeadquartersChange() {
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

        organization.activate(
                UUID.randomUUID(),
                Instant.parse("2026-08-01T11:00:00Z")
        );

        organization.clearDomainEvents();

        StoreId secondaryStoreId =
                new StoreId(
                        UUID.fromString(
                                "86fd6eb4-23f6-4381-842e-e5d57def4a39"
                        )
                );

        organization.addStore(
                secondaryStoreId,
                new StoreCode("DAKAR-01"),
                new StoreName("Magasin Dakar"),
                UUID.randomUUID(),
                Instant.parse("2026-08-02T10:00:00Z")
        );

        organization.clearDomainEvents();

        repositoryAdapter.save(organization);

        entityManager.flush();
        entityManager.clear();

        Organization persisted =
                repositoryAdapter.findById(
                        new OrganizationId(ORGANIZATION_UUID)
                ).orElseThrow();

        // When
        persisted.changeHeadquarters(
                secondaryStoreId,
                UUID.randomUUID(),
                Instant.parse("2026-08-03T10:00:00Z")
        );

        persisted.clearDomainEvents();

        repositoryAdapter.save(persisted);

        entityManager.flush();
        entityManager.clear();

        // Then
        Organization reloaded =
                repositoryAdapter.findById(
                        new OrganizationId(ORGANIZATION_UUID)
                ).orElseThrow();

        assertThat(reloaded.stores())
                .hasSize(2);

        Store currentHeadquarters = reloaded.stores()
                .stream()
                .filter(Store::isHeadquarters)
                .findFirst()
                .orElseThrow();

        assertThat(currentHeadquarters.id())
                .isEqualTo(secondaryStoreId);

        Store oldHeadquarters = reloaded.stores()
                .stream()
                .filter(store ->
                        store.id().value()
                                .equals(HEADQUARTERS_UUID)
                )
                .findFirst()
                .orElseThrow();

        assertThat(oldHeadquarters.isHeadquarters())
                .isFalse();

        assertThat(currentHeadquarters.isActive())
                .isTrue();

        assertThat(oldHeadquarters.isActive())
                .isTrue();

        assertThat(reloaded.stores())
                .filteredOn(Store::isHeadquarters)
                .hasSize(1);
    }
}

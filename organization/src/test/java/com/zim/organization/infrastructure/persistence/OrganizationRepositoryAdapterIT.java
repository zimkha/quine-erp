package com.zim.organization.infrastructure.persistence;


import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class OrganizationRepositoryAdapterIT {


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
    void shouldFindOrganizationByTenantId() {
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

        // When
        Optional<Organization> result =
                repositoryAdapter.findByTenantId(
                        new TenantId(TENANT_UUID)
                );

        // Then
        assertThat(result)
                .isPresent()
                .get()
                .satisfies(found -> {
                    assertThat(found.id())
                            .isEqualTo(
                                    new OrganizationId(
                                            ORGANIZATION_UUID
                                    )
                            );

                    assertThat(found.tenantId())
                            .isEqualTo(
                                    new TenantId(
                                            TENANT_UUID
                                    )
                            );

                    assertThat(found.name().value())
                            .isEqualTo(
                                    "Quincaillerie Thiès"
                            );

                    assertThat(found.legalName().value())
                            .isEqualTo(
                                    "Quincaillerie Thiès SARL"
                            );

                    assertThat(found.currency())
                            .isEqualTo(
                                    CurrencyCode.xof()
                            );

                    assertThat(found.stores())
                            .hasSize(1);
                });
    }

}

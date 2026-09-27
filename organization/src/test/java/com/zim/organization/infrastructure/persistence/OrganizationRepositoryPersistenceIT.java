package com.zim.organization.infrastructure.persistence;

import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.model.OrganizationStatus;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.shared.domain.TenantId;
import com.zim.organization.infrastructure.persistence.support.OrganizationIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationRepositoryPersistenceIT
        extends OrganizationIntegrationTestSupport {

    @Test
    void shouldSaveAndReloadOrganization() {
        // Given
        Organization organization = pendingOrganization();

        // When
        repositoryAdapter.save(organization);

        flushAndClear();

        Organization reloaded = reloadOrganization();

        // Then
        assertThat(reloaded.id())
                .isEqualTo(
                        new OrganizationId(ORGANIZATION_UUID)
                );

        assertThat(reloaded.tenantId())
                .isEqualTo(
                        new TenantId(TENANT_UUID)
                );

        assertThat(reloaded.name().value())
                .isEqualTo("Quincaillerie Thiès");

        assertThat(reloaded.legalName().value())
                .isEqualTo("Quincaillerie Thiès SARL");

        assertThat(reloaded.legalName().normalizedValue())
                .isEqualTo("QUINCAILLERIE THIÈS SARL");

        assertThat(reloaded.currency().value())
                .isEqualTo("XOF");

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
        Organization organization = pendingOrganization();

        repositoryAdapter.save(organization);

        flushAndClear();

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

                    assertThat(
                            found.legalName()
                                    .normalizedValue()
                    )
                            .isEqualTo(
                                    "QUINCAILLERIE THIÈS SARL"
                            );

                    assertThat(found.currency().value())
                            .isEqualTo("XOF");

                    assertThat(found.status())
                            .isEqualTo(
                                    OrganizationStatus
                                            .PENDING_ACTIVATION
                            );

                    assertThat(found.stores())
                            .hasSize(1);
                });
    }

    @Test
    void shouldReturnEmptyWhenTenantIdDoesNotExist() {
        // Given
        TenantId unknownTenantId =
                new TenantId(UUID.randomUUID());

        // When
        Optional<Organization> result =
                repositoryAdapter.findByTenantId(
                        unknownTenantId
                );

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldDetectExistingNormalizedLegalName() {
        // Given
        Organization organization = pendingOrganization();

        repositoryAdapter.save(organization);

        flushAndClear();

        // When
        boolean exists =
                repositoryAdapter.existsByLegalName(
                        "QUINCAILLERIE THIÈS SARL"
                );

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void shouldReturnFalseWhenLegalNameDoesNotExist() {
        // Given
        Organization organization = pendingOrganization();

        repositoryAdapter.save(organization);

        flushAndClear();

        // When
        boolean exists =
                repositoryAdapter.existsByLegalName(
                        "ENTREPRISE INEXISTANTE SARL"
                );

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void shouldFindOrganizationById() {
        // Given
        Organization organization = pendingOrganization();

        repositoryAdapter.save(organization);

        flushAndClear();

        // When
        Optional<Organization> result =
                repositoryAdapter.findById(
                        new OrganizationId(
                                ORGANIZATION_UUID
                        )
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

                    assertThat(found.status())
                            .isEqualTo(
                                    OrganizationStatus
                                            .PENDING_ACTIVATION
                            );
                });
    }

    @Test
    void shouldReturnEmptyWhenOrganizationIdDoesNotExist() {
        // Given
        OrganizationId unknownOrganizationId =
                new OrganizationId(
                        UUID.randomUUID()
                );

        // When
        Optional<Organization> result =
                repositoryAdapter.findById(
                        unknownOrganizationId
                );

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldDetectExistingTenantId() {
        // Given
        Organization organization = pendingOrganization();

        repositoryAdapter.save(organization);

        flushAndClear();

        // When
        boolean exists =
                repositoryAdapter.existsByTenantId(
                        new TenantId(TENANT_UUID)
                );

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void shouldReturnFalseWhenTenantIdDoesNotExist() {
        // Given
        TenantId unknownTenantId =
                new TenantId(UUID.randomUUID());

        // When
        boolean exists =
                repositoryAdapter.existsByTenantId(
                        unknownTenantId
                );

        // Then
        assertThat(exists).isFalse();
    }
}
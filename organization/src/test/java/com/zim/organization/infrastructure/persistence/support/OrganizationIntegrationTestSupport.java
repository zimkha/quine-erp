package com.zim.organization.infrastructure.persistence.support;

import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.valueobject.CurrencyCode;
import com.zim.organization.domain.valueobject.LegalName;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.OrganizationName;
import com.zim.organization.domain.valueobject.StoreCode;
import com.zim.organization.domain.valueobject.StoreId;
import com.zim.organization.domain.valueobject.StoreName;
import com.zim.organization.domain.valueobject.TenantId;
import com.zim.organization.infrastructure.persistence.adapteur.OrganizationRepositoryAdapter;
import com.zim.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.zim.organization.infrastructure.persistence.repository.SpringDataOrganizationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

public abstract class OrganizationIntegrationTestSupport
        extends PostgresIntegrationTest {

    protected static final UUID ORGANIZATION_UUID =
            UUID.fromString(
                    "5c80d578-83f7-4b44-b5f7-598530067a09"
            );

    protected static final UUID TENANT_UUID =
            UUID.fromString(
                    "2d3a7d37-ef2c-4794-b248-b08acf42eb38"
            );

    protected static final UUID HEADQUARTERS_UUID =
            UUID.fromString(
                    "4ee0d038-4617-435c-b7c8-48697d4cf909"
            );

    protected static final UUID SECONDARY_STORE_UUID =
            UUID.fromString(
                    "86fd6eb4-23f6-4381-842e-e5d57def4a39"
            );

    protected static final Instant CREATED_AT =
            Instant.parse("2026-08-01T10:00:00Z");

    protected static final Instant ACTIVATED_AT =
            Instant.parse("2026-08-01T11:00:00Z");

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected SpringDataOrganizationRepository springDataRepository;

    protected OrganizationRepositoryAdapter repositoryAdapter;

    @BeforeEach
    void initializeRepositoryAdapter() {
        repositoryAdapter =
                new OrganizationRepositoryAdapter(
                        springDataRepository,
                        new OrganizationPersistenceMapper()
                );
    }

    protected Organization pendingOrganization() {
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

        return organization;
    }

    protected Organization activeOrganization() {
        Organization organization =
                pendingOrganization();

        organization.activate(
                UUID.randomUUID(),
                ACTIVATED_AT
        );

        organization.clearDomainEvents();

        return organization;
    }

    protected Organization activeOrganizationWithSecondaryStore() {
        Organization organization =
                activeOrganization();

        organization.addStore(
                new StoreId(SECONDARY_STORE_UUID),
                new StoreCode("DAKAR-01"),
                new StoreName("Magasin Dakar"),
                UUID.randomUUID(),
                Instant.parse("2026-08-02T10:00:00Z")
        );

        organization.clearDomainEvents();

        return organization;
    }


    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    protected Organization reloadOrganization() {
        return repositoryAdapter
                .findById(
                        new OrganizationId(
                                ORGANIZATION_UUID
                        )
                )
                .orElseThrow();
    }

}
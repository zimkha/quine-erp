package com.zim.organization.infrastructure.persistence.adapteur;

import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.repository.OrganizationRepository;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.TenantId;
import com.zim.organization.infrastructure.persistence.entity.OrganizationEntity;
import com.zim.organization.infrastructure.persistence.mapper.OrganizationPersistenceMapper;
import com.zim.organization.infrastructure.persistence.repository.SpringDataOrganizationRepository;

import java.util.Objects;
import java.util.Optional;

public final class OrganizationRepositoryAdapter
        implements OrganizationRepository {

    private final SpringDataOrganizationRepository repository;
    private final OrganizationPersistenceMapper mapper;

    public OrganizationRepositoryAdapter(
            SpringDataOrganizationRepository repository,
            OrganizationPersistenceMapper mapper
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void  save(Organization organization) {
        OrganizationEntity entity =
                mapper.toEntity(organization);

        OrganizationEntity savedEntity =
                repository.save(entity);

        repository.save(
                mapper.toEntity(organization)
        );
    }

    @Override
    public Optional<Organization> findById(
            OrganizationId organizationId
    ) {
        Objects.requireNonNull(organizationId);

        return repository
                .findWithStoresById(organizationId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Organization> findByTenantId(
            TenantId tenantId
    ) {
        Objects.requireNonNull(tenantId);

        return repository
                .findByTenantId(tenantId.value())
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByTenantId(TenantId tenantId) {
        Objects.requireNonNull(tenantId);

        return repository.existsByTenantId(
                tenantId.value()
        );
    }

    @Override
    public boolean existsByLegalName(String normalizedLegalName) {
        Objects.requireNonNull(normalizedLegalName);

        return repository.existsByNormalizedLegalName(
                normalizedLegalName
        );
    }
}
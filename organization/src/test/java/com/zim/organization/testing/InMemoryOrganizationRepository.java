package com.zim.organization.testing;

import com.zim.organization.domain.model.Organization;

import com.zim.organization.domain.repository.OrganizationRepository;
import com.zim.organization.domain.valueobject.LegalName;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.TenantId;

import java.util.*;

public final class InMemoryOrganizationRepository
        implements OrganizationRepository {

    private final Map<OrganizationId, Organization> organizations =
            new HashMap<>();

    @Override
    public void save(Organization organization) {
        Objects.requireNonNull(
                organization,
                "Organization cannot be null"
        );

        organizations.put(
                organization.id(),
                organization
        );
    }

    public void add(Organization organization) {
        organizations.put(organization.id(), organization);
    }

    @Override
    public Optional<Organization> findById(
            OrganizationId organizationId
    ) {
        return Optional.ofNullable(
                organizations.get(organizationId)
        );
    }

    @Override
    public Optional<Organization> findByTenantId(
            TenantId tenantId
    ) {
        return organizations.values()
                .stream()
                .filter(organization ->
                        organization.tenantId().equals(tenantId)
                )
                .findFirst();
    }

    @Override
    public boolean existsByTenantId(TenantId tenantId) {
        return organizations.values()
                .stream()
                .anyMatch(organization ->
                        organization.tenantId().equals(tenantId)
                );
    }

    @Override
    public boolean existsByLegalName(
            String normalizedLegalName
    ) {
        Objects.requireNonNull(
                normalizedLegalName,
                "Normalized legal name cannot be null"
        );

        String normalized = normalizedLegalName
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);

        return organizations.values()
                .stream()
                .map(Organization::legalName)
                .map(LegalName::normalizedValue)
                .anyMatch(normalized::equals);
    }
}

package com.zim.quine.testing;

import com.zim.organization.domain.model.Organization;

import com.zim.organization.domain.repository.OrganizationRepository;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.organization.domain.valueobject.TenantId;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class InMemoryOrganizationRepository
        implements OrganizationRepository {

    private final Map<OrganizationId, Organization> organizations =
            new HashMap<>();

    @Override
    public Organization save(Organization organization) {
        organizations.put(organization.id(), organization);
        return organization;
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
    public boolean existsByLegalName(String legalName) {
        String normalizedLegalName = legalName
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);

        return organizations.values()
                .stream()
                .map(Organization::legalName)
                .map(existingLegalName -> existingLegalName
                        .trim()
                        .replaceAll("\\s+", " ")
                        .toUpperCase(Locale.ROOT)
                )
                .anyMatch(normalizedLegalName::equals);
    }
}

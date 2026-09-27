package com.zim.organization.domain.repository;


import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.shared.domain.TenantId;

import java.util.Optional;

public interface OrganizationRepository {

    void save(Organization organization);

    Optional<Organization> findById(OrganizationId organizationId);

    Optional<Organization> findByTenantId(TenantId tenantId);

    boolean existsByTenantId(TenantId tenantId);

    boolean existsByLegalName(String legalName);
}

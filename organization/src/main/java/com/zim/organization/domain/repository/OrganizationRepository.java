package com.zim.organization.domain.repository;


import com.zim.organization.domain.model.Organization;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.shared.domain.TenantId;

import java.util.Optional;

public interface OrganizationRepository {

  void save(Organization organization);

  /**
   * Loads the organization only if it is owned by {@code tenantId}. An
   * organization owned by another tenant is reported as absent, exactly
   * like one that does not exist.
   */
  Optional<Organization> findById(
      TenantId tenantId,
      OrganizationId organizationId
  );

  /**
   * Global on purpose: legal names are unique across all tenants.
   */
  boolean existsByLegalName(String legalName);
}

package com.zim.organization.testing;

import com.zim.organization.domain.model.Organization;

import com.zim.organization.domain.repository.OrganizationRepository;
import com.zim.organization.domain.valueobject.LegalName;
import com.zim.organization.domain.valueobject.OrganizationId;
import com.zim.shared.domain.TenantId;

import java.util.*;

public final class InMemoryOrganizationRepository
    implements OrganizationRepository {

  private final Map<OrganizationId, Organization> organizations =
      new HashMap<>();

  private int saveCount;

  @Override
  public void save(Organization organization) {
    Objects.requireNonNull(
        organization,
        "Organization cannot be null"
    );

    saveCount++;

    organizations.put(
        organization.id(),
        organization
    );
  }

  /**
   * Seeds an organization without counting it as a save.
   */
  public void add(Organization organization) {
    organizations.put(organization.id(), organization);
  }

  /**
   * Number of {@link #save(Organization)} calls; seeding via
   * {@link #add(Organization)} is not counted.
   */
  public int saveCount() {
    return saveCount;
  }

  @Override
  public Optional<Organization> findById(
      TenantId tenantId,
      OrganizationId organizationId
  ) {
    Objects.requireNonNull(tenantId, "Tenant id cannot be null");
    Objects.requireNonNull(
        organizationId,
        "Organization id cannot be null"
    );

    return Optional.ofNullable(organizations.get(organizationId))
        .filter(organization ->
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

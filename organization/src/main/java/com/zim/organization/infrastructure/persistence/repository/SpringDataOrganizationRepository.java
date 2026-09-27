package com.zim.organization.infrastructure.persistence.repository;

import com.zim.organization.infrastructure.persistence.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataOrganizationRepository
    extends JpaRepository<OrganizationEntity, UUID> {

  @EntityGraph(attributePaths = "stores")
  Optional<OrganizationEntity> findWithStoresByIdAndTenantId(
      UUID id,
      UUID tenantId
  );

  boolean existsByNormalizedLegalName(
      String normalizedLegalName
  );
}

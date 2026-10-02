package com.zim.identity.infrastructure.persistence.repository;

import com.zim.identity.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataUserRepository
    extends JpaRepository<UserEntity, UUID> {

  boolean existsByEmail(String email);

  boolean existsByTenantIdAndOwnerTrue(UUID tenantId);

  Optional<UserEntity> findByEmail(String email);
}
